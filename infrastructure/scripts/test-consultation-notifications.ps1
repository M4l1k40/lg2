# Vérifie les notifications du parcours de consultation (client, secrétaire, avocat) à travers la gateway :
#   client crée -> secrétaire assigne -> avocat démarre, répond, clôture,
# et que chaque rôle ne reçoit que les notifications qui le concernent.
# Les notifications d'un client sont rattachées à sa fiche (clientId), celles du personnel à leur compte (sub).
# Prérequis : stack reconstruite (docker compose up -d --build), seed-keycloak-users.ps1 exécuté.
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

function Step-Api($Label, $Method, $Path, $Token, $Expected, $Body = $null) {
    $result = Invoke-Api $Method $Path $Token $Body
    Assert-That "$Label -> $($result.Status) (attendu $Expected)" ($result.Status -eq $Expected)
    if ($result.Status -ne $Expected) { Write-Host "      réponse : $($result.Error)"; throw "Étape impossible, arrêt." }
    return $result
}

# Renvoie la notification correspondante, ou $null.
function Find-Notification($Token, $Type, $ReferenceId) {
    $items = ConvertTo-Items (Invoke-Api GET '/api/notifications' $Token).Json
    $found = @($items | Where-Object { $_.type -eq $Type -and $_.referenceId -eq $ReferenceId })
    if ($found.Count -gt 0) { return $found[0] }
    return $null
}

function Wait-Notification($Token, $Type, $ReferenceId, $Seconds = 30) {
    for ($attempt = 0; $attempt -lt $Seconds; $attempt++) {
        $notification = Find-Notification $Token $Type $ReferenceId
        if ($null -ne $notification) { return $notification }
        Start-Sleep -Seconds 1
    }
    return $null
}

$client = Get-AccessToken 'client-a'
$secretary = Get-AccessToken 'secretary-a'
$lawyer = Get-AccessToken 'lawyer-a'
$lawyerOtherFirm = Get-AccessToken 'lawyer-b'
$clientId = (ConvertFrom-JwtPayload $client).clientId
$lawyerSub = (ConvertFrom-JwtPayload $lawyer).sub
if (-not $clientId) { throw "client-a n'a pas de claim clientId : relancez configure-keycloak-user-profile.ps1 puis seed-keycloak-users.ps1." }

Write-Output "--- 1. Le client crée sa demande"
$created = Step-Api 'CLIENT POST /api/consultations' POST '/api/consultations' $client 201 @{
    subject = "Test notifications $(Get-Date -Format 'HHmmss')"; description = 'Demande créée par le test des notifications.'
}
$id = $created.Json.id
$notification = Wait-Notification $client 'CONSULTATION_CREATED' $id
Assert-That 'CLIENT reçoit CONSULTATION_CREATED (lu par clientId)' ($null -ne $notification)

Write-Output "--- 2. La secrétaire assigne l'avocat"
Step-Api 'SECRETARY PUT assign' PUT "/api/consultations/$id/assign" $secretary 200 @{ lawyerId = $lawyerSub } | Out-Null
Assert-That 'LAWYER reçoit CONSULTATION_ASSIGNED (lu par sub)' ($null -ne (Wait-Notification $lawyer 'CONSULTATION_ASSIGNED' $id))
Assert-That 'SECRETARY reçoit CONSULTATION_ASSIGNED (elle est l''auteur)' ($null -ne (Wait-Notification $secretary 'CONSULTATION_ASSIGNED' $id))

Write-Output "--- 3. L'avocat démarre, répond et clôture"
Step-Api 'LAWYER PUT start' PUT "/api/consultations/$id/start" $lawyer 200 | Out-Null
Step-Api 'LAWYER PUT answer' PUT "/api/consultations/$id/answer" $lawyer 200 @{ answer = 'Réponse de test, confidentielle.' } | Out-Null
$answered = Wait-Notification $client 'CONSULTATION_ANSWERED' $id
Assert-That 'CLIENT reçoit CONSULTATION_ANSWERED' ($null -ne $answered)
if ($null -ne $answered) {
    Write-Output "      livraison : $($answered.status) via $($answered.channel) ($($answered.errorMessage))"
    Assert-That 'les coordonnées du client ont été retrouvées par le notification-service (statut SENT)' ($answered.status -eq 'SENT')
    Assert-That 'le message ne contient pas la réponse de l''avocat' (-not ($answered.message -like '*confidentielle*'))
}
Step-Api 'LAWYER PUT close' PUT "/api/consultations/$id/close" $lawyer 200 | Out-Null
Assert-That 'CLIENT reçoit CONSULTATION_CLOSED' ($null -ne (Wait-Notification $client 'CONSULTATION_CLOSED' $id))

Write-Output "--- 4. Isolation : chacun ne voit que ce qui le concerne"
Assert-That 'SECRETARY ne voit pas CONSULTATION_ANSWERED' ($null -eq (Find-Notification $secretary 'CONSULTATION_ANSWERED' $id))
Assert-That 'SECRETARY ne voit pas CONSULTATION_CLOSED' ($null -eq (Find-Notification $secretary 'CONSULTATION_CLOSED' $id))
Assert-That 'LAWYER ne voit pas CONSULTATION_CREATED (auteur : le client)' ($null -eq (Find-Notification $lawyer 'CONSULTATION_CREATED' $id))
Assert-That 'CLIENT ne voit pas CONSULTATION_ASSIGNED' ($null -eq (Find-Notification $client 'CONSULTATION_ASSIGNED' $id))
foreach ($type in 'CONSULTATION_CREATED', 'CONSULTATION_ASSIGNED', 'CONSULTATION_ANSWERED', 'CONSULTATION_CLOSED') {
    Assert-That "LAWYER d'un autre cabinet ne voit pas $type" ($null -eq (Find-Notification $lawyerOtherFirm $type $id))
}

if ($script:failures -gt 0) { Write-Output "`n$($script:failures) échec(s)."; exit 1 }
Write-Output "`nTous les contrôles sont passés."
