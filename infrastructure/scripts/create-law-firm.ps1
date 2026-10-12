# Crée un nouveau cabinet : un identifiant lawFirmId et son premier administrateur.
# Les autres comptes (avocats, secrétaires, clients) sont ensuite créés par cet administrateur
# depuis la page "Utilisateurs" du frontend.
#
# Exemple :
#   ./create-law-firm.ps1 -AdminUsername admin-cabinet-c -AdminEmail admin@cabinet-c.test -AdminFirstName Karim -AdminLastName Alaoui
param(
    [Parameter(Mandatory = $true)][string]$AdminUsername,
    [Parameter(Mandatory = $true)][string]$AdminEmail,
    [Parameter(Mandatory = $true)][string]$AdminFirstName,
    [Parameter(Mandatory = $true)][string]$AdminLastName,
    [string]$LawFirmId = [guid]::NewGuid().ToString(),
    [string]$TemporaryPassword = ''
)
$ErrorActionPreference = 'Stop'

$keycloakUrl = 'http://localhost:8080'
$realm = 'legalflow'

function Get-Items($Uri, $Headers) {
    $response = Invoke-RestMethod -Method Get -Uri $Uri -Headers $Headers
    $items = @()
    foreach ($item in $response) { $items += $item }
    return ,$items
}

if ($LawFirmId -notmatch '^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$') { throw "LawFirmId doit être un UUID." }
if ([string]::IsNullOrWhiteSpace($TemporaryPassword)) {
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'.ToCharArray()
    $bytes = New-Object byte[] 14
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $TemporaryPassword = -join ($bytes | ForEach-Object { $alphabet[$_ % $alphabet.Length] })
}

$token = (Invoke-RestMethod -Method Post -Uri "$keycloakUrl/realms/master/protocol/openid-connect/token" `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{ client_id = 'admin-cli'; grant_type = 'password'; username = 'admin'; password = 'admin' }).access_token
$headers = @{ Authorization = "Bearer $token" }
$adminUri = "$keycloakUrl/admin/realms/$realm"

if ((Get-Items "$adminUri/users?username=$AdminUsername&exact=true" $headers).Count -gt 0) {
    throw "L'utilisateur $AdminUsername existe déjà."
}

$body = @{
    username      = $AdminUsername.ToLower()
    email         = $AdminEmail
    firstName     = $AdminFirstName
    lastName      = $AdminLastName
    enabled       = $true
    emailVerified = $true
    attributes    = @{ lawFirmId = @($LawFirmId.ToLower()) }
    credentials   = @(@{ type = 'password'; value = $TemporaryPassword; temporary = $true })
}
Invoke-RestMethod -Method Post -Uri "$adminUri/users" -Headers $headers -ContentType 'application/json' `
    -Body (ConvertTo-Json -InputObject $body -Depth 8) | Out-Null

$user = (Get-Items "$adminUri/users?username=$($AdminUsername.ToLower())&exact=true" $headers)[0]
$adminRole = Invoke-RestMethod -Method Get -Uri "$adminUri/roles/ADMIN" -Headers $headers
Invoke-RestMethod -Method Post -Uri "$adminUri/users/$($user.id)/role-mappings/realm" -Headers $headers `
    -ContentType 'application/json' -Body (ConvertTo-Json -InputObject @($adminRole) -Depth 6) | Out-Null

Write-Output "Cabinet créé."
Write-Output "  lawFirmId           : $($LawFirmId.ToLower())"
Write-Output "  administrateur      : $($AdminUsername.ToLower())"
Write-Output "  mot de passe temporaire : $TemporaryPassword  (à changer à la première connexion)"
