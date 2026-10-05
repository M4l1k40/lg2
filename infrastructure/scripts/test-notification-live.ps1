$ErrorActionPreference = 'Stop'
$gatewayUrl = 'http://localhost:9000'
$notificationUrl = 'http://localhost:8087'
$tokenUrl = 'http://localhost:8080/realms/legalflow/protocol/openid-connect/token'
$firmA = '11111111-1111-1111-1111-111111111111'
$firmB = '44444444-4444-4444-4444-444444444444'
$script:failures = 0

function Get-FreshToken {
    param([string]$Username, [string]$Password)

    $response = Invoke-RestMethod -Method Post -Uri $tokenUrl `
        -ContentType 'application/x-www-form-urlencoded' -Body @{
            client_id = 'legalflow-frontend'
            grant_type = 'password'
            username = $Username
            password = $Password
        }
    return $response.access_token
}

function Invoke-ObservedRequest {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Uri,
        [string]$Token,
        [object]$Body
    )

    $request = @{ Method = $Method; Uri = $Uri; UseBasicParsing = $true; ErrorAction = 'Stop' }
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $request.Headers = @{ Authorization = "Bearer $Token" }
    }
    if ($null -ne $Body) {
        $request.ContentType = 'application/json'
        $request.Body = ConvertTo-Json -InputObject $Body -Depth 10 -Compress
    }

    try {
        $response = Invoke-WebRequest @request
        $content = $response.Content
        if ($content -is [byte[]]) {
            $content = [System.Text.Encoding]::UTF8.GetString($content)
        }
        $result = [pscustomobject]@{ StatusCode = [int]$response.StatusCode; Content = [string]$content }
    }
    catch {
        $response = $_.Exception.Response
        if ($null -eq $response) {
            $result = [pscustomobject]@{ StatusCode = 0; Content = $_.Exception.Message }
        }
        else {
            $reader = [System.IO.StreamReader]::new($response.GetResponseStream())
            $result = [pscustomobject]@{ StatusCode = [int]$response.StatusCode; Content = $reader.ReadToEnd() }
        }
    }
    Write-Host "HTTP $Name $($result.StatusCode)"
    Write-Host "BODY $Name $($result.Content)"
    return $result
}

function Assert-Status {
    param($Response, [int]$Expected, [string]$Name)
    if ($Response.StatusCode -eq $Expected) {
        Write-Host "PASS $Name"
    }
    else {
        Write-Host "FAIL $Name expected=$Expected actual=$($Response.StatusCode)"
        $script:failures++
    }
}

$tokenA = Get-FreshToken 'lawyer-a' 'lawyer-a'
$tokenB = Get-FreshToken 'lawyer-b' 'lawyer-b'
Write-Host 'Fresh lawyer-a and lawyer-b access tokens obtained; token values suppressed.'

$createA = Invoke-ObservedRequest 'create-A' 'POST' "$gatewayUrl/api/notifications" $tokenA @{
    type = 'DEADLINE_APPROACHING'
    title = 'Deadline soon'
    message = 'A deadline is approaching.'
    referenceType = 'DEADLINE'
    referenceId = 'cccccccc-cccc-cccc-cccc-cccccccccccc'
    lawFirmId = $firmB
    recipientId = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
}
Assert-Status $createA 201 'lawyer-a create'
$notificationA = $createA.Content | ConvertFrom-Json
Write-Host "ASSERT create-A tenant=$($notificationA.lawFirmId) recipient=$($notificationA.recipientId) read=$($notificationA.read)"

$listA = Invoke-ObservedRequest 'list-A' 'GET' "$gatewayUrl/api/notifications" $tokenA $null
Assert-Status $listA 200 'lawyer-a list'
Invoke-ObservedRequest 'unread-A' 'GET' "$gatewayUrl/api/notifications/unread" $tokenA $null | Out-Null
Invoke-ObservedRequest 'get-A' 'GET' "$gatewayUrl/api/notifications/$($notificationA.id)" $tokenA $null | Out-Null
$markRead = Invoke-ObservedRequest 'mark-read-A' 'PATCH' "$gatewayUrl/api/notifications/$($notificationA.id)/read" $tokenA $null
Assert-Status $markRead 200 'mark single read'

$createA2 = Invoke-ObservedRequest 'create-A2-for-read-all' 'POST' "$gatewayUrl/api/notifications" $tokenA @{
    type = 'DOCUMENT_ADDED'; title = 'Document added'; message = 'A document was added.'
}
Assert-Status $createA2 201 'create second lawyer-a notification'
$notificationA2 = $createA2.Content | ConvertFrom-Json
$readAll = Invoke-ObservedRequest 'read-all-A' 'PATCH' "$gatewayUrl/api/notifications/read-all" $tokenA $null
Assert-Status $readAll 200 'mark all read'

$createB = Invoke-ObservedRequest 'create-B' 'POST' "$gatewayUrl/api/notifications" $tokenB @{
    type = 'HEARING_CREATED'; title = 'Hearing created'; message = 'A hearing was created.'
}
Assert-Status $createB 201 'lawyer-b create'
$notificationB = $createB.Content | ConvertFrom-Json
$listB = Invoke-ObservedRequest 'list-B' 'GET' "$gatewayUrl/api/notifications" $tokenB $null
Assert-Status $listB 200 'lawyer-b list'
if (@(($listB.Content | ConvertFrom-Json) | Where-Object { $_.id -eq $notificationA.id }).Count -eq 0) {
    Write-Host 'PASS lawyer-b list excludes lawyer-a notification'
}
else {
    Write-Host 'FAIL lawyer-b list exposed lawyer-a notification'
    $script:failures++
}
$foreignGet = Invoke-ObservedRequest 'lawyer-b-get-A' 'GET' "$gatewayUrl/api/notifications/$($notificationA.id)" $tokenB $null
Assert-Status $foreignGet 404 'lawyer-b cannot read lawyer-a notification'
$foreignRead = Invoke-ObservedRequest 'lawyer-b-patch-A' 'PATCH' "$gatewayUrl/api/notifications/$($notificationA.id)/read" $tokenB $null
Assert-Status $foreignRead 404 'lawyer-b cannot mark lawyer-a notification read'

$invalid = Invoke-ObservedRequest 'invalid-create' 'POST' "$gatewayUrl/api/notifications" $tokenA @{
    type = 'NOT_A_NOTIFICATION_TYPE'; title = ''; message = ''
}
Assert-Status $invalid 400 'invalid request rejected'
$anonymous = Invoke-ObservedRequest 'anonymous-list' 'GET' "$gatewayUrl/api/notifications" '' $null
Assert-Status $anonymous 401 'unauthenticated request rejected'
$deleteA = Invoke-ObservedRequest 'delete-A' 'DELETE' "$gatewayUrl/api/notifications/$($notificationA.id)" $tokenA $null
Assert-Status $deleteA 204 'delete owned notification'
$deletedRead = Invoke-ObservedRequest 'read-deleted-A' 'GET' "$gatewayUrl/api/notifications/$($notificationA.id)" $tokenA $null
Assert-Status $deletedRead 404 'deleted notification no longer exists'

$health = Invoke-ObservedRequest 'notification-health' 'GET' "$notificationUrl/actuator/health" '' $null
Assert-Status $health 200 'notification actuator health'

Write-Host "IDS notificationA=$($notificationA.id) notificationA2=$($notificationA2.id) notificationB=$($notificationB.id)"
if ($script:failures -gt 0) {
    Write-Host "Notification live checks failed: $script:failures"
    exit 1
}
Write-Host 'All notification live checks passed.'
