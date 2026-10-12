# Vérifie le user-service à travers la gateway : droits par rôle, création de comptes, isolation entre cabinets.
# Prérequis : user-service et gateway reconstruits (docker-compose.user-service.yml),
# configure-keycloak-user-profile.ps1, configure-keycloak-user-service.ps1 et seed-keycloak-users.ps1 exécutés.
$ErrorActionPreference = 'Stop'

$keycloakUrl = 'http://localhost:8080'
$gatewayUrl = 'http://localhost:9000'
$script:failures = 0

function ConvertTo-Items($Value) {
    $items = @()
    foreach ($item in $Value) { if ($null -ne $item) { $items += $item } }
    return ,$items
}

function Get-AccessToken($Username, $Password = $Username) {
    (Invoke-RestMethod -Method Post -Uri "$keycloakUrl/realms/legalflow/protocol/openid-connect/token" `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{ client_id = 'legalflow-frontend'; grant_type = 'password'; username = $Username; password = $Password }).access_token
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
        $json = $null
        if ($response.Content) { $json = [Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray()) | ConvertFrom-Json }
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Json = $json }
    } catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        return [pscustomobject]@{ Status = $status; Json = $null }
    }
}

function Assert-Equal($Label, $Actual, $Expected) {
    if ("$Actual" -eq "$Expected") { Write-Output "PASS  $Label -> $Actual" }
    else { Write-Output "FAIL  $Label -> $Actual (attendu $Expected)"; $script:failures++ }
}

function Assert-True($Label, $Condition) {
    if ($Condition) { Write-Output "PASS  $Label" }
    else { Write-Output "FAIL  $Label"; $script:failures++ }
}

$admin = Get-AccessToken 'admin'
$secretary = Get-AccessToken 'secretary-a'
$lawyer = Get-AccessToken 'lawyer-a'
$client = Get-AccessToken 'client-a'
$lawyerB = Get-AccessToken 'lawyer-b'
$adminId = (ConvertFrom-JwtPayload $admin).sub
$lawyerAId = (ConvertFrom-JwtPayload $lawyer).sub
$lawyerBId = (ConvertFrom-JwtPayload $lawyerB).sub
$ownClientId = (ConvertFrom-JwtPayload $client).clientId

$suffix = Get-Random -Maximum 999999
$newUsername = "test-lawyer-$suffix"
$newUser = @{ username = $newUsername; email = "$newUsername@dev-only.test"; firstName = 'Test'; lastName = "Avocat$suffix"; role = 'LAWYER' }

Write-Output "--- Droits par rôle"
$allUsers = Invoke-Api GET '/api/users' $admin
Assert-Equal 'ADMIN     GET  /api/users' $allUsers.Status 200
Assert-True 'ADMIN voit lawyer-a avec le rôle LAWYER' ($null -ne ((ConvertTo-Items $allUsers.Json) | Where-Object { $_.username -eq 'lawyer-a' -and $_.role -eq 'LAWYER' }))
Assert-Equal 'SECRETARY GET  /api/users (interdit)' (Invoke-Api GET '/api/users' $secretary).Status 403
Assert-Equal 'LAWYER    GET  /api/users (interdit)' (Invoke-Api GET '/api/users' $lawyer).Status 403
Assert-Equal 'CLIENT    GET  /api/users (interdit)' (Invoke-Api GET '/api/users' $client).Status 403
Assert-Equal 'SECRETARY POST /api/users (interdit)' (Invoke-Api POST '/api/users' $secretary $newUser).Status 403
Assert-Equal 'CLIENT    GET  /api/users/lawyers (interdit)' (Invoke-Api GET '/api/users/lawyers' $client).Status 403

Write-Output "--- Annuaire des avocats et profil"
$lawyers = Invoke-Api GET '/api/users/lawyers' $secretary
Assert-Equal 'SECRETARY GET  /api/users/lawyers' $lawyers.Status 200
$lawyerList = ConvertTo-Items $lawyers.Json
Assert-True 'lawyer-a et lawyer-a2 sont listés' ((@($lawyerList | Where-Object { $_.username -in 'lawyer-a', 'lawyer-a2' })).Count -eq 2)
Assert-True 'lawyer-b (autre cabinet) n''est pas listé' ($null -eq ($lawyerList | Where-Object { $_.id -eq $lawyerBId }))
$me = Invoke-Api GET '/api/users/me' $client
Assert-Equal 'CLIENT    GET  /api/users/me' $me.Status 200
Assert-Equal 'clientId du profil' $me.Json.clientId $ownClientId
$lawyersOfB = ConvertTo-Items (Invoke-Api GET '/api/users/lawyers' $lawyerB).Json
Assert-True 'lawyer-b ne voit aucun avocat du cabinet A' ($null -eq ($lawyersOfB | Where-Object { $_.id -eq $lawyerAId }))

Write-Output "--- Création de comptes"
$created = Invoke-Api POST '/api/users' $admin $newUser
Assert-Equal 'ADMIN     POST /api/users (avocat)' $created.Status 201
Assert-Equal 'rôle du compte créé' $created.Json.user.role 'LAWYER'
Assert-Equal 'longueur du mot de passe temporaire généré' ("$($created.Json.temporaryPassword)".Length) 14
$createdId = $created.Json.user.id
Assert-Equal 'ADMIN     POST doublon (même identifiant)' (Invoke-Api POST '/api/users' $admin $newUser).Status 409
Assert-Equal 'ADMIN     POST CLIENT sans clientId' (Invoke-Api POST '/api/users' $admin @{ username = "c$suffix"; email = "c$suffix@dev-only.test"; firstName = 'C'; lastName = 'Test'; role = 'CLIENT' }).Status 400
Assert-Equal 'ADMIN     POST CLIENT avec une fiche inexistante' (Invoke-Api POST '/api/users' $admin @{ username = "d$suffix"; email = "d$suffix@dev-only.test"; firstName = 'D'; lastName = 'Test'; role = 'CLIENT'; clientId = [guid]::NewGuid().ToString() }).Status 422
Assert-Equal 'ADMIN     POST avocat avec clientId (interdit)' (Invoke-Api POST '/api/users' $admin @{ username = "e$suffix"; email = "e$suffix@dev-only.test"; firstName = 'E'; lastName = 'Test'; role = 'LAWYER'; clientId = $ownClientId }).Status 400
Assert-Equal 'ADMIN     POST rôle inconnu' (Invoke-Api POST '/api/users' $admin @{ username = "f$suffix"; email = "f$suffix@dev-only.test"; firstName = 'F'; lastName = 'Test'; role = 'SUPERUSER' }).Status 400
$afterCreate = ConvertTo-Items (Invoke-Api GET '/api/users/lawyers' $secretary).Json
Assert-True 'le nouvel avocat apparaît dans l''annuaire' ($null -ne ($afterCreate | Where-Object { $_.id -eq $createdId }))

Write-Output "--- Gestion des comptes et isolation"
$disabled = Invoke-Api PUT "/api/users/$createdId/enabled" $admin @{ enabled = $false }
Assert-Equal 'ADMIN     PUT enabled=false' $disabled.Status 200
Assert-Equal 'compte désactivé' $disabled.Json.enabled $false
$afterDisable = ConvertTo-Items (Invoke-Api GET '/api/users/lawyers' $secretary).Json
Assert-True 'un avocat désactivé disparaît de l''annuaire' ($null -eq ($afterDisable | Where-Object { $_.id -eq $createdId }))
Assert-Equal 'ADMIN     désactive son propre compte' (Invoke-Api PUT "/api/users/$adminId/enabled" $admin @{ enabled = $false }).Status 409
Assert-Equal 'ADMIN     modifie un utilisateur d''un autre cabinet' (Invoke-Api PUT "/api/users/$lawyerBId/enabled" $admin @{ enabled = $false }).Status 404
Assert-Equal 'SECRETARY PUT enabled (interdit)' (Invoke-Api PUT "/api/users/$createdId/enabled" $secretary @{ enabled = $true }).Status 403
$reset = Invoke-Api POST "/api/users/$createdId/reset-password" $admin
Assert-Equal 'ADMIN     POST reset-password' $reset.Status 200
Assert-Equal 'longueur du nouveau mot de passe' ("$($reset.Json.temporaryPassword)".Length) 14
Assert-Equal 'ADMIN     reset-password sur un identifiant invalide' (Invoke-Api POST '/api/users/not-a-uuid/reset-password' $admin).Status 404
Assert-Equal 'ADMIN     reset-password sur un utilisateur d''un autre cabinet' (Invoke-Api POST "/api/users/$lawyerBId/reset-password" $admin).Status 404

Write-Output "`nLe compte de test '$newUsername' reste dans Keycloak (désactivé)."
if ($script:failures -gt 0) { Write-Output "`n$($script:failures) test(s) en échec."; exit 1 }
Write-Output "`nuser-service : tous les contrôles passent."
