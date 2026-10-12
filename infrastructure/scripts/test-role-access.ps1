# Vérifie le contrôle d'accès par rôle (clients et dossiers) à travers la gateway.
# Prérequis : backend reconstruit, configure-keycloak-user-profile.ps1 puis seed-keycloak-users.ps1 exécutés.
$ErrorActionPreference = 'Stop'

$keycloakUrl = 'http://localhost:8080'
$gatewayUrl = 'http://localhost:9000'
$script:failures = 0

# Normalise une valeur JSON (tableau, objet seul ou null) en vrai tableau sans éléments null.
function ConvertTo-Items($Value) {
    $items = @()
    foreach ($item in $Value) { if ($null -ne $item) { $items += $item } }
    return ,$items
}

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

# Retourne le code HTTP et le corps JSON (sans lever d'exception sur 4xx/5xx)
function Invoke-Api($Method, $Path, $Token, $Body = $null) {
    $parameters = @{
        Uri = "$gatewayUrl$Path"; Method = $Method; Headers = @{ Authorization = "Bearer $Token" }
        UseBasicParsing = $true
    }
    if ($null -ne $Body) { $parameters.ContentType = 'application/json'; $parameters.Body = (ConvertTo-Json -InputObject $Body) }
    try {
        $response = Invoke-WebRequest @parameters
        $json = if ($response.Content) { $response.Content | ConvertFrom-Json } else { $null }
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Json = $json }
    } catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        return [pscustomobject]@{ Status = $status; Json = $null }
    }
}

function Assert-Status($Label, $Actual, $Expected) {
    if ($Actual -eq $Expected) {
        Write-Output "PASS  $Label -> $Actual"
    } else {
        Write-Output "FAIL  $Label -> $Actual (attendu $Expected)"
        $script:failures++
    }
}

$lawyer = Get-AccessToken 'lawyer-a'
$secretary = Get-AccessToken 'secretary-a'
$client = Get-AccessToken 'client-a'
$ownClientId = (ConvertFrom-JwtPayload $client).clientId
if (-not $ownClientId) { throw "client-a n'a pas de claim clientId : relancez configure-keycloak-user-profile.ps1 puis seed-keycloak-users.ps1." }

Write-Output "--- Avocat"
Assert-Status 'LAWYER   GET  /api/clients' (Invoke-Api GET '/api/clients' $lawyer).Status 200
Assert-Status 'LAWYER   GET  /api/cases' (Invoke-Api GET '/api/cases' $lawyer).Status 200

Write-Output "--- Secrétaire"
Assert-Status 'SECRETARY GET /api/clients' (Invoke-Api GET '/api/clients' $secretary).Status 200
Assert-Status 'SECRETARY GET /api/cases' (Invoke-Api GET '/api/cases' $secretary).Status 200

Write-Output "--- Client"
Assert-Status 'CLIENT   GET  /api/clients (liste interdite)' (Invoke-Api GET '/api/clients' $client).Status 403
Assert-Status 'CLIENT   POST /api/clients (interdit)' (Invoke-Api POST '/api/clients' $client @{ firstName = 'X'; lastName = 'Y'; email = "x-$([guid]::NewGuid())@dev-only.test" }).Status 403
Assert-Status 'CLIENT   POST /api/cases (interdit)' (Invoke-Api POST '/api/cases' $client @{ clientId = $ownClientId; lawyerId = [guid]::NewGuid().ToString(); reference = 'X'; title = 'X' }).Status 403
Assert-Status 'CLIENT   GET  /api/clients/me' (Invoke-Api GET '/api/clients/me' $client).Status 200
Assert-Status 'CLIENT   GET  /api/clients/{sa fiche}' (Invoke-Api GET "/api/clients/$ownClientId" $client).Status 200

$allClients = ConvertTo-Items (Invoke-Api GET '/api/clients' $secretary).Json
$otherClient = $allClients | Where-Object { $_.id -ne $ownClientId } | Select-Object -First 1
if ($otherClient) {
    Assert-Status 'CLIENT   GET  /api/clients/{fiche d un autre}' (Invoke-Api GET "/api/clients/$($otherClient.id)" $client).Status 404
} else {
    Write-Output 'SKIP  aucun autre client dans le cabinet pour tester l isolation'
}

$clientCases = Invoke-Api GET '/api/cases' $client
Assert-Status 'CLIENT   GET  /api/cases' $clientCases.Status 200
$clientCaseItems = ConvertTo-Items $clientCases.Json
$foreignCases = @($clientCaseItems | Where-Object { $_.clientId -ne $ownClientId })
if ($foreignCases.Count -eq 0) {
    Write-Output "PASS  CLIENT ne voit que ses propres dossiers ($($clientCaseItems.Count) dossier(s))"
} else {
    Write-Output "FAIL  CLIENT voit $($foreignCases.Count) dossier(s) d'autres clients"
    $script:failures++
}

$lawyerCases = ConvertTo-Items (Invoke-Api GET '/api/cases' $secretary).Json
$foreignCase = $lawyerCases | Where-Object { $_.clientId -ne $ownClientId } | Select-Object -First 1
if ($foreignCase) {
    Assert-Status 'CLIENT   GET  /api/cases/{dossier d un autre}' (Invoke-Api GET "/api/cases/$($foreignCase.id)" $client).Status 404
}

if ($script:failures -gt 0) { Write-Output "`n$($script:failures) test(s) en échec."; exit 1 }
Write-Output "`nTous les tests de contrôle d'accès passent."
