# Vérifie le module judiciaire à travers la gateway avec chaque rôle : tribunaux, juges, procédures, audiences,
# statuts (transitions et règles de cohérence), isolation entre avocats et entre cabinets, droits de chaque rôle.
# Il n'existe aucune suppression côté serveur : les tribunaux, juges et procédures créés par le test restent en base
# (préfixe TEST-JUD pour les repérer). Prérequis : judicial-service reconstruit avec les règles d'écriture.
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

# Copie d'une table de hachage avec certaines valeurs remplacées (l'opérateur + échoue si une clé existe déjà).
function Merge-Hash($Base, $Overrides) {
    $copy = @{}
    foreach ($key in $Base.Keys) { $copy[$key] = $Base[$key] }
    foreach ($key in $Overrides.Keys) { $copy[$key] = $Overrides[$key] }
    return $copy
}

function Assert-Status($Label, $Result, $Expected) {
    Assert-That "$Label -> $($Result.Status) (attendu $Expected)" ($Result.Status -eq $Expected)
    if ($Result.Status -ne $Expected -and $Result.Error) { Write-Host "      réponse : $($Result.Error)" }
}

function Contains-Id($Path, $Token, $Id) {
    $items = @((Invoke-Api GET $Path $Token).Json | ForEach-Object { $_ })
    return [bool]@($items | Where-Object { $_.id -eq $Id }).Count
}

function Get-CaseStatus($Id, $Token) { (Invoke-Api GET "/api/judicial-cases/$Id" $Token).Json.status }

$lawyer = Get-AccessToken 'lawyer-a'
$lawyerSameFirm = Get-AccessToken 'lawyer-a2'
$lawyerOtherFirm = Get-AccessToken 'lawyer-b'
$secretary = Get-AccessToken 'secretary-a'
$client = Get-AccessToken 'client-a'
$lawyerClaims = ConvertFrom-JwtPayload $lawyer
$lawFirmId = $lawyerClaims.lawFirmId
$clientClaimId = (ConvertFrom-JwtPayload $client).clientId

$cases = @((Invoke-Api GET '/api/cases' $lawyer).Json | ForEach-Object { $_ })
if ($cases.Count -eq 0) {
    $clients = @((Invoke-Api GET '/api/clients' $lawyer).Json | ForEach-Object { $_ })
    if ($clients.Count -eq 0) { throw "Aucun client confié à lawyer-a : relancez seed-keycloak-users.ps1." }
    $newCase = Invoke-Api POST '/api/cases' $lawyer @{ clientId = $clients[0].id; lawyerId = $lawyerClaims.sub; reference = "JUD-$([guid]::NewGuid().ToString().Substring(0, 6))"; title = 'Dossier de test judiciaire' }
    Assert-Status 'LAWYER crée un dossier de test (aucun dossier existant)' $newCase 201
    if ($newCase.Status -ne 201) { throw "Création du dossier impossible, arrêt." }
    $case = $newCase.Json
} else {
    $case = $cases[0]
}
$suffix = [guid]::NewGuid().ToString().Substring(0, 6)
$future = (Get-Date).AddDays(10).ToString("yyyy-MM-dd'T'10:00:00")
$today = (Get-Date).ToString('yyyy-MM-dd')

Write-Output "--- 1. Tribunaux et juges"
$court = Invoke-Api POST '/api/courts' $lawyer @{ name = "TEST-JUD Tribunal $suffix"; type = 'Tribunal de première instance'; city = 'Casablanca' }
Assert-Status 'LAWYER crée un tribunal' $court 201
if ($court.Status -ne 201) { throw "Création du tribunal impossible, arrêt." }
$otherCourt = Invoke-Api POST '/api/courts' $lawyer @{ name = "TEST-JUD Autre tribunal $suffix"; type = 'Tribunal de commerce'; city = 'Rabat' }
Assert-Status 'LAWYER crée un second tribunal' $otherCourt 201
$judge = Invoke-Api POST '/api/judges' $lawyer @{ courtId = $court.Json.id; firstName = 'Anne'; lastName = "Test$suffix"; role = 'Président' }
Assert-Status 'LAWYER ajoute un juge au premier tribunal' $judge 201
if ($judge.Status -ne 201) { throw "Création du juge impossible, arrêt." }
Assert-Status 'un tribunal sans nom est refusé' (Invoke-Api POST '/api/courts' $lawyer @{ name = ''; type = 'X'; city = 'Y' }) 400
Assert-That 'SECRETARY lit la liste des tribunaux' ((Invoke-Api GET '/api/courts' $secretary).Status -eq 200)
Assert-Status 'SECRETARY ne peut pas créer de tribunal' (Invoke-Api POST '/api/courts' $secretary @{ name = "TEST-JUD refus $suffix"; type = 'X'; city = 'Y' }) 403
Assert-Status 'CLIENT n''a pas accès aux tribunaux' (Invoke-Api GET '/api/courts' $client) 403
Assert-That 'le tribunal créé figure dans la liste avec son juge' (@((Invoke-Api GET '/api/courts' $lawyer).Json | ForEach-Object { $_ } | Where-Object { $_.id -eq $court.Json.id -and @($_.judges).Count -eq 1 }).Count -eq 1)

Write-Output "--- 2. Procédure judiciaire"
$body = @{ lawFirmId = $lawFirmId; caseId = $case.id; courtId = $court.Json.id; judgeId = $judge.Json.id; courtCaseNumber = "TEST-JUD-$suffix"; procedureType = 'CIVIL'; status = 'REGISTERED'; openingDate = $today }
$created = Invoke-Api POST '/api/judicial-cases' $lawyer $body
Assert-Status 'LAWYER enregistre une procédure' $created 201
if ($created.Status -ne 201) { throw "Création de la procédure impossible, arrêt." }
$id = $created.Json.id
Assert-That 'la procédure démarre à l''état REGISTERED' ($created.Json.status -eq 'REGISTERED')
Assert-Status 'un juge qui n''appartient pas au tribunal est refusé (400)' (Invoke-Api POST '/api/judicial-cases' $lawyer (Merge-Hash $body @{ courtId = $otherCourt.Json.id; courtCaseNumber = "TEST-JUD-B-$suffix" })) 400
Assert-Status 'CLIENT ne peut pas créer de procédure' (Invoke-Api POST '/api/judicial-cases' $client $body) 403
Assert-Status 'SECRETARY ne peut pas créer de procédure' (Invoke-Api POST '/api/judicial-cases' $secretary $body) 403

Write-Output "--- 3. Lecture et isolation"
Assert-That 'LAWYER voit sa procédure' (Contains-Id '/api/judicial-cases' $lawyer $id)
Assert-That 'SECRETARY voit la procédure (personnel de bureau)' (Contains-Id '/api/judicial-cases' $secretary $id)
Assert-That 'un autre avocat du cabinet ne la voit pas' (-not (Contains-Id '/api/judicial-cases' $lawyerSameFirm $id))
Assert-Status 'un autre avocat du cabinet : détail introuvable' (Invoke-Api GET "/api/judicial-cases/$id" $lawyerSameFirm) 404
Assert-That 'un avocat d''un autre cabinet ne la voit pas' (-not (Contains-Id '/api/judicial-cases' $lawyerOtherFirm $id))
Assert-Status 'un avocat d''un autre cabinet : détail introuvable' (Invoke-Api GET "/api/judicial-cases/$id" $lawyerOtherFirm) 404
Assert-That "CLIENT ne voit la procédure que si le dossier est à son nom (clientId du jeton : $clientClaimId)" ((Contains-Id '/api/judicial-cases' $client $id) -eq ($case.clientId -eq $clientClaimId))

Write-Output "--- 4. Audiences"
$hearingBody = @{ dateTime = $future; type = 'FIRST_HEARING'; location = 'Salle 1'; notes = 'Audience de test' }
$hearing = Invoke-Api POST "/api/judicial-cases/$id/hearings" $lawyer $hearingBody
Assert-Status 'LAWYER planifie une audience' $hearing 201
if ($hearing.Status -ne 201) { throw "Création de l'audience impossible, arrêt." }
$hearingId = $hearing.Json.id
Assert-That 'l''audience est planifiée (SCHEDULED)' ($hearing.Json.status -eq 'SCHEDULED')
Assert-That 'la procédure passe à HEARING_SCHEDULED' ((Get-CaseStatus $id $lawyer) -eq 'HEARING_SCHEDULED')
Assert-That 'l''audience figure dans la liste des audiences' (Contains-Id "/api/judicial-cases/$id/hearings" $lawyer $hearingId)
Assert-Status 'un autre avocat ne peut pas planifier d''audience (404)' (Invoke-Api POST "/api/judicial-cases/$id/hearings" $lawyerSameFirm $hearingBody) 404
Assert-Status 'SECRETARY ne peut pas planifier d''audience (403)' (Invoke-Api POST "/api/judicial-cases/$id/hearings" $secretary $hearingBody) 403
Assert-Status 'CLIENT ne peut pas planifier d''audience (403)' (Invoke-Api POST "/api/judicial-cases/$id/hearings" $client $hearingBody) 403
Assert-Status 'un autre avocat ne peut pas changer le statut de l''audience (404)' (Invoke-Api PATCH "/api/hearings/$hearingId/status?status=COMPLETED" $lawyerSameFirm) 404
Assert-Status 'un autre avocat ne peut pas changer le statut de la procédure (404)' (Invoke-Api PATCH "/api/judicial-cases/$id/status?status=IN_PROGRESS" $lawyerSameFirm) 404

Write-Output "--- 5. Statuts et cohérence"
Assert-Status 'un statut d''audience inconnu est refusé (400)' (Invoke-Api PATCH "/api/hearings/$hearingId/status?status=NIMPORTE" $lawyer) 400
Assert-Status 'LAWYER marque l''audience tenue' (Invoke-Api PATCH "/api/hearings/$hearingId/status?status=COMPLETED" $lawyer) 200
Assert-That 'la procédure passe en délibéré (DELIBERATION)' ((Get-CaseStatus $id $lawyer) -eq 'DELIBERATION')
Assert-Status 'une audience tenue ne peut plus changer de statut (409)' (Invoke-Api PATCH "/api/hearings/$hearingId/status?status=SCHEDULED" $lawyer) 409
Assert-Status 'transition de procédure interdite : DELIBERATION -> IN_PROGRESS (409)' (Invoke-Api PATCH "/api/judicial-cases/$id/status?status=IN_PROGRESS" $lawyer) 409
Assert-Status 'LAWYER rend le jugement' (Invoke-Api PATCH "/api/judicial-cases/$id/status?status=JUDGMENT_ISSUED" $lawyer) 200
$late = Invoke-Api POST "/api/judicial-cases/$id/hearings" $lawyer $hearingBody
Assert-Status 'une audience peut encore être planifiée après le jugement' $late 201
Assert-That 'planifier une audience ne fait PAS reculer une procédure jugée' ((Get-CaseStatus $id $lawyer) -eq 'JUDGMENT_ISSUED')
if ($late.Status -eq 201) {
    Assert-Status 'LAWYER marque cette audience tenue' (Invoke-Api PATCH "/api/hearings/$($late.Json.id)/status?status=COMPLETED" $lawyer) 200
    Assert-That 'terminer une audience ne rouvre PAS une procédure jugée' ((Get-CaseStatus $id $lawyer) -eq 'JUDGMENT_ISSUED')
}
Assert-Status 'LAWYER clôture la procédure' (Invoke-Api PATCH "/api/judicial-cases/$id/status?status=CLOSED" $lawyer) 200
Assert-Status 'on ne peut plus planifier d''audience sur une procédure close (409)' (Invoke-Api POST "/api/judicial-cases/$id/hearings" $lawyer $hearingBody) 409
Assert-Status 'une procédure close ne change plus de statut (409)' (Invoke-Api PATCH "/api/judicial-cases/$id/status?status=IN_PROGRESS" $lawyer) 409

Write-Output "--- 6. Historique"
$types = @((Invoke-Api GET "/api/judicial-cases/$id/events" $lawyer).Json | ForEach-Object { $_ } | ForEach-Object { $_.type })
Assert-That 'l''historique contient l''enregistrement, les audiences et les changements de statut' (($types -contains 'JUDICIAL_CASE_REGISTERED') -and ($types -contains 'HEARING_CREATED') -and ($types -contains 'JUDICIAL_STATUS_CHANGED') -and ($types -contains 'HEARING_STATUS_CHANGED'))
Assert-Status 'un autre avocat ne lit pas l''historique (404)' (Invoke-Api GET "/api/judicial-cases/$id/events" $lawyerSameFirm) 404

if ($script:failures -gt 0) { Write-Output "`n$($script:failures) échec(s)."; exit 1 }
Write-Output "`nTous les contrôles sont passés. (Les enregistrements TEST-JUD restent en base : aucune suppression n'existe côté serveur.)"
