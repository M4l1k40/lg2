$ErrorActionPreference = 'Stop'
$gatewayUrl = 'http://localhost:9000'
$tokenUrl = 'http://localhost:8080/realms/legalflow/protocol/openid-connect/token'
$firmA = '11111111-1111-1111-1111-111111111111'
$firmB = '44444444-4444-4444-4444-444444444444'

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

function Invoke-LiveApi {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [string]$Token,
        [object]$Body
    )

    $request = @{
        Method = $Method
        Uri = "$gatewayUrl$Path"
        Headers = @{ Authorization = "Bearer $Token" }
        UseBasicParsing = $true
        ErrorAction = 'Stop'
    }
    if ($PSBoundParameters.ContainsKey('Body')) {
        $request.ContentType = 'application/json'
        $request.Body = ConvertTo-Json -InputObject $Body -Depth 12 -Compress
    }

    try {
        $response = Invoke-WebRequest @request
        $result = [pscustomobject]@{
            StatusCode = [int]$response.StatusCode
            Content = [string]$response.Content
        }
    }
    catch {
        $response = $_.Exception.Response
        if ($null -eq $response) {
            $result = [pscustomobject]@{ StatusCode = 0; Content = $_.Exception.Message }
        }
        else {
            $reader = [System.IO.StreamReader]::new($response.GetResponseStream())
            $result = [pscustomobject]@{
                StatusCode = [int]$response.StatusCode
                Content = $reader.ReadToEnd()
            }
        }
    }

    Write-Host "HTTP $Name $($result.StatusCode)"
    Write-Host "BODY $Name $($result.Content)"
    return $result
}

$tokenA = Get-FreshToken 'lawyer-a' 'lawyer-a'
$tokenB = Get-FreshToken 'lawyer-b' 'lawyer-b'
Write-Host 'Fresh lawyer-a and lawyer-b tokens obtained; token contents suppressed.'

$clientResponse = Invoke-LiveApi 'setup-client-A' 'POST' '/api/clients' $tokenA @{
    firstName = 'Deadline'
    lastName = 'LiveTest'
    email = "deadline-$([guid]::NewGuid())@dev-only.test"
    phone = '555-0199'
    address = 'Firm A live verification'
}
if ($clientResponse.StatusCode -ne 201) { throw "Client setup failed: HTTP $($clientResponse.StatusCode) $($clientResponse.Content)" }
$client = $clientResponse.Content | ConvertFrom-Json

$caseResponse = Invoke-LiveApi 'setup-case-A' 'POST' '/api/cases' $tokenA @{
    clientId = $client.id
    lawyerId = [guid]::NewGuid().ToString()
    reference = "DL-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
    title = 'Deadline live verification'
    description = 'Temporary live API verification'
}
if ($caseResponse.StatusCode -ne 201) { throw "Case setup failed: HTTP $($caseResponse.StatusCode) $($caseResponse.Content)" }
$case = $caseResponse.Content | ConvertFrom-Json
Write-Host "SETUP caseA=$($case.id) firm=$($case.lawFirmId)"

$futureDate = [datetime]::Now.AddDays(6).ToString('yyyy-MM-ddTHH:mm:ss')
$deadlineResponse = Invoke-LiveApi 'create-future' 'POST' '/api/deadlines' $tokenA @{
    caseId = $case.id
    title = 'Live upcoming deadline'
    description = 'Deadline API verification'
    dueDate = $futureDate
    priority = 'HIGH'
    status = 'COMPLETED'
}
if ($deadlineResponse.StatusCode -ne 201) { throw "Deadline setup failed: HTTP $($deadlineResponse.StatusCode) $($deadlineResponse.Content)" }
$deadline = $deadlineResponse.Content | ConvertFrom-Json

Invoke-LiveApi 'create-past' 'POST' '/api/deadlines' $tokenA @{
    caseId = $case.id
    title = 'Past due rejected'
    dueDate = [datetime]::Now.AddDays(-1).ToString('yyyy-MM-ddTHH:mm:ss')
    priority = 'MEDIUM'
} | Out-Null
Invoke-LiveApi 'list-own' 'GET' "/api/deadlines?lawFirmId=$firmA" $tokenA $null | Out-Null
Invoke-LiveApi 'read-own' 'GET' "/api/deadlines/$($deadline.id)" $tokenA $null | Out-Null
Invoke-LiveApi 'approaching-7d' 'GET' "/api/deadlines/approaching?lawFirmId=$firmA&days=7" $tokenA $null | Out-Null
Invoke-LiveApi 'complete-status' 'PATCH' "/api/deadlines/$($deadline.id)/status?status=COMPLETED" $tokenA $null | Out-Null
Invoke-LiveApi 'invalid-status' 'PATCH' "/api/deadlines/$($deadline.id)/status?status=REGISTERED" $tokenA $null | Out-Null
Invoke-LiveApi 'foreign-read-B' 'GET' "/api/deadlines/$($deadline.id)" $tokenB $null | Out-Null
Invoke-LiveApi 'foreign-list-B' 'GET' "/api/deadlines?lawFirmId=$firmA" $tokenB $null | Out-Null
Invoke-LiveApi 'foreign-case-create-B' 'POST' '/api/deadlines' $tokenB @{
    caseId = $case.id
    title = 'Cross tenant reference rejection'
    dueDate = $futureDate
    priority = 'LOW'
} | Out-Null

Write-Host "IDS deadlineA=$($deadline.id) caseA=$($case.id)"
