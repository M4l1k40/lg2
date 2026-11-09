# Vérifie, à travers la gateway et avec chaque rôle, les échéances : création, lecture, modification, statuts,
# échéances proches, isolation (autre avocat, autre cabinet), droits (secrétaire, client) et nettoyage.
# Prérequis : comptes de test présents (seed). Si lawyer-a n'a aucun dossier, le script en crée un.
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

function Assert-Status($Label, $Result, $Expected) {
    Assert-That "$Label -> $($Result.Status) (attendu $Expected)" ($Result.Status -eq $Expected)
    if ($Result.Status -ne $Expected -and $Result.Error) { Write-Host "      réponse : $($Result.Error)" }
}

function Contains-Id($Path, $Token, $Id) {
    $items = @((Invoke-Api GET $Path $Token).Json | ForEach-Object { $_ })
    return [bool]@($items | Where-Object { $_.id -eq $Id }).Count
}

$lawyer = Get-AccessToken 'lawyer-a'
$lawyerSameFirm = Get-AccessToken 'lawyer-a2'
$lawyerOtherFirm = Get-AccessToken 'lawyer-b'
$secretary = Get-AccessToken 'secretary-a'
$client = Get-AccessToken 'client-a'
$lawyerId = (ConvertFrom-JwtPayload $lawyer).sub

$cases = @((Invoke-Api GET '/api/cases' $lawyer).Json | ForEach-Object { $_ })
if ($cases.Count -eq 0) {
    $clients = @((Invoke-Api GET '/api/clients' $lawyer).Json | ForEach-Object { $_ })
    if ($clients.Count -eq 0) { throw "Aucun client confié à lawyer-a : relancez seed-keycloak-users.ps1." }
    $newCase = Invoke-Api POST '/api/cases' $lawyer @{ clientId = $clients[0].id; lawyerId = $lawyerId; reference = "ECH-$([guid]::NewGuid().ToString().Substring(0, 6))"; title = 'Dossier de test des échéances' }
    Assert-Status 'LAWYER crée un dossier de test (aucun dossier existant)' $newCase 201
    if ($newCase.Status -ne 201) { throw "Création du dossier impossible, arrêt." }
    $caseId = $newCase.Json.id
} else {
    $caseId = $cases[0].id
}

$inTwoDays = (Get-Date).AddDays(2).ToString("yyyy-MM-dd'T'10:00:00")
$inTenDays = (Get-Date).AddDays(10).ToString("yyyy-MM-dd'T'15:30:00")
$yesterday = (Get-Date).AddDays(-1).ToString("yyyy-MM-dd'T'10:00:00")
$created = @()

Write-Output "--- 1. Création par l'avocat"
$first = Invoke-Api POST '/api/deadlines' $lawyer @{ caseId = $caseId; title = 'Dépôt des conclusions'; description = 'Échéance de test'; dueDate = $inTwoDays; priority = 'HIGH' }
Assert-Status 'LAWYER crée une échéance' $first 201
if ($first.Status -ne 201) { throw "Création impossible, arrêt." }
$id = $first.Json.id
$created += $id
Assert-That 'statut initial PENDING' ($first.Json.status -eq 'PENDING')
Assert-That "l'auteur enregistré est bien lawyer-a" ($first.Json.createdBy -eq $lawyerId)
Assert-That 'priorité et dossier conservés' ($first.Json.priority -eq 'HIGH' -and $first.Json.caseId -eq $caseId)
Assert-Status 'une date limite passée est refusée' (Invoke-Api POST '/api/deadlines' $lawyer @{ caseId = $caseId; title = 'Passée'; dueDate = $yesterday; priority = 'LOW' }) 400
Assert-Status 'un intitulé vide est refusé' (Invoke-Api POST '/api/deadlines' $lawyer @{ caseId = $caseId; title = ''; dueDate = $inTwoDays; priority = 'LOW' }) 400
Assert-Status 'une priorité inconnue est refusée' (Invoke-Api POST '/api/deadlines' $lawyer @{ caseId = $caseId; title = 'X'; dueDate = $inTwoDays; priority = 'ENORME' }) 400

Write-Output "--- 2. Lecture"
Assert-That 'LAWYER voit son échéance dans la liste' (Contains-Id '/api/deadlines' $lawyer $id)
Assert-Status 'LAWYER lit le détail' (Invoke-Api GET "/api/deadlines/$id" $lawyer) 200
Assert-That 'SECRETARY voit l''échéance (lecture seule, personnel de bureau)' (Contains-Id '/api/deadlines' $secretary $id)
Assert-That 'l''échéance due dans 2 jours figure dans les échéances proches (7 jours)' (Contains-Id '/api/deadlines/approaching?days=7' $lawyer $id)
Assert-Status 'jours hors limites refusés (0)' (Invoke-Api GET '/api/deadlines/approaching?days=0' $lawyer) 400

Write-Output "--- 3. Isolation"
Assert-That 'un autre avocat du cabinet ne la voit pas' (-not (Contains-Id '/api/deadlines' $lawyerSameFirm $id))
Assert-Status 'un autre avocat du cabinet : détail introuvable' (Invoke-Api GET "/api/deadlines/$id" $lawyerSameFirm) 404
Assert-That 'un avocat d''un autre cabinet ne la voit pas' (-not (Contains-Id '/api/deadlines' $lawyerOtherFirm $id))
Assert-Status 'un avocat d''un autre cabinet : détail introuvable' (Invoke-Api GET "/api/deadlines/$id" $lawyerOtherFirm) 404
Assert-Status 'CLIENT n''a pas accès aux échéances (403)' (Invoke-Api GET '/api/deadlines' $client) 403

Write-Output "--- 4. Modification"
$edit = @{ caseId = $caseId; title = 'Conclusions déposées'; description = 'Modifiée'; dueDate = $inTenDays; priority = 'URGENT' }
$updated = Invoke-Api PUT "/api/deadlines/$id" $lawyer $edit
Assert-Status 'LAWYER modifie son échéance' $updated 200
Assert-That 'intitulé, priorité et date modifiés' ($updated.Json.title -eq 'Conclusions déposées' -and $updated.Json.priority -eq 'URGENT' -and (([datetime]$updated.Json.dueDate).ToString("yyyy-MM-dd'T'HH:mm", [Globalization.CultureInfo]::InvariantCulture) -eq $inTenDays.Substring(0, 16)))
Assert-Status 'un autre avocat ne peut pas la modifier (404)' (Invoke-Api PUT "/api/deadlines/$id" $lawyerSameFirm $edit) 404
Assert-Status 'un avocat d''un autre cabinet ne peut pas la modifier (404)' (Invoke-Api PUT "/api/deadlines/$id" $lawyerOtherFirm $edit) 404
Assert-Status 'SECRETARY ne peut pas modifier (403)' (Invoke-Api PUT "/api/deadlines/$id" $secretary $edit) 403
Assert-Status 'CLIENT ne peut pas modifier (403)' (Invoke-Api PUT "/api/deadlines/$id" $client $edit) 403

Write-Output "--- 5. Statuts"
Assert-Status 'SECRETARY ne peut pas changer le statut (403)' (Invoke-Api PATCH "/api/deadlines/$id/status?status=COMPLETED" $secretary) 403
Assert-Status 'un autre avocat ne peut pas changer le statut (404)' (Invoke-Api PATCH "/api/deadlines/$id/status?status=COMPLETED" $lawyerSameFirm) 404
Assert-Status 'un statut inconnu est refusé (409)' (Invoke-Api PATCH "/api/deadlines/$id/status?status=NIMPORTE" $lawyer) 409
$done = Invoke-Api PATCH "/api/deadlines/$id/status?status=COMPLETED" $lawyer
Assert-Status 'LAWYER marque terminée' $done 200
Assert-That 'statut COMPLETED' ($done.Json.status -eq 'COMPLETED')
Assert-Status 'une échéance terminée ne peut plus changer de statut (409)' (Invoke-Api PATCH "/api/deadlines/$id/status?status=PENDING" $lawyer) 409
Assert-That 'une échéance terminée n''est plus dans les échéances proches' (-not (Contains-Id '/api/deadlines/approaching?days=30' $lawyer $id))
$second = Invoke-Api POST '/api/deadlines' $lawyer @{ caseId = $caseId; title = 'Seconde échéance'; dueDate = $inTenDays; priority = 'LOW' }
Assert-Status 'LAWYER crée une seconde échéance' $second 201
if ($second.Status -eq 201) {
    $created += $second.Json.id
    Assert-Status 'LAWYER annule la seconde' (Invoke-Api PATCH "/api/deadlines/$($second.Json.id)/status?status=CANCELLED" $lawyer) 200
}

Write-Output "--- 6. Suppression et nettoyage"
Assert-Status 'SECRETARY ne peut pas supprimer (403)' (Invoke-Api DELETE "/api/deadlines/$id" $secretary) 403
Assert-Status 'un autre avocat ne peut pas supprimer (404)' (Invoke-Api DELETE "/api/deadlines/$id" $lawyerSameFirm) 404
foreach ($deadlineId in $created) {
    Assert-Status "LAWYER supprime $deadlineId" (Invoke-Api DELETE "/api/deadlines/$deadlineId" $lawyer) 204
}
Assert-Status 'une échéance supprimée est introuvable' (Invoke-Api GET "/api/deadlines/$id" $lawyer) 404

if ($script:failures -gt 0) { Write-Output "`n$($script:failures) échec(s)."; exit 1 }
Write-Output "`nTous les contrôles sont passés."
