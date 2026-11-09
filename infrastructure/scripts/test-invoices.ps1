# Vérifie, à travers la gateway et avec chaque rôle, les factures : création, lecture, modification, statuts,
# gel des factures payées ou annulées, isolation (autre avocat, autre cabinet), droits (secrétaire, client)
# et nettoyage. Prérequis : billing-service reconstruit avec la règle « facture figée », comptes de test présents.
$ErrorActionPreference = 'Stop'

$keycloakUrl = 'http://localhost:8080'
$gatewayUrl = 'http://localhost:9000'
$script:failures = 0

function Get-AccessToken($Username) {
    (Invoke-RestMethod -Method Post -Uri "$keycloakUrl/realms/legalflow/protocol/openid-connect/token" `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{ client_id = 'legalflow-frontend'; grant_type = 'password'; username = $Username; password = $Username }).access_token
}

function ConvertFrom-JwtPayload($Token) {
    $payload = $Token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    while ($payload.Length % 4) { $payload += '=' }
    [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json
}

function Invoke-Api($Method, $Path, $Token, $Body = $null) {
    $parameters = @{
        Uri = "$gatewayUrl$Path"; Method = $Method; Headers = @{ Authorization = "Bearer $Token" }
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Body))
    }
    try {
        $response = Invoke-WebRequest @parameters
        $json = $null
        if ($response.Content) { $json = [Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray()) | ConvertFrom-Json }
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Json = $json; Error = '' }
    } catch {
        $status = 0
        $errorBody = ''
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            try { $errorBody = (New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } catch { }
        }
        return [pscustomobject]@{ Status = $status; Json = $null; Error = $errorBody }
    }
}

# Write-Host (et non Write-Output) : dans une fonction, Write-Output serait capturé dans la valeur de retour.
function Assert-That($Label, $Condition) {
    if ($Condition) { Write-Host "PASS  $Label" } else { Write-Host "FAIL  $Label"; $script:failures++ }
}

# Copie d'une table de hachage avec certaines valeurs remplacées (l'opérateur + échoue si une clé existe déjà).
function Merge-Hash($Base, $Overrides) {
    $copy = @{}
    foreach ($key in $Base.Keys) { $copy[$key] = $Base[$key] }
    foreach ($key in $Overrides.Keys) { $copy[$key] = $Overrides[$key] }
    return $copy
}

function Assert-Status($Label, $Result, $Expected) {
    Assert-That "$Label -> $($Result.Status) (attendu $Expected)" ($Result.Status -eq $Expected)
    if ($Result.Status -ne $Expected -and $Result.Error) { Write-Host "      réponse : $($Result.Error)" }
}

function Contains-Id($Token, $Id) {
    $items = @((Invoke-Api GET '/api/invoices' $Token).Json | ForEach-Object { $_ })
    return [bool]@($items | Where-Object { $_.id -eq $Id }).Count
}

$lawyer = Get-AccessToken 'lawyer-a'
$lawyerSameFirm = Get-AccessToken 'lawyer-a2'
$lawyerOtherFirm = Get-AccessToken 'lawyer-b'
$secretary = Get-AccessToken 'secretary-a'
$client = Get-AccessToken 'client-a'
$lawyerClaims = ConvertFrom-JwtPayload $lawyer
$lawyerId = $lawyerClaims.sub
$lawFirmId = $lawyerClaims.lawFirmId
$clientClaimId = (ConvertFrom-JwtPayload $client).clientId

$cases = @((Invoke-Api GET '/api/cases' $lawyer).Json | ForEach-Object { $_ })
if ($cases.Count -eq 0) {
    $clients = @((Invoke-Api GET '/api/clients' $lawyer).Json | ForEach-Object { $_ })
    if ($clients.Count -eq 0) { throw "Aucun client confié à lawyer-a : relancez seed-keycloak-users.ps1." }
    $newCase = Invoke-Api POST '/api/cases' $lawyer @{ clientId = $clients[0].id; lawyerId = $lawyerId; reference = "FAC-$([guid]::NewGuid().ToString().Substring(0, 6))"; title = 'Dossier de test des factures' }
    Assert-Status 'LAWYER crée un dossier de test (aucun dossier existant)' $newCase 201
    if ($newCase.Status -ne 201) { throw "Création du dossier impossible, arrêt." }
    $case = $newCase.Json
} else {
    $case = $cases[0]
}
$caseId = $case.id
$clientId = $case.clientId

$suffix = [guid]::NewGuid().ToString().Substring(0, 8)
$number = "TEST-$suffix"
$due = (Get-Date).AddDays(15).ToString('yyyy-MM-dd')
$created = @()

Write-Output "--- 1. Création par l'avocat"
$body = @{ lawFirmId = $lawFirmId; caseId = $caseId; clientId = $clientId; invoiceNumber = $number; description = 'Honoraires de test'; amount = 1250.50; dueDate = $due }
$first = Invoke-Api POST '/api/invoices' $lawyer $body
Assert-Status 'LAWYER crée une facture' $first 201
if ($first.Status -ne 201) { throw "Création impossible, arrêt." }
$id = $first.Json.id
$created += $id
Assert-That 'la facture créée est envoyée (SENT)' ($first.Json.status -eq 'SENT')
Assert-That 'montant, numéro et client conservés' ([decimal]$first.Json.amount -eq 1250.50 -and $first.Json.invoiceNumber -eq $number -and $first.Json.clientId -eq $clientId)
Assert-That "l'auteur enregistré est bien lawyer-a" ($first.Json.createdBy -eq $lawyerId)
Assert-Status 'un numéro déjà utilisé est refusé (409)' (Invoke-Api POST '/api/invoices' $lawyer $body) 409
Assert-Status 'un montant nul est refusé' (Invoke-Api POST '/api/invoices' $lawyer (Merge-Hash $body @{ invoiceNumber = "Z-$suffix"; amount = 0 })) 400
Assert-Status 'une description vide est refusée' (Invoke-Api POST '/api/invoices' $lawyer (Merge-Hash $body @{ invoiceNumber = "Y-$suffix"; description = '' })) 400
Assert-Status "un autre cabinet dans le corps est refusé (403)" (Invoke-Api POST '/api/invoices' $lawyer (Merge-Hash $body @{ invoiceNumber = "X-$suffix"; lawFirmId = [guid]::NewGuid().ToString() })) 403
Assert-Status 'CLIENT ne peut pas créer (403)' (Invoke-Api POST '/api/invoices' $client (Merge-Hash $body @{ invoiceNumber = "W-$suffix" })) 403
Assert-Status 'SECRETARY ne peut pas créer (403)' (Invoke-Api POST '/api/invoices' $secretary (Merge-Hash $body @{ invoiceNumber = "V-$suffix" })) 403

Write-Output "--- 2. Lecture"
Assert-That 'LAWYER voit sa facture dans la liste' (Contains-Id $lawyer $id)
Assert-Status 'LAWYER lit le détail' (Invoke-Api GET "/api/invoices/$id" $lawyer) 200
Assert-That 'SECRETARY voit la facture (personnel de bureau)' (Contains-Id $secretary $id)
$clientSees = Contains-Id $client $id
Assert-That "CLIENT ne voit la facture que si elle est à son nom (clientId du jeton : $clientClaimId)" ($clientSees -eq ($clientId -eq $clientClaimId))

Write-Output "--- 3. Isolation"
Assert-That 'un autre avocat du cabinet ne la voit pas' (-not (Contains-Id $lawyerSameFirm $id))
Assert-Status 'un autre avocat du cabinet : détail introuvable' (Invoke-Api GET "/api/invoices/$id" $lawyerSameFirm) 404
Assert-That 'un avocat d''un autre cabinet ne la voit pas' (-not (Contains-Id $lawyerOtherFirm $id))
Assert-Status 'un avocat d''un autre cabinet : détail introuvable' (Invoke-Api GET "/api/invoices/$id" $lawyerOtherFirm) 404

Write-Output "--- 4. Modification"
$edit = Merge-Hash $body @{ description = 'Honoraires modifiés'; amount = 1300.00 }
$updated = Invoke-Api PUT "/api/invoices/$id" $lawyer $edit
Assert-Status 'LAWYER modifie sa facture' $updated 200
Assert-That 'description et montant modifiés' ($updated.Json.description -eq 'Honoraires modifiés' -and [decimal]$updated.Json.amount -eq 1300.00)
Assert-Status 'un autre avocat ne peut pas la modifier (404)' (Invoke-Api PUT "/api/invoices/$id" $lawyerSameFirm $edit) 404
Assert-Status 'un avocat d''un autre cabinet ne peut pas la modifier (404)' (Invoke-Api PUT "/api/invoices/$id" $lawyerOtherFirm @{ description = $edit.description; amount = $edit.amount; invoiceNumber = $edit.invoiceNumber; caseId = $edit.caseId; clientId = $edit.clientId; dueDate = $edit.dueDate }) 404
Assert-Status 'SECRETARY ne peut pas modifier (403)' (Invoke-Api PUT "/api/invoices/$id" $secretary $edit) 403
Assert-Status 'CLIENT ne peut pas modifier (403)' (Invoke-Api PUT "/api/invoices/$id" $client $edit) 403

Write-Output "--- 5. Statuts"
Assert-Status 'un statut inconnu est refusé (409)' (Invoke-Api PATCH "/api/invoices/$id/status?status=NIMPORTE" $lawyer) 409
Assert-Status 'un autre avocat ne peut pas changer le statut (404)' (Invoke-Api PATCH "/api/invoices/$id/status?status=PAID" $lawyerSameFirm) 404
Assert-Status 'CLIENT ne peut pas changer le statut (403)' (Invoke-Api PATCH "/api/invoices/$id/status?status=PAID" $client) 403
$paid = Invoke-Api PATCH "/api/invoices/$id/status?status=PAID" $lawyer
Assert-Status 'LAWYER marque la facture payée' $paid 200
Assert-That 'statut PAID' ($paid.Json.status -eq 'PAID')
Assert-Status 'une facture payée ne peut plus changer de statut (409)' (Invoke-Api PATCH "/api/invoices/$id/status?status=CANCELLED" $lawyer) 409
Assert-Status 'une facture payée ne peut plus être modifiée (409)' (Invoke-Api PUT "/api/invoices/$id" $lawyer $edit) 409
$secondBody = Merge-Hash $body @{ invoiceNumber = "TEST2-$suffix"; amount = 80.00 }
$second = Invoke-Api POST '/api/invoices' $lawyer $secondBody
Assert-Status 'LAWYER crée une seconde facture' $second 201
if ($second.Status -eq 201) {
    $created += $second.Json.id
    Assert-Status 'SECRETARY peut marquer une facture annulée' (Invoke-Api PATCH "/api/invoices/$($second.Json.id)/status?status=CANCELLED" $secretary) 200
    Assert-Status 'une facture annulée ne peut plus être modifiée (409)' (Invoke-Api PUT "/api/invoices/$($second.Json.id)" $lawyer ($secondBody)) 409
}

Write-Output "--- 6. Suppression et nettoyage"
Assert-Status 'LAWYER ne peut pas supprimer (403)' (Invoke-Api DELETE "/api/invoices/$id" $lawyer) 403
Assert-Status 'SECRETARY ne peut pas supprimer (403)' (Invoke-Api DELETE "/api/invoices/$id" $secretary) 403
$admin = $null
try { $admin = Get-AccessToken 'admin' } catch { Write-Host "AVERTISSEMENT  compte admin indisponible : les factures de test restent en base ($($created -join ', '))." }
if ($admin) {
    foreach ($invoiceId in $created) {
        Assert-Status "ADMIN supprime $invoiceId" (Invoke-Api DELETE "/api/invoices/$invoiceId" $admin) 204
    }
    Assert-Status 'une facture supprimée est introuvable' (Invoke-Api GET "/api/invoices/$id" $lawyer) 404
}

if ($script:failures -gt 0) { Write-Output "`n$($script:failures) échec(s)."; exit 1 }
Write-Output "`nTous les contrôles sont passés."
