# Vérifie le cloisonnement des avocats : un avocat ne voit que les clients dont il est en charge
# et les dossiers (et factures) qui lui sont assignés ; l'administration voit tout le cabinet.
# Prérequis : services reconstruits, configure-keycloak-user-profile.ps1 et seed-keycloak-users.ps1 exécutés.
$ErrorActionPreference = 'Stop'

$keycloakUrl = 'http://localhost:8080'
$gatewayUrl = 'http://localhost:9000'
$firmA = '11111111-1111-1111-1111-111111111111'
$script:failures = 0

function ConvertTo-Items($Value) {
    $items = @()
    foreach ($item in $Value) { if ($null -ne $item) { $items += $item } }
    return ,$items
}

function Get-AccessToken($Username, $Password = $Username) {
    (Invoke-RestMethod -Method Post -Uri "$keycloakUrl/realms/legalflow/protocol/openid-connect/token" `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{ client_id = 'legalflow-frontend'; grant_type = 'password'; username = $Username; password = $Password }).access_token
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
    if ($null -ne $Body) { $parameters.ContentType = 'application/json; charset=utf-8'; $parameters.Body = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Body)) }
    try {
        $response = Invoke-WebRequest @parameters
        $json = $null
        if ($response.Content) { $json = [Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray()) | ConvertFrom-Json }
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Json = $json }
    } catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        return [pscustomobject]@{ Status = $status; Json = $null }
    }
}

function Assert-Equal($Label, $Actual, $Expected) {
    if ("$Actual" -eq "$Expected") { Write-Output "PASS  $Label -> $Actual" }
    else { Write-Output "FAIL  $Label -> $Actual (attendu $Expected)"; $script:failures++ }
}

function Assert-True($Label, $Condition) {
    if ($Condition) { Write-Output "PASS  $Label" }
    else { Write-Output "FAIL  $Label"; $script:failures++ }
}

function Has-Id($Items, $Id) { return ($null -ne ((ConvertTo-Items $Items) | Where-Object { $_.id -eq $Id })) }

$admin = Get-AccessToken 'admin'
$secretary = Get-AccessToken 'secretary-a'
$lawyerA = Get-AccessToken 'lawyer-a'
$lawyerA2 = Get-AccessToken 'lawyer-a2'
$lawyerAId = (ConvertFrom-JwtPayload $lawyerA).sub
$lawyerA2Id = (ConvertFrom-JwtPayload $lawyerA2).sub
$n = Get-Random -Maximum 999999
$future = (Get-Date).AddYears(1).ToString('yyyy-MM-dd')

Write-Output "--- 1. Un client créé par l'administrateur n'est confié à aucun avocat"
$created = Invoke-Api POST '/api/clients' $admin @{ firstName = 'Scope'; lastName = "Test$n"; email = "scope-$n@dev-only.test" }
Assert-Equal 'ADMIN POST /api/clients' $created.Status 201
$clientId = $created.Json.id
Assert-True 'aucun avocat en charge au départ' ((ConvertTo-Items $created.Json.lawyerIds).Count -eq 0)
Assert-True 'ADMIN voit ce client' (Has-Id (Invoke-Api GET '/api/clients' $admin).Json $clientId)
Assert-True 'SECRETARY voit ce client' (Has-Id (Invoke-Api GET '/api/clients' $secretary).Json $clientId)
Assert-True 'lawyer-a ne le voit pas dans sa liste' (-not (Has-Id (Invoke-Api GET '/api/clients' $lawyerA).Json $clientId))
Assert-True 'lawyer-a2 ne le voit pas dans sa liste' (-not (Has-Id (Invoke-Api GET '/api/clients' $lawyerA2).Json $clientId))
Assert-Equal 'lawyer-a GET /api/clients/{id}' (Invoke-Api GET "/api/clients/$clientId" $lawyerA).Status 404
Assert-Equal 'lawyer-a2 GET /api/clients/{id}' (Invoke-Api GET "/api/clients/$clientId" $lawyerA2).Status 404

Write-Output "--- 2. Seule l'administration désigne les avocats en charge"
Assert-Equal 'LAWYER PUT /api/clients/{id}/lawyers (interdit)' (Invoke-Api PUT "/api/clients/$clientId/lawyers" $lawyerA @{ lawyerIds = @($lawyerAId) }).Status 403
$assigned = Invoke-Api PUT "/api/clients/$clientId/lawyers" $admin @{ lawyerIds = @($lawyerAId) }
Assert-Equal 'ADMIN PUT /api/clients/{id}/lawyers' $assigned.Status 200
Assert-True 'lawyer-a voit maintenant ce client' (Has-Id (Invoke-Api GET '/api/clients' $lawyerA).Json $clientId)
Assert-Equal 'lawyer-a GET /api/clients/{id}' (Invoke-Api GET "/api/clients/$clientId" $lawyerA).Status 200
Assert-Equal 'lawyer-a2 GET /api/clients/{id} (toujours interdit)' (Invoke-Api GET "/api/clients/$clientId" $lawyerA2).Status 404

Write-Output "--- 3. Dossiers : un avocat n'ouvre que ses propres dossiers"
Assert-Equal 'lawyer-a2 POST /api/cases pour un client qui ne lui est pas confié' (Invoke-Api POST '/api/cases' $lawyerA2 @{ clientId = $clientId; lawyerId = $lawyerA2Id; reference = "SC-X-$n"; title = 'Refusé' }).Status 422
Assert-Equal 'lawyer-a POST /api/cases assigné à un autre avocat' (Invoke-Api POST '/api/cases' $lawyerA @{ clientId = $clientId; lawyerId = $lawyerA2Id; reference = "SC-Y-$n"; title = 'Refusé' }).Status 403
$case = Invoke-Api POST '/api/cases' $lawyerA @{ clientId = $clientId; lawyerId = $lawyerAId; reference = "SC-A-$n"; title = 'Dossier de lawyer-a' }
Assert-Equal 'lawyer-a POST /api/cases (le sien)' $case.Status 201
$caseId = $case.Json.id
Assert-True 'lawyer-a voit son dossier' (Has-Id (Invoke-Api GET '/api/cases' $lawyerA).Json $caseId)
Assert-True 'lawyer-a2 ne voit pas ce dossier dans sa liste' (-not (Has-Id (Invoke-Api GET '/api/cases' $lawyerA2).Json $caseId))
Assert-Equal 'lawyer-a2 GET /api/cases/{id}' (Invoke-Api GET "/api/cases/$caseId" $lawyerA2).Status 404
Assert-True 'SECRETARY voit ce dossier' (Has-Id (Invoke-Api GET '/api/cases' $secretary).Json $caseId)

Write-Output "--- 4. L'administration ne peut assigner qu'un avocat en charge du client"
Assert-Equal 'SECRETARY POST /api/cases avec lawyer-a2 (pas en charge)' (Invoke-Api POST '/api/cases' $secretary @{ clientId = $clientId; lawyerId = $lawyerA2Id; reference = "SC-B-$n"; title = 'À assigner' }).Status 422
Assert-Equal 'ADMIN PUT lawyers [lawyer-a, lawyer-a2]' (Invoke-Api PUT "/api/clients/$clientId/lawyers" $admin @{ lawyerIds = @($lawyerAId, $lawyerA2Id) }).Status 200
$case2 = Invoke-Api POST '/api/cases' $secretary @{ clientId = $clientId; lawyerId = $lawyerA2Id; reference = "SC-C-$n"; title = 'Dossier de lawyer-a2' }
Assert-Equal 'SECRETARY POST /api/cases pour lawyer-a2' $case2.Status 201
$case2Id = $case2.Json.id
Assert-True 'lawyer-a2 voit son dossier' (Has-Id (Invoke-Api GET '/api/cases' $lawyerA2).Json $case2Id)
Assert-True 'lawyer-a2 ne voit toujours pas le dossier de lawyer-a' (-not (Has-Id (Invoke-Api GET '/api/cases' $lawyerA2).Json $caseId))
Assert-True 'lawyer-a ne voit pas le dossier de lawyer-a2' (-not (Has-Id (Invoke-Api GET '/api/cases' $lawyerA).Json $case2Id))

Write-Output "--- 5. Un avocat qui crée un client en devient le seul responsable"
$own = Invoke-Api POST '/api/clients' $lawyerA @{ firstName = 'Propre'; lastName = "Client$n"; email = "own-$n@dev-only.test"; lawyerIds = @($lawyerA2Id) }
Assert-Equal 'lawyer-a POST /api/clients' $own.Status 201
$ownId = $own.Json.id
Assert-True 'lawyer-a est le seul avocat en charge (lawyerIds de la requête ignoré)' ((ConvertTo-Items $own.Json.lawyerIds).Count -eq 1 -and "$((ConvertTo-Items $own.Json.lawyerIds)[0])" -eq $lawyerAId)
Assert-True 'lawyer-a voit son client' (Has-Id (Invoke-Api GET '/api/clients' $lawyerA).Json $ownId)
Assert-True 'lawyer-a2 ne voit pas ce client' (-not (Has-Id (Invoke-Api GET '/api/clients' $lawyerA2).Json $ownId))

Write-Output "--- 6. Factures : visibles selon le dossier"
$invoice = Invoke-Api POST '/api/invoices' $lawyerA @{ lawFirmId = $firmA; caseId = $caseId; clientId = $clientId; invoiceNumber = "INV-SC-$n"; description = 'Honoraires'; amount = 100.5; dueDate = $future }
Assert-Equal 'lawyer-a POST /api/invoices (son dossier)' $invoice.Status 201
$invoiceId = $invoice.Json.id
Assert-True 'lawyer-a voit sa facture' (Has-Id (Invoke-Api GET '/api/invoices' $lawyerA).Json $invoiceId)
Assert-True 'lawyer-a2 ne voit pas cette facture' (-not (Has-Id (Invoke-Api GET '/api/invoices' $lawyerA2).Json $invoiceId))
Assert-Equal 'lawyer-a2 GET /api/invoices/{id}' (Invoke-Api GET "/api/invoices/$invoiceId" $lawyerA2).Status 404
Assert-True 'SECRETARY voit cette facture' (Has-Id (Invoke-Api GET '/api/invoices' $secretary).Json $invoiceId)

if ($script:failures -gt 0) { Write-Output "`n$($script:failures) test(s) en échec."; exit 1 }
Write-Output "`nCloisonnement des avocats validé."
