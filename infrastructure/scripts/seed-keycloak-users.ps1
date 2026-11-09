
# Crée (ou met à jour) les utilisateurs de démonstration du cabinet A :
#   - secretary-a / secretary-a  (rôle SECRETARY)
#   - client-a    / client-a     (rôle CLIENT, lié à une fiche client du cabinet A)
#   - lawyer-a2   / lawyer-a2    (rôle LAWYER, second avocat du cabinet A)
#
# Le script est idempotent : on peut le relancer sans risque.
# Prérequis :
#   - Keycloak démarré
#   - Backend démarré
#   - configure-keycloak-user-profile.ps1 déjà exécuté

$ErrorActionPreference = 'Stop'

$keycloakUrl = 'http://localhost:8080'
$gatewayUrl = 'http://localhost:9000'
$realm = 'legalflow'
$firmA = '11111111-1111-1111-1111-111111111111'
$clientEmail = 'client-a@dev-only.test'

# Convertit les réponses REST en tableaux PowerShell.
function Get-Items($Uri, $Headers) {
    $response = Invoke-RestMethod -Method Get -Uri $Uri `
        -Headers $Headers -ErrorAction Stop

    $items = @()
    foreach ($item in $response) {
        $items += $item
    }

    return ,$items
}

# Récupère un token Keycloak.
function Get-AccessToken($Url, $Realm, $ClientId, $Username, $Password) {
    $response = Invoke-RestMethod -Method Post `
        -Uri "$Url/realms/$Realm/protocol/openid-connect/token" `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{
            client_id  = $ClientId
            grant_type = 'password'
            username   = $Username
            password   = $Password
        } `
        -ErrorAction Stop

    return $response.access_token
}

# Décode le contenu d'un JWT.
function ConvertFrom-JwtPayload($Token) {
    $payload = $Token.Split('.')[1].Replace('-', '+').Replace('_', '/')

    while ($payload.Length % 4) {
        $payload += '='
    }

    return [Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String($payload)
    ) | ConvertFrom-Json
}

# ============================================================
# 1. Fiche client "Client A"
# ============================================================

# Authentification de l'administrateur LegalFlow.
$adminApiToken = Get-AccessToken $keycloakUrl $realm `
    'legalflow-frontend' 'admin' 'admin'

$adminApiHeaders = @{
    Authorization = "Bearer $adminApiToken"
}

# Récupération de l'identifiant Keycloak de lawyer-a.
$lawyerToken = Get-AccessToken $keycloakUrl $realm `
    'legalflow-frontend' 'lawyer-a' 'lawyer-a'

$lawyerAId = (ConvertFrom-JwtPayload $lawyerToken).sub

# Recherche de la fiche client.
$clients = Get-Items "$gatewayUrl/api/clients" $adminApiHeaders

$clientRecord = $clients |
    Where-Object { $_.email -eq $clientEmail } |
    Select-Object -First 1

# Création si la fiche n'existe pas.
if ($null -eq $clientRecord) {

    $clientRecord = Invoke-RestMethod -Method Post `
        -Uri "$gatewayUrl/api/clients" `
        -Headers $adminApiHeaders `
        -ContentType 'application/json' `
        -Body (ConvertTo-Json -InputObject @{
            firstName = 'Client'
            lastName  = 'A'
            email     = $clientEmail
            phone     = '555-0100'
            address   = 'Cabinet A'
        }) `
        -ErrorAction Stop

    Write-Output "Fiche client créée : $($clientRecord.id)"
}
else {
    Write-Output "Fiche client existante : $($clientRecord.id)"
}

if ([string]::IsNullOrWhiteSpace([string]$clientRecord.id)) {
    throw "Impossible de récupérer l'identifiant de la fiche Client A."
}

# Affectation de Client A à lawyer-a.
$null = Invoke-RestMethod -Method Put `
    -Uri "$gatewayUrl/api/clients/$($clientRecord.id)/lawyers" `
    -Headers $adminApiHeaders `
    -ContentType 'application/json' `
    -Body (ConvertTo-Json -InputObject @{
        lawyerIds = @($lawyerAId)
    }) `
    -ErrorAction Stop

Write-Output "lawyer-a est en charge de Client A."

# ============================================================
# 2. Configuration des utilisateurs Keycloak
# ============================================================

# Authentification de l'administrateur Keycloak.
$adminToken = Get-AccessToken $keycloakUrl 'master' `
    'admin-cli' 'admin' 'admin'

$adminHeaders = @{
    Authorization = "Bearer $adminToken"
}

$usersUri = "$keycloakUrl/admin/realms/$realm/users"

function Set-LegalFlowUser($Username, $Role, $FirstName, $LastName, $Attributes) {

    # Informations du compte.
    $body = @{
        username        = $Username
        enabled         = $true
        email           = "$Username@dev-only.test"
        emailVerified   = $true
        firstName       = $FirstName
        lastName        = $LastName
        attributes      = $Attributes
        requiredActions = @()
    }

    # Recherche de l'utilisateur.
    $existing = Get-Items "${usersUri}?username=${Username}&exact=true" $adminHeaders

    if ($existing.Count -eq 0) {

        # Création d'un nouvel utilisateur avec mot de passe permanent.
        $body.credentials = @(
            @{
                type      = 'password'
                value     = $Username
                temporary = $false
            }
        )

        Invoke-RestMethod -Method Post `
            -Uri $usersUri `
            -Headers $adminHeaders `
            -ContentType 'application/json' `
            -Body (ConvertTo-Json -InputObject $body -Depth 10) `
            -ErrorAction Stop | Out-Null

        # Récupération de l'utilisateur créé.
        $existing = Get-Items "${usersUri}?username=${Username}&exact=true" $adminHeaders

        if ($existing.Count -ne 1) {
            throw "Impossible de retrouver l'utilisateur créé : $Username"
        }

        Write-Output "Utilisateur créé : $Username"
    }
    else {

        # Mise à jour d'un utilisateur existant.
        Invoke-RestMethod -Method Put `
            -Uri "$usersUri/$($existing[0].id)" `
            -Headers $adminHeaders `
            -ContentType 'application/json' `
            -Body (ConvertTo-Json -InputObject $body -Depth 10) `
            -ErrorAction Stop | Out-Null

        Write-Output "Utilisateur mis à jour : $Username"
    }

    $userId = $existing[0].id

    # Définition du mot de passe permanent.
    # Cette opération est également effectuée pour les utilisateurs existants.
    $credential = @{
        type      = 'password'
        value     = $Username
        temporary = $false
    }

    Invoke-RestMethod -Method Put `
        -Uri "$usersUri/$userId/reset-password" `
        -Headers $adminHeaders `
        -ContentType 'application/json' `
        -Body (ConvertTo-Json -InputObject $credential -Depth 5) `
        -ErrorAction Stop | Out-Null

    # Vérification de l'existence du rôle.
    $roleRepresentation = Invoke-RestMethod -Method Get `
        -Uri "$keycloakUrl/admin/realms/$realm/roles/$Role" `
        -Headers $adminHeaders `
        -ErrorAction Stop

    # Attribution du rôle.
    Invoke-RestMethod -Method Post `
        -Uri "$usersUri/$userId/role-mappings/realm" `
        -Headers $adminHeaders `
        -ContentType 'application/json' `
        -Body (ConvertTo-Json -InputObject @($roleRepresentation) -Depth 10) `
        -ErrorAction Stop | Out-Null

    Write-Output "PASS: $Username - mot de passe permanent, requiredActions vides, rôle $Role."
}

# Création / mise à jour des trois utilisateurs.
Set-LegalFlowUser 'secretary-a' 'SECRETARY' 'Secretary' 'A' @{
    lawFirmId = @($firmA)
}

Set-LegalFlowUser 'client-a' 'CLIENT' 'Client' 'A' @{
    lawFirmId = @($firmA)
    clientId  = @($clientRecord.id)
}

Set-LegalFlowUser 'lawyer-a2' 'LAWYER' 'Lawyer' 'A2' @{
    lawFirmId = @($firmA)
}

# ============================================================
# 3. Vérification des utilisateurs et des claims JWT
# ============================================================

foreach ($username in @('lawyer-a', 'lawyer-a2', 'secretary-a', 'client-a')) {

    $token = Get-AccessToken $keycloakUrl $realm `
        'legalflow-frontend' $username $username

    $claims = ConvertFrom-JwtPayload $token

    $roles = @(
        $claims.realm_access.roles |
        Where-Object { $_ -in @('ADMIN', 'LAWYER', 'SECRETARY', 'CLIENT') }
    )

    Write-Output (
        "{0,-12} roles={1,-10} lawFirmId={2} clientId={3}" -f `
        $claims.preferred_username,
        ($roles -join ','),
        $claims.lawFirmId,
        $claims.clientId
    )

    Write-Output ("{0,-12} id (sub) = {1}" -f '', $claims.sub)

    if ($claims.lawFirmId -ne $firmA) {
        throw "Le claim lawFirmId est incorrect pour $username."
    }

    if ($username -eq 'client-a' -and $claims.clientId -ne $clientRecord.id) {
        throw "Le claim clientId est incorrect pour client-a."
    }
}

Write-Output ""
Write-Output "Tous les utilisateurs et claims Keycloak ont été vérifiés."
