# Teste le parcours complet d'une consultation avec 4 rôles, à travers la gateway :
#   client crée -> secrétaire assigne -> avocat démarre, répond, clôture -> client lit la réponse.
# Prérequis : consultation-service reconstruit, configure-keycloak-user-profile.ps1 et seed-keycloak-users.ps1 exécutés.
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

$client = Get-AccessToken 'client-a'
$secretary = Get-AccessToken 'secretary-a'
$lawyer = Get-AccessToken 'lawyer-a'
$lawyer2 = Get-AccessToken 'lawyer-a2'
$lawyerB = Get-AccessToken 'lawyer-b'
$ownClientId = (ConvertFrom-JwtPayload $client).clientId
$lawyerId = (ConvertFrom-JwtPayload $lawyer).sub
if (-not $ownClientId) { throw "client-a n'a pas de claim clientId : relancez configure-keycloak-user-profile.ps1 puis seed-keycloak-users.ps1." }

Write-Output "--- 1. Le client crée sa demande"
$created = Invoke-Api POST '/api/consultations' $client @{ subject = "Test parcours $(Get-Date -Format 'HHmmss')"; description = 'Je souhaite un avis sur la rupture de mon contrat de travail.' }
Assert-Equal 'CLIENT POST /api/consultations (sans clientId)' $created.Status 201
Assert-Equal 'statut initial' $created.Json.status 'PENDING'
Assert-Equal 'clientId repris du token' $created.Json.clientId $ownClientId
$id = $created.Json.id

$allClients = ConvertTo-Items (Invoke-Api GET '/api/clients' $secretary).Json
$otherClient = $allClients | Where-Object { $_.id -ne $ownClientId } | Select-Object -First 1
if ($otherClient) {
    Assert-Equal 'CLIENT ne peut pas créer pour un autre client' (Invoke-Api POST '/api/consultations' $client @{ clientId = $otherClient.id; subject = 'Usurpation'; description = 'Demande au nom d un autre client.' }).Status 403
}

Write-Output "--- 2. Le client ne peut pas piloter le workflow"
Assert-Equal 'CLIENT PUT assign' (Invoke-Api PUT "/api/consultations/$id/assign" $client @{ lawyerId = $lawyerId }).Status 403
Assert-Equal 'CLIENT PUT start' (Invoke-Api PUT "/api/consultations/$id/start" $client).Status 403
Assert-Equal 'CLIENT PUT answer' (Invoke-Api PUT "/api/consultations/$id/answer" $client @{ answer = 'Je me réponds à moi-même.' }).Status 403
Assert-Equal 'CLIENT PUT close' (Invoke-Api PUT "/api/consultations/$id/close" $client).Status 403

Write-Output "--- 3. Visibilité côté avocats"
$visibleToLawyer2 = ConvertTo-Items (Invoke-Api GET '/api/consultations' $lawyer2).Json
Assert-True 'un avocat du cabinet voit la demande en attente' ($null -ne ($visibleToLawyer2 | Where-Object { $_.id -eq $id }))
Assert-Equal 'avocat d un autre cabinet : GET /{id}' (Invoke-Api GET "/api/consultations/$id" $lawyerB).Status 404

Write-Output "--- 4. La secrétaire assigne l'avocat"
Assert-Equal 'SECRETARY PUT start (interdit)' (Invoke-Api PUT "/api/consultations/$id/start" $secretary).Status 403
$assigned = Invoke-Api PUT "/api/consultations/$id/assign" $secretary @{ lawyerId = $lawyerId }
Assert-Equal 'SECRETARY PUT assign' $assigned.Status 200
Assert-Equal 'statut après assignation' $assigned.Json.status 'ASSIGNED'

Write-Output "--- 5. Seul l'avocat assigné travaille la demande"
Assert-Equal 'autre avocat : GET /{id}' (Invoke-Api GET "/api/consultations/$id" $lawyer2).Status 404
Assert-Equal 'autre avocat : PUT start' (Invoke-Api PUT "/api/consultations/$id/start" $lawyer2).Status 404
$started = Invoke-Api PUT "/api/consultations/$id/start" $lawyer
Assert-Equal 'LAWYER assigné : PUT start' $started.Status 200
Assert-Equal 'statut après démarrage' $started.Json.status 'IN_PROGRESS'
$answerText = 'Voici mon avis : vous pouvez contester cette rupture dans un délai de deux ans.'
$answered = Invoke-Api PUT "/api/consultations/$id/answer" $lawyer @{ answer = $answerText }
Assert-Equal 'LAWYER assigné : PUT answer' $answered.Status 200
Assert-Equal 'statut après réponse' $answered.Json.status 'ANSWERED'

Write-Output "--- 6. Le client lit la réponse"
$clientView = Invoke-Api GET "/api/consultations/$id" $client
Assert-Equal 'CLIENT GET /{id}' $clientView.Status 200
Assert-Equal 'réponse visible par le client' $clientView.Json.answer $answerText

Write-Output "--- 7. Clôture"
$closed = Invoke-Api PUT "/api/consultations/$id/close" $lawyer
Assert-Equal 'LAWYER PUT close' $closed.Status 200
Assert-Equal 'statut final' $closed.Json.status 'CLOSED'
Assert-Equal 'CLIENT PUT cancel sur une consultation clôturée' (Invoke-Api PUT "/api/consultations/$id/cancel" $client).Status 409

if ($otherClient) {
    Write-Output "--- 8. Isolation entre clients"
    $foreign = Invoke-Api POST '/api/consultations' $secretary @{ clientId = $otherClient.id; subject = 'Dossier d un autre client'; description = 'Demande saisie par la secrétaire pour un autre client.' }
    Assert-Equal 'SECRETARY POST pour un autre client' $foreign.Status 201
    Assert-Equal 'CLIENT GET /{consultation d un autre}' (Invoke-Api GET "/api/consultations/$($foreign.Json.id)" $client).Status 404
    $clientList = ConvertTo-Items (Invoke-Api GET '/api/consultations' $client).Json
    $foreignInList = @($clientList | Where-Object { $_.clientId -ne $ownClientId })
    Assert-True 'la liste du client ne contient que ses consultations' ($foreignInList.Count -eq 0)
}

if ($script:failures -gt 0) { Write-Output "`n$($script:failures) test(s) en échec."; exit 1 }
Write-Output "`nParcours complet validé pour les 4 rôles."
