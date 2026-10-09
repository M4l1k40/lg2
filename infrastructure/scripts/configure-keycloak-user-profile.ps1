$ErrorActionPreference = 'Stop'

$keycloakUrl = 'http://localhost:8080'
$realm = 'legalflow'
$adminUsername = 'admin'
$adminPassword = 'admin'
$profilePath = Join-Path $PSScriptRoot 'legalflow-user-profile.json'

# Attributs "admin uniquement" exposés comme claims dans le token : nom -> fichier de client scope
$claimScopes = [ordered]@{
    lawFirmId = (Join-Path $PSScriptRoot 'legalflow-client-scope.json')
    clientId  = (Join-Path $PSScriptRoot 'legalflow-clientid-scope.json')
}

# Invoke-RestMethod peut renvoyer un tableau JSON "en bloc" : on le déplie toujours en vrai tableau.
function Get-Items($Uri, $Headers) {
    $response = Invoke-RestMethod -Method Get -Uri $Uri -Headers $Headers
    $items = @()
    foreach ($item in $response) { $items += $item }
    return ,$items
}

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

$clientsUri = "$keycloakUrl/admin/realms/$realm/clients?clientId=legalflow-frontend"
$frontendClients = Get-Items $clientsUri $headers
if ($frontendClients.Count -ne 1) {
    throw 'Expected exactly one legalflow-frontend client.'
}
$frontendClientId = $frontendClients[0].id
$defaultScopesUri = "$keycloakUrl/admin/realms/$realm/clients/$frontendClientId/default-client-scopes"

foreach ($claimName in $claimScopes.Keys) {
    $clientScopeConfig = Get-Content -Path $claimScopes[$claimName] -Raw | ConvertFrom-Json
    $clientScopes = Get-Items $clientScopeUri $headers
    $claimScope = $clientScopes | Where-Object { $_.name -eq $claimName } | Select-Object -First 1
    if ($null -eq $claimScope) {
        Invoke-RestMethod -Method Post -Uri $clientScopeUri -Headers $headers `
            -ContentType 'application/json' `
            -Body (ConvertTo-Json -InputObject $clientScopeConfig -Depth 12) | Out-Null
        $clientScopes = Get-Items $clientScopeUri $headers
        $claimScope = $clientScopes | Where-Object { $_.name -eq $claimName } | Select-Object -First 1
    }
    if ($null -eq $claimScope) {
        throw "Keycloak $claimName client scope was not created."
    }

    $defaultScopes = Get-Items $defaultScopesUri $headers
    if (-not ($defaultScopes | Where-Object { $_.name -eq $claimName })) {
        Invoke-RestMethod -Method Put -Uri "$defaultScopesUri/$($claimScope.id)" `
            -Headers $headers | Out-Null
    }
}

$defaultScopes = Get-Items $defaultScopesUri $headers
$defaultScopeNames = @($defaultScopes | ForEach-Object { $_.name })
$requiredDefaultScopes = @('basic', 'web-origins', 'acr', 'roles', 'profile', 'email') + @($claimScopes.Keys)
$missingScopes = @($requiredDefaultScopes | Where-Object { $_ -notin $defaultScopeNames })
if ($missingScopes.Count -gt 0) {
    throw "Frontend client is missing default client scopes: $($missingScopes -join ', ')"
}

$profileConfig = Get-Content -Path $profilePath -Raw | ConvertFrom-Json
$profileUri = "$keycloakUrl/admin/realms/$realm/users/profile"
$profile = Invoke-RestMethod -Method Get -Uri $profileUri -Headers $headers
$managedNames = @($claimScopes.Keys)
$profile.attributes = @($profile.attributes | Where-Object { $_.name -notin $managedNames }) + @($profileConfig.attributes)

Invoke-RestMethod -Method Put -Uri $profileUri -Headers $headers `
    -ContentType 'application/json' `
    -Body (ConvertTo-Json -InputObject $profile -Depth 20) | Out-Null

$configuredProfile = Invoke-RestMethod -Method Get -Uri $profileUri -Headers $headers
foreach ($claimName in $claimScopes.Keys) {
    $attribute = $configuredProfile.attributes | Where-Object { $_.name -eq $claimName }

    if ($null -eq $attribute -or
        $attribute.permissions.view -notcontains 'admin' -or
        $attribute.permissions.edit -notcontains 'admin' -or
        $attribute.permissions.edit -contains 'user') {
        throw "Keycloak $claimName user-profile permissions did not verify as admin-only."
    }
    Write-Output "PASS: Keycloak $claimName profile is configured and editable only in admin context."
}

Write-Output "PASS: legalflow-frontend default scopes: $($requiredDefaultScopes -join ', ')"
