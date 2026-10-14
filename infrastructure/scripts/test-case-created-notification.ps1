# Vérifie le pipeline RabbitMQ de bout en bout : ouvrir un dossier publie CASE_CREATED, le notification-service
# crée la notification de l'avocat assigné, et la lecture des notifications fonctionne pour chaque rôle.
# Prérequis : stack reconstruite (docker compose up -d --build), seed-keycloak-users.ps1 exécuté,
# et au moins un client confié à lawyer-a.
$ErrorActionPreference = 'Stop'

$keycloakUrl = 'http://localhost:8080'
$gatewayUrl = 'http://localhost:9000'
$script:failures = 0

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

function Invoke-Api($Method, $Path, $Token, $Body = $null) {
    $parameters = @{
        Uri = "$gatewayUrl$Path"; Method = $Method; Headers = @{ Authorization = "Bearer $Token" }
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        # UTF-8 explicite : sans charset, PowerShell 5.1 encode le corps en ISO-8859-1 et casse les accents.
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Body))
    }
    try {
        $response = Invoke-WebRequest @parameters
        $json = if ($response.Content) { $response.Content | ConvertFrom-Json } else { $null }
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

function Assert-That($Label, $Condition) {
    if ($Condition) { Write-Output "PASS  $Label" } else { Write-Output "FAIL  $Label"; $script:failures++ }
}

function Get-CaseNotifications($Token, $CaseId) {
    $response = Invoke-Api GET '/api/notifications' $Token
    $items = ConvertTo-Items $response.Json
    return @($items | Where-Object { $_.type -eq 'CASE_CREATED' -and $_.referenceId -eq $CaseId })
}

$lawyer = Get-AccessToken 'lawyer-a'
$secretary = Get-AccessToken 'secretary-a'
$client = Get-AccessToken 'client-a'
$lawyerSub = (ConvertFrom-JwtPayload $lawyer).sub

Write-Output "--- Préparation"
$clients = ConvertTo-Items (Invoke-Api GET '/api/clients' $lawyer).Json
if ($clients.Count -eq 0) {
    # Un avocat qui crée un client en devient le responsable : pas besoin de passer par la secrétaire.
    Write-Output "Aucun client confié à lawyer-a : création d'un client de test."
    $newClient = Invoke-Api POST '/api/clients' $lawyer @{
        firstName = 'Test'; lastName = 'Evenements'; email = "e2e-$([guid]::NewGuid().ToString().Substring(0, 8))@dev-only.test"
    }
    Assert-That "LAWYER POST /api/clients -> $($newClient.Status) (attendu 201)" ($newClient.Status -eq 201)
    if ($newClient.Status -ne 201) { Write-Output "Réponse du service : $($newClient.Error)"; throw "Création du client de test impossible, arrêt." }
    $clients = @($newClient.Json)
}
$clientId = $clients[0].id

Write-Output "--- Ouverture d'un dossier par l'avocat"
$reference = "E2E-$([guid]::NewGuid().ToString().Substring(0, 8))"
$created = Invoke-Api POST '/api/cases' $lawyer @{
    clientId = $clientId; lawyerId = $lawyerSub; reference = $reference; title = 'Test événement RabbitMQ'
}
Assert-That "LAWYER POST /api/cases -> $($created.Status) (attendu 201)" ($created.Status -eq 201)
if ($created.Status -ne 201) { Write-Output "Réponse du service : $($created.Error)"; throw "Création du dossier impossible, arrêt." }
$caseId = $created.Json.id

Write-Output "--- Notification de l'avocat (asynchrone, 20 s max)"
$found = @()
for ($attempt = 0; $attempt -lt 30 -and $found.Count -eq 0; $attempt++) {
    Start-Sleep -Seconds 1
    $found = Get-CaseNotifications $lawyer $caseId
}
Assert-That "LAWYER voit la notification CASE_CREATED du dossier $reference" ($found.Count -ge 1)
if ($found.Count -ge 1) {
    # L'envoi d'email échoue tant qu'aucun serveur mail n'est branché : la notification reste lisible dans l'application.
    Write-Output "      statut de livraison : $($found[0].status) via $($found[0].channel) ($($found[0].errorMessage))"
} else {
    Write-Output "      dossier attendu : $caseId"
    Write-Output "      notifications vues par l'avocat :"
    $seen = ConvertTo-Items (Invoke-Api GET '/api/notifications' $lawyer).Json
    $seen | Select-Object -First 5 type, status, referenceId | Format-Table -AutoSize | Out-String | Write-Output
}

Write-Output "--- Isolation des destinataires"
Assert-That 'SECRETARY ne voit pas la notification de l''avocat' ((Get-CaseNotifications $secretary $caseId).Count -eq 0)
$clientResponse = Invoke-Api GET '/api/notifications' $client
Assert-That "CLIENT GET /api/notifications -> $($clientResponse.Status) (attendu 200, lecture par clientId)" ($clientResponse.Status -eq 200)
Assert-That 'CLIENT ne voit pas la notification de l''avocat' ((Get-CaseNotifications $client $caseId).Count -eq 0)

if ($script:failures -gt 0) { Write-Output "`n$($script:failures) échec(s)."; exit 1 }
Write-Output "`nTous les contrôles sont passés."
