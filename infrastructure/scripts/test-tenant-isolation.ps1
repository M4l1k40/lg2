$ErrorActionPreference = 'Stop'
$gatewayUrl = 'http://localhost:9000'
$tokenUrl = 'http://localhost:8080/realms/legalflow/protocol/openid-connect/token'
$firmA = '11111111-1111-1111-1111-111111111111'
$firmB = '44444444-4444-4444-4444-444444444444'
$script:failures = 0

function Get-Token {
    param([string]$Username, [string]$Password)
    $form = @{
        client_id = 'legalflow-frontend'
        grant_type = 'password'
        username = $Username
        password = $Password
    }
    $response = Invoke-RestMethod -Method Post -Uri $tokenUrl `
        -ContentType 'application/x-www-form-urlencoded' -Body $form
    return $response.access_token
}

function Decode-TokenPayload {
    param([string]$Token)

    $payload = $Token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    switch ($payload.Length % 4) {
        2 { $payload += '==' }
        3 { $payload += '=' }
    }
    $json = [System.Text.Encoding]::UTF8.GetString(
        [System.Convert]::FromBase64String($payload))
    return $json | ConvertFrom-Json
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers,
        [object]$Body
    )

    $request = @{
        Method = $Method
        Uri = "$gatewayUrl$Path"
        Headers = $Headers
        UseBasicParsing = $true
        ErrorAction = 'Stop'
    }
    if ($PSBoundParameters.ContainsKey('Body')) {
        $request.ContentType = 'application/json'
        $request.Body = ConvertTo-Json -InputObject $Body -Depth 12 -Compress
    }

    try {
        $response = Invoke-WebRequest @request
        return [pscustomobject]@{
            StatusCode = [int]$response.StatusCode
            Content = [string]$response.Content
        }
    }
    catch {
        $response = $_.Exception.Response
        if ($null -eq $response) {
            return [pscustomobject]@{ StatusCode = 0; Content = $_.Exception.Message }
        }
        $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
        return [pscustomobject]@{
            StatusCode = [int]$response.StatusCode
            Content = $reader.ReadToEnd()
        }
    }
}

function Assert-Check {
    param([bool]$Passed, [string]$Message)

    if ($Passed) {
        Write-Host "PASS: $Message"
    }
    else {
        Write-Host "FAIL: $Message"
        $script:failures++
    }
}

function Assert-Status {
    param($Response, [int]$Expected, [string]$Message)
    Assert-Check ($Response.StatusCode -eq $Expected) `
        "$Message (expected HTTP $Expected, got $($Response.StatusCode))"
}

function Get-ResponseItems {
    param($Response)
    if ($Response.StatusCode -ne 200 -or [string]::IsNullOrWhiteSpace($Response.Content)) {
        return @()
    }
    $parsed = ConvertFrom-Json -InputObject $Response.Content
    if ($null -eq $parsed) { return @() }
    return @($parsed)
}

try {
    $adminToken = Get-Token -Username 'admin' -Password 'admin'
    $lawyerAToken = Get-Token -Username 'lawyer-a' -Password 'lawyer-a'
    $lawyerBToken = Get-Token -Username 'lawyer-b' -Password 'lawyer-b'
    $adminClaims = Decode-TokenPayload $adminToken
    $lawyerAClaims = Decode-TokenPayload $lawyerAToken
    $lawyerBClaims = Decode-TokenPayload $lawyerBToken

    Write-Host "CLAIM admin.lawFirmId=$($adminClaims.lawFirmId)"
    Write-Host "CLAIM lawyer-a.lawFirmId=$($lawyerAClaims.lawFirmId)"
    Write-Host "CLAIM lawyer-b.lawFirmId=$($lawyerBClaims.lawFirmId)"
    Assert-Check ($adminClaims.lawFirmId -eq $firmA) 'admin token contains firm A lawFirmId'
    Assert-Check ($lawyerAClaims.lawFirmId -eq $firmA) 'lawyer-a token contains firm A lawFirmId'
    Assert-Check ($lawyerBClaims.lawFirmId -eq $firmB) 'lawyer-b token contains firm B lawFirmId'

    $headersA = @{ Authorization = "Bearer $lawyerAToken" }
    $headersB = @{ Authorization = "Bearer $lawyerBToken" }

    $clientResponse = Invoke-Api Post '/api/clients' $headersA @{
        firstName = 'Alpha'; lastName = 'Client'; email = "alpha-$([guid]::NewGuid())@dev-only.test"
        phone = '555-0100'; address = 'Firm A'
    }
    Assert-Status $clientResponse 201 'lawyer-a creates a client without body lawFirmId'
    if ($clientResponse.StatusCode -ne 201) { throw "Client setup failed: $($clientResponse.Content)" }
    $clientA = ConvertFrom-Json -InputObject $clientResponse.Content
    Assert-Check ($clientA.lawFirmId -eq $firmA) 'client lawFirmId is assigned from lawyer-a token'
    Assert-Status (Invoke-Api Get "/api/clients/$($clientA.id)" $headersA) 200 'lawyer-a reads own client'

    $clientListB = Invoke-Api Get '/api/clients' $headersB
    Assert-Status $clientListB 200 'lawyer-b lists clients'
    $clientItemsB = Get-ResponseItems $clientListB
    Assert-Check (@($clientItemsB | Where-Object { $_.id -eq $clientA.id }).Count -eq 0) 'lawyer-b client list excludes firm A client'
    Assert-Status (Invoke-Api Get "/api/clients/$($clientA.id)" $headersB) 404 'lawyer-b cannot read firm A client by id'
    Assert-Status (Invoke-Api Get "/api/clients?lawFirmId=$firmA" $headersB) 403 'lawyer-b cannot query clients for firm A'
    $clientMismatch = Invoke-Api Post '/api/clients' $headersB @{
        lawFirmId = $firmA; firstName = 'Forged'; lastName = 'Client'
        email = "forged-client-$([guid]::NewGuid())@dev-only.test"
    }
    Assert-Status $clientMismatch 403 'lawyer-b cannot create a client for firm A'

    $caseResponse = Invoke-Api Post '/api/cases' $headersA @{
        clientId = $clientA.id; lawyerId = [guid]::NewGuid().ToString()
        reference = "CASE-A-$([guid]::NewGuid().ToString('N').Substring(0,8))"
        title = 'Firm A case'; description = 'Tenant isolation test'
    }
    Assert-Status $caseResponse 201 'lawyer-a creates a case without body lawFirmId'
    if ($caseResponse.StatusCode -ne 201) { throw "Case setup failed: $($caseResponse.Content)" }
    $caseA = ConvertFrom-Json -InputObject $caseResponse.Content
    Assert-Check ($caseA.lawFirmId -eq $firmA) 'case lawFirmId is assigned from lawyer-a token'
    Assert-Status (Invoke-Api Get "/api/cases/$($caseA.id)" $headersA) 200 'lawyer-a reads own case'
    $caseListB = Invoke-Api Get '/api/cases' $headersB
    Assert-Status $caseListB 200 'lawyer-b lists cases'
    $caseItemsB = Get-ResponseItems $caseListB
    Assert-Check (@($caseItemsB | Where-Object { $_.id -eq $caseA.id }).Count -eq 0) 'lawyer-b case list excludes firm A case'
    Assert-Status (Invoke-Api Get "/api/cases/$($caseA.id)" $headersB) 404 'lawyer-b cannot read firm A case by id'
    Assert-Status (Invoke-Api Get "/api/cases/$($caseA.id)/events" $headersB) 404 'lawyer-b cannot read firm A case events'
    Assert-Status (Invoke-Api Get "/api/cases?lawFirmId=$firmA" $headersB) 403 'lawyer-b cannot query cases for firm A'
    $caseMismatch = Invoke-Api Post '/api/cases' $headersB @{
        lawFirmId = $firmA; clientId = $clientA.id; lawyerId = [guid]::NewGuid().ToString()
        reference = 'FORGED-CASE'; title = 'Forged case'
    }
    Assert-Status $caseMismatch 403 'lawyer-b cannot create a case for firm A'

    $foreignClientCase = Invoke-Api Post '/api/cases' $headersB @{
        clientId = $clientA.id; lawyerId = [guid]::NewGuid().ToString()
        reference = 'CROSS-TENANT-CLIENT'; title = 'Foreign client should be rejected'
    }
    Assert-Status $foreignClientCase 422 'lawyer-b cannot attach lawyer-a client to a case'

    $clientBResponse = Invoke-Api Post '/api/clients' $headersB @{
        firstName = 'Beta'; lastName = 'Client'; email = "beta-$([guid]::NewGuid())@dev-only.test"
        phone = '555-0200'; address = 'Firm B'
    }
    Assert-Status $clientBResponse 201 'lawyer-b creates own client'
    if ($clientBResponse.StatusCode -ne 201) { throw "Firm B client setup failed: $($clientBResponse.Content)" }
    $clientB = ConvertFrom-Json -InputObject $clientBResponse.Content
    $caseBResponse = Invoke-Api Post '/api/cases' $headersB @{
        clientId = $clientB.id; lawyerId = [guid]::NewGuid().ToString()
        reference = "CASE-B-$([guid]::NewGuid().ToString('N').Substring(0,8))"
        title = 'Firm B case'; description = 'Tenant isolation test'
    }
    Assert-Status $caseBResponse 201 'lawyer-b creates a case with own client'
    if ($caseBResponse.StatusCode -ne 201) { throw "Firm B case setup failed: $($caseBResponse.Content)" }
    $caseB = ConvertFrom-Json -InputObject $caseBResponse.Content

    $invoiceResponse = Invoke-Api Post '/api/invoices' $headersA @{
        lawFirmId = $firmA; caseId = $caseA.id; clientId = $clientA.id
        invoiceNumber = "INV-$([guid]::NewGuid().ToString('N').Substring(0,8))"
        description = 'Tenant isolation runtime check'; amount = 125.50
        dueDate = (Get-Date).AddDays(14).ToString('yyyy-MM-dd')
    }
    Assert-Status $invoiceResponse 201 'lawyer-a creates an invoice through the gateway'
    if ($invoiceResponse.StatusCode -ne 201) { throw "Invoice setup failed: $($invoiceResponse.Content)" }
    $invoice = ConvertFrom-Json -InputObject $invoiceResponse.Content
    Assert-Status (Invoke-Api Get "/api/invoices/$($invoice.id)" $headersA) 200 'lawyer-a reads own invoice through the gateway'
    Assert-Status (Invoke-Api Get "/api/invoices/$($invoice.id)" $headersB) 404 'lawyer-b cannot read firm A invoice'

    $courtResponse = Invoke-Api Post '/api/courts' $headersA @{
        name = "Tenant Isolation Court $([guid]::NewGuid().ToString('N').Substring(0,8))"
        type = 'CIVIL'; city = 'Test City'; address = 'Test address'
    }
    Assert-Status $courtResponse 201 'lawyer-a creates court test fixture'
    if ($courtResponse.StatusCode -ne 201) { throw "Court setup failed: $($courtResponse.Content)" }
    $court = ConvertFrom-Json -InputObject $courtResponse.Content
    $judgeResponse = Invoke-Api Post '/api/judges' $headersA @{
        courtId = $court.id; firstName = 'Test'; lastName = 'Judge'; role = 'JUDGE'
    }
    Assert-Status $judgeResponse 201 'lawyer-a creates judge test fixture'
    if ($judgeResponse.StatusCode -ne 201) { throw "Judge setup failed: $($judgeResponse.Content)" }
    $judge = ConvertFrom-Json -InputObject $judgeResponse.Content

    $judicialResponse = Invoke-Api Post '/api/judicial-cases' $headersA @{
        caseId = $caseA.id; courtId = $court.id; judgeId = $judge.id
        courtCaseNumber = "A-$([guid]::NewGuid().ToString('N').Substring(0,8))"
        procedureType = 'CIVIL'; status = 'REGISTERED'; openingDate = (Get-Date -Format 'yyyy-MM-dd')
    }
    Assert-Status $judicialResponse 201 'lawyer-a creates a judicial case without body lawFirmId'
    if ($judicialResponse.StatusCode -ne 201) { throw "Judicial-case setup failed: $($judicialResponse.Content)" }
    $judicialA = ConvertFrom-Json -InputObject $judicialResponse.Content
    Assert-Check ($judicialA.lawFirmId -eq $firmA) 'judicial-case lawFirmId is assigned from lawyer-a token'
    Assert-Status (Invoke-Api Get "/api/judicial-cases/$($judicialA.id)" $headersA) 200 'lawyer-a reads own judicial case'
    $judicialListB = Invoke-Api Get '/api/judicial-cases' $headersB
    Assert-Status $judicialListB 200 'lawyer-b lists judicial cases'
    $judicialItemsB = Get-ResponseItems $judicialListB
    Assert-Check (@($judicialItemsB | Where-Object { $_.id -eq $judicialA.id }).Count -eq 0) 'lawyer-b judicial list excludes firm A case'
    Assert-Status (Invoke-Api Get "/api/judicial-cases/$($judicialA.id)" $headersB) 404 'lawyer-b cannot read firm A judicial case by id'
    Assert-Status (Invoke-Api Get "/api/judicial-cases/$($judicialA.id)/events" $headersB) 404 'lawyer-b cannot read firm A judicial events'
    Assert-Status (Invoke-Api Get "/api/judicial-cases/$($judicialA.id)/hearings" $headersB) 404 'lawyer-b cannot read firm A hearings'
    Assert-Status (Invoke-Api Patch "/api/judicial-cases/$($judicialA.id)/status?status=CLOSED" $headersB) 404 'lawyer-b cannot patch firm A judicial case'
    Assert-Status (Invoke-Api Get "/api/judicial-cases?lawFirmId=$firmA" $headersB) 403 'lawyer-b cannot query judicial cases for firm A'
    $judicialMismatch = Invoke-Api Post '/api/judicial-cases' $headersB @{
        lawFirmId = $firmA; caseId = $caseA.id; courtId = $court.id
        judgeId = $judge.id; courtCaseNumber = 'FORGED-JUDICIAL'
        procedureType = 'CIVIL'; openingDate = (Get-Date -Format 'yyyy-MM-dd')
    }
    Assert-Status $judicialMismatch 403 'lawyer-b cannot create a judicial case for firm A'

    $foreignJudicialCase = Invoke-Api Post '/api/judicial-cases' $headersA @{
        caseId = $caseB.id; courtId = $court.id; judgeId = $judge.id
        courtCaseNumber = 'CROSS-TENANT-CASE'; procedureType = 'CIVIL'
        openingDate = (Get-Date -Format 'yyyy-MM-dd')
    }
    Assert-Status $foreignJudicialCase 422 'lawyer-a cannot create a judicial case for lawyer-b case'

    $hearingResponse = Invoke-Api Post "/api/judicial-cases/$($judicialA.id)/hearings" $headersA @{
        dateTime = (Get-Date).AddDays(7).ToString('yyyy-MM-ddTHH:mm:ss')
        type = 'FIRST_HEARING'; status = 'SCHEDULED'; location = 'Dev test court'
    }
    Assert-Status $hearingResponse 201 'lawyer-a creates a hearing on own judicial case'
    if ($hearingResponse.StatusCode -ne 201) { throw "Hearing setup failed: $($hearingResponse.Content)" }
    $hearingA = ConvertFrom-Json -InputObject $hearingResponse.Content
    Assert-Status (Invoke-Api Patch "/api/hearings/$($hearingA.id)/status?status=POSTPONED" $headersB) 404 'lawyer-b cannot patch firm A hearing'
    Assert-Status (Invoke-Api Get "/api/judicial-cases/$($judicialA.id)/events" $headersA) 200 'lawyer-a reads own judicial events'
    Assert-Status (Invoke-Api Get "/api/judicial-cases/$($judicialA.id)/hearings" $headersA) 200 'lawyer-a reads own hearings'

    if ($script:failures -gt 0) {
        Write-Host "Tenant isolation checks failed: $script:failures"
        exit 1
    }
    Write-Host 'All tenant-isolation checks passed.'
    exit 0
}
catch {
    Write-Host "FAIL: tenant isolation script encountered an error: $($_.Exception.Message)"
    exit 1
}
