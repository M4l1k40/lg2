# Vérifie, à travers la gateway et avec chaque rôle, les rendez-vous : création, lecture, modification, statuts,
# isolation (autre avocat, autre cabinet), droits (secrétaire, client) et nettoyage des données créées.
# Prérequis : appointment-service reconstruit avec le PUT, comptes de test présents (seed), un client confié à lawyer-a.
$ErrorActionPreference = 'Stop'

$keycloakUrl = 'http://localhost:8080'
$gatewayUrl = 'http://localhost:9000'
$script:failures = 0

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

function Assert-Status($Label, $Result, $Expected) {
    Assert-That "$Label -> $($Result.Status) (attendu $Expected)" ($Result.Status -eq $Expected)
    if ($Result.Status -ne $Expected -and $Result.Error) { Write-Host "      réponse : $($Result.Error)" }
}

function Contains-Id($Token, $Id) {
    $list = Invoke-Api GET '/api/appointments' $Token
    $items = @($list.Json | ForEach-Object { $_ })
    return [bool]@($items | Where-Object { $_.id -eq $Id }).Count
}

$lawyer = Get-AccessToken 'lawyer-a'
$lawyerSameFirm = Get-AccessToken 'lawyer-a2'
$lawyerOtherFirm = Get-AccessToken 'lawyer-b'
$secretary = Get-AccessToken 'secretary-a'
$client = Get-AccessToken 'client-a'
$lawyerId = (ConvertFrom-JwtPayload $lawyer).sub
$otherLawyerId = (ConvertFrom-JwtPayload $lawyerSameFirm).sub

$clients = @((Invoke-Api GET '/api/clients' $lawyer).Json | ForEach-Object { $_ })
if ($clients.Count -eq 0) { throw "Aucun client confié à lawyer-a : relancez seed-keycloak-users.ps1." }
$clientId = $clients[0].id

$tomorrow = (Get-Date).AddDays(1).ToString("yyyy-MM-dd'T'10:00:00")
$later = (Get-Date).AddDays(3).ToString("yyyy-MM-dd'T'15:30:00")
$yesterday = (Get-Date).AddDays(-1).ToString("yyyy-MM-dd'T'10:00:00")
$created = @()

Write-Output "--- 1. Création par l'avocat"
$first = Invoke-Api POST '/api/appointments' $lawyer @{ clientId = $clientId; lawyerId = $lawyerId; dateTime = $tomorrow; type = 'CLIENT_MEETING'; location = 'Cabinet'; notes = 'Premier rendez-vous' }
Assert-Status 'LAWYER crée un rendez-vous' $first 201
if ($first.Status -ne 201) { throw "Création impossible, arrêt." }
$id = $first.Json.id
$created += $id
Assert-That 'statut initial SCHEDULED' ($first.Json.status -eq 'SCHEDULED')
Assert-That "l'avocat du rendez-vous est bien lawyer-a" ($first.Json.lawyerId -eq $lawyerId)
Assert-Status 'LAWYER ne peut pas créer au nom d''un autre avocat' (Invoke-Api POST '/api/appointments' $lawyer @{ clientId = $clientId; lawyerId = $otherLawyerId; dateTime = $tomorrow; type = 'CLIENT_MEETING' }) 403
Assert-Status 'une date passée est refusée' (Invoke-Api POST '/api/appointments' $lawyer @{ clientId = $clientId; lawyerId = $lawyerId; dateTime = $yesterday; type = 'CLIENT_MEETING' }) 400

Write-Output "--- 2. Lecture"
Assert-That 'LAWYER voit son rendez-vous dans la liste' (Contains-Id $lawyer $id)
Assert-Status 'LAWYER lit le détail' (Invoke-Api GET "/api/appointments/$id" $lawyer) 200
Assert-That 'SECRETARY voit le rendez-vous (personnel de bureau)' (Contains-Id $secretary $id)

Write-Output "--- 3. Isolation"
Assert-That 'un autre avocat du cabinet ne le voit pas' (-not (Contains-Id $lawyerSameFirm $id))
Assert-Status 'un autre avocat du cabinet : détail introuvable' (Invoke-Api GET "/api/appointments/$id" $lawyerSameFirm) 404
Assert-That 'un avocat d''un autre cabinet ne le voit pas' (-not (Contains-Id $lawyerOtherFirm $id))
Assert-Status 'un avocat d''un autre cabinet : détail introuvable' (Invoke-Api GET "/api/appointments/$id" $lawyerOtherFirm) 404

Write-Output "--- 4. Modification"
$edit = @{ dateTime = $later; type = 'COURT_HEARING'; location = 'Tribunal de commerce'; notes = 'Modifié' }
$updated = Invoke-Api PUT "/api/appointments/$id" $lawyer $edit
Assert-Status 'LAWYER modifie son rendez-vous' $updated 200
Assert-That 'le lieu, le type et la date ont été modifiés' ($updated.Json.location -eq 'Tribunal de commerce' -and $updated.Json.type -eq 'COURT_HEARING' -and $updated.Json.dateTime.StartsWith($later.Substring(0, 16)))
Assert-That 'le client et l''avocat n''ont pas changé' ($updated.Json.clientId -eq $clientId -and $updated.Json.lawyerId -eq $lawyerId)
Assert-Status 'un autre avocat ne peut pas le modifier (404)' (Invoke-Api PUT "/api/appointments/$id" $lawyerSameFirm $edit) 404
Assert-Status 'un avocat d''un autre cabinet ne peut pas le modifier (404)' (Invoke-Api PUT "/api/appointments/$id" $lawyerOtherFirm $edit) 404
Assert-Status 'CLIENT ne peut pas modifier (403)' (Invoke-Api PUT "/api/appointments/$id" $client $edit) 403
Assert-Status 'SECRETARY peut modifier' (Invoke-Api PUT "/api/appointments/$id" $secretary @{ dateTime = $later; type = 'COURT_HEARING'; location = 'Salle 2'; notes = 'Par la secrétaire' }) 200

Write-Output "--- 5. Statuts"
Assert-Status 'LAWYER marque terminé' (Invoke-Api PATCH "/api/appointments/$id/status?status=COMPLETED" $lawyer) 200
Assert-Status 'un rendez-vous terminé ne peut plus être modifié (409)' (Invoke-Api PUT "/api/appointments/$id" $lawyer $edit) 409
$second = Invoke-Api POST '/api/appointments' $lawyer @{ clientId = $clientId; lawyerId = $lawyerId; dateTime = $later; type = 'CONSULTATION' }
Assert-Status 'LAWYER crée un second rendez-vous' $second 201
if ($second.Status -eq 201) {
    $created += $second.Json.id
    Assert-Status 'LAWYER annule le second' (Invoke-Api PATCH "/api/appointments/$($second.Json.id)/status?status=CANCELLED" $lawyer) 200
    Assert-Status 'un rendez-vous annulé ne peut plus être modifié (409)' (Invoke-Api PUT "/api/appointments/$($second.Json.id)" $lawyer $edit) 409
}

Write-Output "--- 6. Suppression et nettoyage"
Assert-Status 'LAWYER ne peut pas supprimer (403)' (Invoke-Api DELETE "/api/appointments/$id" $lawyer) 403
foreach ($appointmentId in $created) {
    Assert-Status "SECRETARY supprime $appointmentId" (Invoke-Api DELETE "/api/appointments/$appointmentId" $secretary) 204
}

if ($script:failures -gt 0) { Write-Output "`n$($script:failures) échec(s)."; exit 1 }
Write-Output "`nTous les contrôles sont passés."
