# Crée (ou met à jour) le compte de service Keycloak utilisé par le user-service pour créer
# et gérer les utilisateurs. Idempotent : on peut le relancer sans risque.
#
# Le secret ci-dessous est un secret de DÉVELOPPEMENT. En production : générer un secret fort,
# le fournir au user-service via KEYCLOAK_ADMIN_CLIENT_SECRET (variable d'environnement ou secret Docker),
# et ne jamais le committer.
param(
    [string]$ClientSecret = 'dev-only-user-service-secret'
)
$ErrorActionPreference = 'Stop'

$keycloakUrl = 'http://localhost:8080'
$realm = 'legalflow'
$serviceClientId = 'legalflow-user-service'
$adminUsername = 'admin'
$adminPassword = 'admin'
$requiredRoles = @('manage-users', 'view-users', 'query-users', 'view-realm')

function Get-Items($Uri, $Headers) {
    $response = Invoke-RestMethod -Method Get -Uri $Uri -Headers $Headers
    $items = @()
    foreach ($item in $response) { $items += $item }
    return ,$items
}

$tokenResponse = Invoke-RestMethod -Method Post `
    -Uri "$keycloakUrl/realms/master/protocol/openid-connect/token" `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{ client_id = 'admin-cli'; grant_type = 'password'; username = $adminUsername; password = $adminPassword }
$headers = @{ Authorization = "Bearer $($tokenResponse.access_token)" }
$adminUri = "$keycloakUrl/admin/realms/$realm"

# 1) Client confidentiel avec compte de service (aucune connexion interactive possible)
$clientRepresentation = @{
    clientId                  = $serviceClientId
    name                      = 'LegalFlow user-service (compte de service)'
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
    Invoke-RestMethod -Method Post -Uri "$adminUri/clients" -Headers $headers -ContentType 'application/json' `
        -Body (ConvertTo-Json -InputObject $clientRepresentation -Depth 6) | Out-Null
    Write-Output "Client $serviceClientId créé."
} else {
    $clientRepresentation.id = $existing[0].id
    Invoke-RestMethod -Method Put -Uri "$adminUri/clients/$($existing[0].id)" -Headers $headers -ContentType 'application/json' `
        -Body (ConvertTo-Json -InputObject $clientRepresentation -Depth 6) | Out-Null
    Write-Output "Client $serviceClientId mis à jour."
}
$serviceClient = (Get-Items "$adminUri/clients?clientId=$serviceClientId" $headers)[0]

# 2) Rôles minimaux du compte de service (realm-management) : gérer les utilisateurs, rien de plus
$serviceAccountUser = Invoke-RestMethod -Method Get -Uri "$adminUri/clients/$($serviceClient.id)/service-account-user" -Headers $headers
$realmManagement = (Get-Items "$adminUri/clients?clientId=realm-management" $headers)[0]
$availableRoles = Get-Items "$adminUri/clients/$($realmManagement.id)/roles" $headers
$rolesToGrant = @($availableRoles | Where-Object { $_.name -in $requiredRoles })
if ($rolesToGrant.Count -ne $requiredRoles.Count) {
    throw "Rôles realm-management introuvables : $($requiredRoles -join ', ')"
}
Invoke-RestMethod -Method Post -Uri "$adminUri/users/$($serviceAccountUser.id)/role-mappings/clients/$($realmManagement.id)" `
    -Headers $headers -ContentType 'application/json' -Body (ConvertTo-Json -InputObject @($rolesToGrant) -Depth 6) | Out-Null
Write-Output "PASS: rôles accordés au compte de service : $($requiredRoles -join ', ')"

# 3) Vérification : le compte de service obtient un token et peut lire les utilisateurs et les rôles
$serviceToken = (Invoke-RestMethod -Method Post -Uri "$keycloakUrl/realms/$realm/protocol/openid-connect/token" `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{ grant_type = 'client_credentials'; client_id = $serviceClientId; client_secret = $ClientSecret }).access_token
$serviceHeaders = @{ Authorization = "Bearer $serviceToken" }
$null = Invoke-RestMethod -Method Get -Uri "$adminUri/users?max=1" -Headers $serviceHeaders
$null = Invoke-RestMethod -Method Get -Uri "$adminUri/roles/LAWYER" -Headers $serviceHeaders
$null = Invoke-RestMethod -Method Get -Uri "$adminUri/roles/LAWYER/users?max=1" -Headers $serviceHeaders
Write-Output "PASS: le compte de service lit les utilisateurs et les rôles."
Write-Output "Secret à fournir au user-service : KEYCLOAK_ADMIN_CLIENT_SECRET=$ClientSecret (développement uniquement)"
