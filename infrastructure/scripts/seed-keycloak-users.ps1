# Crée (ou met à jour) les utilisateurs de démonstration du cabinet A :
#   - secretary-a / secretary-a  (rôle SECRETARY)
#   - client-a    / client-a     (rôle CLIENT, lié à une fiche client du cabinet A)
#   - lawyer-a2   / lawyer-a2    (rôle LAWYER, second avocat du cabinet A, pour tester l'isolation entre avocats)
# Le script est idempotent : on peut le relancer sans risque.
# Prérequis : backend démarré et ./configure-keycloak-user-profile.ps1 déjà exécuté.
$ErrorActionPreference = 'Stop'

$keycloakUrl = 'http://localhost:8080'
$gatewayUrl = 'http://localhost:9000'
$realm = 'legalflow'
$firmA = '11111111-1111-1111-1111-111111111111'
$clientEmail = 'client-a@dev-only.test'

# Invoke-RestMethod peut renvoyer un tableau JSON "en bloc" : on le déplie toujours en vrai tableau.
function Get-Items($Uri, $Headers) {
    $response = Invoke-RestMethod -Method Get -Uri $Uri -Headers $Headers
    $items = @()
    foreach ($item in $response) { $items += $item }
    return ,$items
}

function Get-AccessToken($Url, $Realm, $ClientId, $Username, $Password) {
    (Invoke-RestMethod -Method Post -Uri "$Url/realms/$Realm/protocol/openid-connect/token" `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{ client_id = $ClientId; grant_type = 'password'; username = $Username; password = $Password }).access_token
}

function ConvertFrom-JwtPayload($Token) {
    $payload = $Token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    while ($payload.Length % 4) { $payload += '=' }
    [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json
}

# 1) Fiche client "Client A" dans le cabinet A : créée par l'administrateur, puis confiée à lawyer-a
#    (un avocat ne voit que les clients dont il est en charge).
$adminApiToken = Get-AccessToken $keycloakUrl $realm 'legalflow-frontend' 'admin' 'admin'
$adminApiHeaders = @{ Authorization = "Bearer $adminApiToken" }
$lawyerToken = Get-AccessToken $keycloakUrl $realm 'legalflow-frontend' 'lawyer-a' 'lawyer-a'
$lawyerAId = (ConvertFrom-JwtPayload $lawyerToken).sub

$clients = Get-Items "$gatewayUrl/api/clients" $adminApiHeaders
$clientRecord = $clients | Where-Object { $_.email -eq $clientEmail } | Select-Object -First 1
if ($null -eq $clientRecord) {
    $clientRecord = Invoke-RestMethod -Method Post -Uri "$gatewayUrl/api/clients" -Headers $adminApiHeaders `
        -ContentType 'application/json' `
        -Body (ConvertTo-Json -InputObject @{
            firstName = 'Client'; lastName = 'A'; email = $clientEmail; phone = '555-0100'; address = 'Cabinet A'
        })
    Write-Output "Fiche client créée : $($clientRecord.id)"
} else {
    Write-Output "Fiche client existante : $($clientRecord.id)"
}
$null = Invoke-RestMethod -Method Put -Uri "$gatewayUrl/api/clients/$($clientRecord.id)/lawyers" -Headers $adminApiHeaders `
    -ContentType 'application/json' -Body (ConvertTo-Json -InputObject @{ lawyerIds = @($lawyerAId) })
Write-Output "lawyer-a est en charge de Client A."

# 2) Utilisateurs Keycloak
$adminToken = Get-AccessToken $keycloakUrl 'master' 'admin-cli' 'admin' 'admin'
$adminHeaders = @{ Authorization = "Bearer $adminToken" }
$usersUri = "$keycloakUrl/admin/realms/$realm/users"

function Set-LegalFlowUser($Username, $Role, $FirstName, $LastName, $Attributes) {
    $body = @{
        username      = $Username
        enabled       = $true
        email         = "$Username@dev-only.test"
        emailVerified = $true
        firstName     = $FirstName
        lastName      = $LastName
        attributes    = $Attributes
    }

    $existing = Get-Items "${usersUri}?username=${Username}&exact=true" $adminHeaders
    if ($existing.Count -eq 0) {
        $body.credentials = @(@{ type = 'password'; value = $Username; temporary = $false })
        Invoke-RestMethod -Method Post -Uri $usersUri -Headers $adminHeaders -ContentType 'application/json' `
            -Body (ConvertTo-Json -InputObject $body -Depth 10) | Out-Null
        $existing = Get-Items "${usersUri}?username=${Username}&exact=true" $adminHeaders
        Write-Output "Utilisateur créé : $Username"
    } else {
        Invoke-RestMethod -Method Put -Uri "$usersUri/$($existing[0].id)" -Headers $adminHeaders -ContentType 'application/json' `
            -Body (ConvertTo-Json -InputObject $body -Depth 10) | Out-Null
        Write-Output "Utilisateur mis à jour : $Username"
    }

    $userId = $existing[0].id
    $roleRepresentation = Invoke-RestMethod -Method Get -Uri "$keycloakUrl/admin/realms/$realm/roles/$Role" -Headers $adminHeaders
    Invoke-RestMethod -Method Post -Uri "$usersUri/$userId/role-mappings/realm" -Headers $adminHeaders `
        -ContentType 'application/json' -Body (ConvertTo-Json -InputObject @($roleRepresentation) -Depth 10) | Out-Null
}

Set-LegalFlowUser 'secretary-a' 'SECRETARY' 'Secretary' 'A' @{ lawFirmId = @($firmA) }
Set-LegalFlowUser 'client-a' 'CLIENT' 'Client' 'A' @{ lawFirmId = @($firmA); clientId = @($clientRecord.id) }
Set-LegalFlowUser 'lawyer-a2' 'LAWYER' 'Lawyer' 'A2' @{ lawFirmId = @($firmA) }

# 3) Vérification : les claims présents dans les tokens
foreach ($username in @('lawyer-a', 'lawyer-a2', 'secretary-a', 'client-a')) {
    $token = Get-AccessToken $keycloakUrl $realm 'legalflow-frontend' $username $username
    $claims = ConvertFrom-JwtPayload $token
    Write-Output ("{0,-12} roles={1,-10} lawFirmId={2} clientId={3}" -f `
        $claims.preferred_username, (($claims.realm_access.roles | Where-Object { $_ -in 'ADMIN','LAWYER','SECRETARY','CLIENT' }) -join ','), $claims.lawFirmId, $claims.clientId)
    Write-Output ("{0,-12} id (sub) = {1}" -f '', $claims.sub)
}
