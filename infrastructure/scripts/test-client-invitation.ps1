# Vérifie l'invitation d'un client : création du compte sans mot de passe, email d'invitation (Mailpit),
# renvoi, et droits d'accès. Prérequis : configure-keycloak-realm-email.ps1 exécuté, stack reconstruite.
$ErrorActionPreference = 'Stop'

$keycloakUrl = 'http://localhost:8080'
$gatewayUrl = 'http://localhost:9000'
$mailpitUrl = 'http://localhost:8025'
$script:failures = 0

function Get-AccessToken($Username, $Password = $Username) {
    (Invoke-RestMethod -Method Post -Uri "$keycloakUrl/realms/legalflow/protocol/openid-connect/token" `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{ client_id = 'legalflow-frontend'; grant_type = 'password'; username = $Username; password = $Password }).access_token
}

function Invoke-Api($Method, $Path, $Token, $Body = $null) {
    $parameters = @{
        Uri = "$gatewayUrl$Path"; Method = $Method; Headers = @{ Authorization = "Bearer $Token" }
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Body))
    }
    try {
        $response = Invoke-WebRequest @parameters
        $json = $null
        if ($response.Content) { $json = [Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray()) | ConvertFrom-Json }
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

# Write-Host (et non Write-Output) : dans une fonction, Write-Output serait capturé dans la valeur de retour.
function Assert-That($Label, $Condition) {
    if ($Condition) { Write-Host "PASS  $Label" } else { Write-Host "FAIL  $Label"; $script:failures++ }
}

function Get-InvitationMails($Address) {
    $result = Invoke-RestMethod -Uri "$mailpitUrl/api/v1/search?query=$([uri]::EscapeDataString("to:$Address"))"
    return @($result.messages)
}

$lawyer = Get-AccessToken 'lawyer-a'
$lawyerOtherFirm = Get-AccessToken 'lawyer-b'
$client = Get-AccessToken 'client-a'

Write-Output "--- 1. L'avocat crée une fiche client (sans compte)"
$address = "invite-$([guid]::NewGuid().ToString().Substring(0, 8))@dev-only.test"
$created = Invoke-Api POST '/api/clients' $lawyer @{ firstName = 'Invite'; lastName = 'Test'; email = $address; phone = '555-0199'; address = 'Cabinet A' }
Assert-That "LAWYER POST /api/clients -> $($created.Status) (attendu 201)" ($created.Status -eq 201)
if ($created.Status -ne 201) { Write-Host "      réponse : $($created.Error)"; throw "Création de la fiche impossible, arrêt." }
$clientId = $created.Json.id

Write-Output "--- 2. Invitation"
$invited = Invoke-Api POST "/api/users/clients/$clientId/invitation" $lawyer
Assert-That "LAWYER POST invitation -> $($invited.Status) (attendu 200)" ($invited.Status -eq 200)
if ($invited.Status -ne 200) {
    Write-Host "      réponse : $($invited.Error)"
    throw "Invitation impossible, arrêt (le SMTP du realm est-il configuré ? configure-keycloak-realm-email.ps1)."
}
Assert-That 'statut INVITED (compte créé)' ($invited.Json.status -eq 'INVITED')

$mails = @()
for ($attempt = 0; $attempt -lt 20 -and $mails.Count -eq 0; $attempt++) {
    Start-Sleep -Seconds 1
    $mails = @(Get-InvitationMails $address)
}
Assert-That "un email d'invitation est arrivé dans Mailpit pour $address" ($mails.Count -ge 1)
if ($mails.Count -ge 1) {
    $body = Invoke-RestMethod -Uri "$mailpitUrl/api/v1/message/$($mails[0].ID)"
    $link = [regex]::Match([string]$body.Text, 'http\S*/login-actions/action-token\S*').Value
    Assert-That "l'email contient un lien d'action Keycloak à durée limitée" ($link -ne '')
    Write-Output "      ouvrez-le dans Mailpit (http://localhost:8025) pour choisir le mot de passe"
}

Write-Output "--- 3. Le compte existe, lié à la fiche, sans aucun mot de passe"
$adminToken = (Invoke-RestMethod -Method Post -Uri "$keycloakUrl/realms/master/protocol/openid-connect/token" `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{ client_id = 'admin-cli'; grant_type = 'password'; username = 'admin'; password = 'admin' }).access_token
$adminHeaders = @{ Authorization = "Bearer $adminToken" }
# Réponses lues en texte brut : Windows PowerShell 5.1 livre un JSON vide ([]) comme UN objet, ce qui fausse .Count.
# ForEach-Object { $_ } aplatit le tableau ; la liste d'identifiants est comparée à "[]" exactement.
$accountsRaw = (Invoke-WebRequest -UseBasicParsing -Headers $adminHeaders `
    -Uri "$keycloakUrl/admin/realms/legalflow/users?email=$([uri]::EscapeDataString($address))&exact=true").Content
$accounts = @($accountsRaw | ConvertFrom-Json | ForEach-Object { $_ })
Assert-That 'un compte Keycloak a été créé pour ce client' ($accounts.Count -eq 1)
if ($accounts.Count -eq 1) {
    $credentialsRaw = (Invoke-WebRequest -UseBasicParsing -Headers $adminHeaders `
        -Uri "$keycloakUrl/admin/realms/legalflow/users/$($accounts[0].id)/credentials").Content
    Assert-That 'le compte n''a aucun mot de passe : le client le choisira via le lien' ($credentialsRaw.Trim() -eq '[]')
    Assert-That 'le compte est lié à la fiche (attribut clientId)' ($accounts[0].attributes.clientId[0] -eq $clientId)
    Assert-That 'le compte est rattaché au cabinet de l''avocat (attribut lawFirmId)' ([bool]$accounts[0].attributes.lawFirmId[0])
}

Write-Output "--- 4. Renvoi"
$again = Invoke-Api POST "/api/users/clients/$clientId/invitation" $lawyer
Assert-That "LAWYER renvoie l'invitation -> $($again.Status) (attendu 200)" ($again.Status -eq 200)
Assert-That 'statut REINVITED (le compte n''est pas recréé)' ($again.Json.status -eq 'REINVITED')

Write-Output "--- 5. Droits"
Assert-That 'CLIENT ne peut inviter personne (403)' ((Invoke-Api POST "/api/users/clients/$clientId/invitation" $client).Status -eq 403)
Assert-That "LAWYER d'un autre cabinet ne peut pas inviter ce client (422)" ((Invoke-Api POST "/api/users/clients/$clientId/invitation" $lawyerOtherFirm).Status -eq 422)

if ($script:failures -gt 0) { Write-Output "`n$($script:failures) échec(s)."; exit 1 }
Write-Output "`nTous les contrôles sont passés."
