$ErrorActionPreference = 'Stop'

$keycloakUrl = 'http://localhost:8080'
$realm = 'legalflow'
$adminUsername = 'admin'
$adminPassword = 'admin'
$profilePath = Join-Path $PSScriptRoot 'legalflow-user-profile.json'
$clientScopePath = Join-Path $PSScriptRoot 'legalflow-client-scope.json'

$tokenResponse = Invoke-RestMethod -Method Post `
    -Uri "$keycloakUrl/realms/master/protocol/openid-connect/token" `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{
        client_id = 'admin-cli'
        grant_type = 'password'
        username = $adminUsername
        password = $adminPassword
    }

$headers = @{ Authorization = "Bearer $($tokenResponse.access_token)" }
$clientScopeUri = "$keycloakUrl/admin/realms/$realm/client-scopes"
$clientScopeConfig = Get-Content -Path $clientScopePath -Raw | ConvertFrom-Json
$clientScopes = Invoke-RestMethod -Method Get -Uri $clientScopeUri -Headers $headers
$lawFirmScope = $clientScopes | Where-Object { $_.name -eq 'lawFirmId' } | Select-Object -First 1
if ($null -eq $lawFirmScope) {
    Invoke-RestMethod -Method Post -Uri $clientScopeUri -Headers $headers `
        -ContentType 'application/json' `
        -Body (ConvertTo-Json -InputObject $clientScopeConfig -Depth 12) | Out-Null
    $clientScopes = Invoke-RestMethod -Method Get -Uri $clientScopeUri -Headers $headers
    $lawFirmScope = $clientScopes | Where-Object { $_.name -eq 'lawFirmId' } | Select-Object -First 1
}
if ($null -eq $lawFirmScope) {
    throw 'Keycloak lawFirmId client scope was not created.'
}

$clientsUri = "$keycloakUrl/admin/realms/$realm/clients?clientId=legalflow-frontend"
$frontendClients = @(Invoke-RestMethod -Method Get -Uri $clientsUri -Headers $headers)
if ($frontendClients.Count -ne 1) {
    throw 'Expected exactly one legalflow-frontend client.'
}
$frontendClientId = $frontendClients[0].id
$defaultScopesUri = "$keycloakUrl/admin/realms/$realm/clients/$frontendClientId/default-client-scopes"
$defaultScopes = Invoke-RestMethod -Method Get -Uri $defaultScopesUri -Headers $headers
if (-not ($defaultScopes | Where-Object { $_.name -eq 'lawFirmId' })) {
    Invoke-RestMethod -Method Put -Uri "$defaultScopesUri/$($lawFirmScope.id)" `
        -Headers $headers | Out-Null
}

$defaultScopes = Invoke-RestMethod -Method Get -Uri $defaultScopesUri -Headers $headers
$defaultScopeNames = @($defaultScopes | ForEach-Object { $_.name })
$requiredDefaultScopes = @('basic', 'web-origins', 'acr', 'roles', 'profile', 'email', 'lawFirmId')
$missingScopes = @($requiredDefaultScopes | Where-Object { $_ -notin $defaultScopeNames })
if ($missingScopes.Count -gt 0) {
    throw "Frontend client is missing default client scopes: $($missingScopes -join ', ')"
}

$profileConfig = Get-Content -Path $profilePath -Raw | ConvertFrom-Json
$profileUri = "$keycloakUrl/admin/realms/$realm/users/profile"
$profile = Invoke-RestMethod -Method Get -Uri $profileUri -Headers $headers
$profile.attributes = @($profile.attributes | Where-Object { $_.name -ne 'lawFirmId' }) + @($profileConfig.attributes)
$profile.groups = @($profile.groups) + @($profileConfig.groups)

Invoke-RestMethod -Method Put -Uri $profileUri -Headers $headers `
    -ContentType 'application/json' `
    -Body (ConvertTo-Json -InputObject $profile -Depth 20) | Out-Null

$configuredProfile = Invoke-RestMethod -Method Get -Uri $profileUri -Headers $headers
$lawFirmIdAttribute = $configuredProfile.attributes |
    Where-Object { $_.name -eq 'lawFirmId' }

if ($null -eq $lawFirmIdAttribute -or
    $lawFirmIdAttribute.permissions.view -notcontains 'admin' -or
    $lawFirmIdAttribute.permissions.edit -notcontains 'admin' -or
    $lawFirmIdAttribute.permissions.edit -contains 'user') {
    throw 'Keycloak lawFirmId user-profile permissions did not verify as admin-only.'
}

Write-Output 'PASS: Keycloak lawFirmId profile is configured and editable only in admin context.'
Write-Output "PASS: legalflow-frontend default scopes: $($requiredDefaultScopes -join ', ')"
