# Crée (ou met à jour) le compte de service Keycloak du notification-service.
# Il lui permet de retrouver les coordonnées d'un client à partir de son clientId (endpoint interne du
# client-service), sans les mettre dans les événements RabbitMQ. Idempotent : on peut le relancer sans risque.
#
# Moindre privilège : le compte n'a QUE le rôle SERVICE_CONTACT_LOOKUP (aucun rôle humain, aucun accès à l'API
# d'administration de Keycloak). Le secret ci-dessous est un secret de DÉVELOPPEMENT : en production, en générer un
# fort, le fournir au notification-service par NOTIFICATION_SERVICE_CLIENT_SECRET, et ne jamais le committer.
param(
    [string]$ClientSecret = 'dev-only-notification-service-secret'
)
$ErrorActionPreference = 'Stop'

$keycloakUrl = 'http://localhost:8080'
$realm = 'legalflow'
$serviceClientId = 'legalflow-notification-service'
$roleName = 'SERVICE_CONTACT_LOOKUP'
$adminUsername = 'admin'
$adminPassword = 'admin'
$script:failures = 0

function Get-Items($Uri, $Headers) {
    $response = Invoke-RestMethod -Method Get -Uri $Uri -Headers $Headers
    $items = @()
    foreach ($item in $response) { $items += $item }
    return ,$items
}

# Corps JSON encodé en UTF-8 : sans charset, PowerShell 5.1 envoie ISO-8859-1 et Keycloak ne peut pas lire les accents.
# La virgule empêche PowerShell de dérouler le tableau d'octets renvoyé par la fonction (sinon le corps envoyé serait
# une liste de nombres et Keycloak répondrait "Cannot parse the JSON").
function ConvertTo-JsonBytes($Object) {
    return ,[Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Object -Depth 6))
}

function ConvertFrom-JwtPayload($Token) {
    $payload = $Token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    while ($payload.Length % 4) { $payload += '=' }
    [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json
}

function Assert-That($Label, $Condition) {
    if ($Condition) { Write-Output "PASS: $Label" } else { Write-Output "FAIL: $Label"; $script:failures++ }
}

$tokenResponse = Invoke-RestMethod -Method Post `
    -Uri "$keycloakUrl/realms/master/protocol/openid-connect/token" `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{ client_id = 'admin-cli'; grant_type = 'password'; username = $adminUsername; password = $adminPassword }
$headers = @{ Authorization = "Bearer $($tokenResponse.access_token)" }
$adminUri = "$keycloakUrl/admin/realms/$realm"

# 1) Rôle technique, distinct de tous les rôles humains
$existingRole = @(Get-Items "$adminUri/roles?search=$roleName" $headers | Where-Object { $_.name -eq $roleName })
if ($existingRole.Count -eq 0) {
    Invoke-RestMethod -Method Post -Uri "$adminUri/roles" -Headers $headers -ContentType 'application/json; charset=utf-8' `
        -Body (ConvertTo-JsonBytes @{ name = $roleName; description = 'Lecture des coordonnees des clients par le notification-service (compte de service uniquement)' }) | Out-Null
    Write-Output "Rôle $roleName créé."
}
$role = Invoke-RestMethod -Method Get -Uri "$adminUri/roles/$roleName" -Headers $headers

# 2) Client confidentiel avec compte de service (aucune connexion interactive possible)
$clientRepresentation = @{
    clientId                  = $serviceClientId
    name                      = 'LegalFlow notification-service (compte de service)'
    enabled                   = $true
    publicClient              = $false
    secret                    = $ClientSecret
    serviceAccountsEnabled    = $true
    standardFlowEnabled       = $false
    directAccessGrantsEnabled = $false
    implicitFlowEnabled       = $false
    protocol                  = 'openid-connect'
}
$existing = Get-Items "$adminUri/clients?clientId=$serviceClientId" $headers
if ($existing.Count -eq 0) {
    Invoke-RestMethod -Method Post -Uri "$adminUri/clients" -Headers $headers -ContentType 'application/json; charset=utf-8' `
        -Body (ConvertTo-JsonBytes $clientRepresentation) | Out-Null
    Write-Output "Client $serviceClientId créé."
} else {
    $clientRepresentation.id = $existing[0].id
    Invoke-RestMethod -Method Put -Uri "$adminUri/clients/$($existing[0].id)" -Headers $headers -ContentType 'application/json; charset=utf-8' `
        -Body (ConvertTo-JsonBytes $clientRepresentation) | Out-Null
    Write-Output "Client $serviceClientId mis à jour."
}
$serviceClient = (Get-Items "$adminUri/clients?clientId=$serviceClientId" $headers)[0]

# 3) Le compte de service reçoit ce seul rôle
$serviceAccountUser = Invoke-RestMethod -Method Get -Uri "$adminUri/clients/$($serviceClient.id)/service-account-user" -Headers $headers
Invoke-RestMethod -Method Post -Uri "$adminUri/users/$($serviceAccountUser.id)/role-mappings/realm" `
    -Headers $headers -ContentType 'application/json; charset=utf-8' -Body (ConvertTo-JsonBytes @(@{ id = $role.id; name = $role.name })) | Out-Null

# 4) Vérification : le token du compte de service porte le rôle, et aucun rôle humain
$serviceToken = (Invoke-RestMethod -Method Post -Uri "$keycloakUrl/realms/$realm/protocol/openid-connect/token" `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{ grant_type = 'client_credentials'; client_id = $serviceClientId; client_secret = $ClientSecret }).access_token
$claims = ConvertFrom-JwtPayload $serviceToken
$roles = @($claims.realm_access.roles)
Write-Output "      émetteur du token (iss) : $($claims.iss)"
Assert-That "le token contient le rôle $roleName" ($roles -contains $roleName)
$humanRoles = @($roles | Where-Object { $_ -in 'ADMIN', 'LAWYER', 'SECRETARY', 'CLIENT' })
Assert-That 'le token ne contient aucun rôle humain (ADMIN, LAWYER, SECRETARY, CLIENT)' ($humanRoles.Count -eq 0)
Assert-That 'le token ne porte pas de lawFirmId : le cabinet doit être passé explicitement' (-not $claims.lawFirmId)

if ($script:failures -gt 0) { exit 1 }
Write-Output "Secret à fournir au notification-service : NOTIFICATION_SERVICE_CLIENT_SECRET=$ClientSecret (développement uniquement)"
