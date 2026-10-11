# Vérifie le contrôle d'accès par rôle des 6 services restants (rendez-vous, factures, échéances,
# documents, judiciaire, notifications) à travers la gateway.
# Convention : "403" = refusé par le rôle ; "404" = le rôle est autorisé, mais la ressource de test n'existe pas.
# Prérequis : les 6 services reconstruits, configure-keycloak-user-profile.ps1 et seed-keycloak-users.ps1 exécutés.
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
    if ($null -ne $Body) { $parameters.ContentType = 'application/json; charset=utf-8'; $parameters.Body = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Body)) }
    try {
        $response = Invoke-WebRequest @parameters
        return [int]$response.StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return 0
    }
}

function Test-Call($Label, $Method, $Path, $Token, $Expected, $Body = $null) {
    $actual = Invoke-Api $Method $Path $Token $Body
    if ($actual -eq $Expected) { Write-Output "PASS  $Label -> $actual" }
    else { Write-Output "FAIL  $Label -> $actual (attendu $Expected)"; $script:failures++ }
}

$client = Get-AccessToken 'client-a'
$secretary = Get-AccessToken 'secretary-a'
$lawyer = Get-AccessToken 'lawyer-a'
$ownClientId = (ConvertFrom-JwtPayload $client).clientId
if (-not $ownClientId) { throw "client-a n'a pas de claim clientId : relancez configure-keycloak-user-profile.ps1 puis seed-keycloak-users.ps1." }

$missing = [guid]::NewGuid().ToString()
$caseId = [guid]::NewGuid().ToString()
$lawyerId = [guid]::NewGuid().ToString()
$future = (Get-Date).AddYears(1).ToString('yyyy-MM-ddTHH:mm:ss')
$futureDate = (Get-Date).AddYears(1).ToString('yyyy-MM-dd')

$appointmentBody = @{ clientId = $ownClientId; lawyerId = $lawyerId; dateTime = $future; type = 'CLIENT_MEETING' }
$invoiceBody = @{ lawFirmId = '11111111-1111-1111-1111-111111111111'; caseId = $caseId; clientId = $ownClientId; invoiceNumber = 'INV-TEST'; description = 'Honoraires'; amount = 100.5; dueDate = $futureDate }
$documentBody = @{ caseId = $caseId; fileName = 'test.pdf'; fileType = 'application/pdf'; fileSize = 1024; storageKey = 'documents/test.pdf'; visibility = 'CASE_MEMBERS' }
$notificationBody = @{ type = 'CASE_CREATED'; title = 'Test'; message = 'Message de test' }

Write-Output "--- Rendez-vous"
Test-Call 'CLIENT    GET    /api/appointments' GET '/api/appointments' $client 200
Test-Call 'SECRETARY GET    /api/appointments' GET '/api/appointments' $secretary 200
Test-Call 'LAWYER    GET    /api/appointments' GET '/api/appointments' $lawyer 200
Test-Call 'CLIENT    POST   /api/appointments (interdit)' POST '/api/appointments' $client 403 $appointmentBody
Test-Call 'CLIENT    DELETE /api/appointments/{id} (interdit)' DELETE "/api/appointments/$missing" $client 403
Test-Call 'LAWYER    DELETE /api/appointments/{id} (interdit)' DELETE "/api/appointments/$missing" $lawyer 403
Test-Call 'SECRETARY DELETE /api/appointments/{id} (autorisé)' DELETE "/api/appointments/$missing" $secretary 404

Write-Output "--- Factures"
Test-Call 'CLIENT    GET    /api/invoices (les siennes)' GET '/api/invoices' $client 200
Test-Call 'SECRETARY GET    /api/invoices' GET '/api/invoices' $secretary 200
Test-Call 'LAWYER    GET    /api/invoices' GET '/api/invoices' $lawyer 200
Test-Call 'CLIENT    POST   /api/invoices (interdit)' POST '/api/invoices' $client 403 $invoiceBody
Test-Call 'SECRETARY POST   /api/invoices (interdit)' POST '/api/invoices' $secretary 403 $invoiceBody
Test-Call 'CLIENT    PATCH  /api/invoices/{id}/status (interdit)' PATCH "/api/invoices/$missing/status?status=PAID" $client 403
Test-Call 'SECRETARY PATCH  /api/invoices/{id}/status (autorisé)' PATCH "/api/invoices/$missing/status?status=PAID" $secretary 404
Test-Call 'LAWYER    DELETE /api/invoices/{id} (réservé ADMIN)' DELETE "/api/invoices/$missing" $lawyer 403

Write-Output "--- Échéances"
Test-Call 'CLIENT    GET    /api/deadlines (interdit)' GET '/api/deadlines' $client 403
Test-Call 'SECRETARY GET    /api/deadlines (lecture)' GET '/api/deadlines' $secretary 200
Test-Call 'LAWYER    GET    /api/deadlines' GET '/api/deadlines' $lawyer 200
Test-Call 'SECRETARY PATCH  /api/deadlines/{id}/status (interdit)' PATCH "/api/deadlines/$missing/status?status=COMPLETED" $secretary 403
Test-Call 'SECRETARY DELETE /api/deadlines/{id} (interdit)' DELETE "/api/deadlines/$missing" $secretary 403
Test-Call 'LAWYER    DELETE /api/deadlines/{id} (autorisé)' DELETE "/api/deadlines/$missing" $lawyer 404

Write-Output "--- Documents"
Test-Call 'CLIENT    GET    /api/documents/{id} (autorisé)' GET "/api/documents/$missing" $client 404
Test-Call 'CLIENT    POST   /api/documents (interdit)' POST '/api/documents' $client 403 $documentBody
Test-Call 'CLIENT    DELETE /api/documents/{id} (interdit)' DELETE "/api/documents/$missing" $client 403
Test-Call 'SECRETARY DELETE /api/documents/{id} (interdit)' DELETE "/api/documents/$missing" $secretary 403
Test-Call 'LAWYER    DELETE /api/documents/{id} (autorisé)' DELETE "/api/documents/$missing" $lawyer 404

Write-Output "--- Judiciaire"
Test-Call 'CLIENT    GET    /api/judicial-cases (ses dossiers)' GET '/api/judicial-cases' $client 200
Test-Call 'CLIENT    GET    /api/courts (interdit)' GET '/api/courts' $client 403
Test-Call 'SECRETARY GET    /api/courts (lecture)' GET '/api/courts' $secretary 200
Test-Call 'LAWYER    GET    /api/courts' GET '/api/courts' $lawyer 200
Test-Call 'SECRETARY PATCH  /api/judicial-cases/{id}/status (interdit)' PATCH "/api/judicial-cases/$missing/status?status=CLOSED" $secretary 403
Test-Call 'LAWYER    PATCH  /api/judicial-cases/{id}/status (autorisé)' PATCH "/api/judicial-cases/$missing/status?status=CLOSED" $lawyer 404

Write-Output "--- Notifications"
Test-Call 'CLIENT    GET    /api/notifications (les siennes)' GET '/api/notifications' $client 200
Test-Call 'SECRETARY GET    /api/notifications' GET '/api/notifications' $secretary 200
Test-Call 'LAWYER    GET    /api/notifications' GET '/api/notifications' $lawyer 200
Test-Call 'CLIENT    POST   /api/notifications (interdit)' POST '/api/notifications' $client 403 $notificationBody

if ($script:failures -gt 0) { Write-Output "`n$($script:failures) test(s) en échec."; exit 1 }
Write-Output "`nTous les contrôles d'accès des 6 services passent."
