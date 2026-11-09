$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$logDirectory = Join-Path $repositoryRoot 'logs\ci-local'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

$maven = Get-Command mvn -ErrorAction SilentlyContinue
$npm = Get-Command npm -ErrorAction SilentlyContinue
if (-not $maven) {
    throw 'Maven (mvn) was not found on PATH.'
}
if (-not $npm) {
    throw 'npm was not found on PATH.'
}

$pomFiles = @(Get-ChildItem -Path $repositoryRoot -Filter pom.xml -File -Recurse |
    Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' })
$eventsDirectory = Join-Path $repositoryRoot 'infrastructure\legalflow-events'
$eventsPom = Join-Path $eventsDirectory 'pom.xml'
if (-not (Test-Path -LiteralPath $eventsPom -PathType Leaf)) {
    throw "Shared Maven library POM not found: $eventsPom"
}
if (-not ($pomFiles | Where-Object { $_.FullName -eq $eventsPom })) {
    throw "Shared Maven library was not discovered: $eventsPom"
}

$moduleDirectories = @($pomFiles | ForEach-Object { Split-Path -Parent $_.FullName } |
    Sort-Object -Unique)
$script:Results = @()

function Get-MavenTestCount {
    param([string]$ModuleDirectory)

    $reportDirectory = Join-Path $ModuleDirectory 'target\surefire-reports'
    $reports = @(Get-ChildItem -Path $reportDirectory -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
    if ($reports.Count -eq 0) {
        return 'N/A'
    }

    $testCount = 0
    foreach ($report in $reports) {
        [xml]$document = Get-Content -LiteralPath $report.FullName -Raw
        $testCount += $document.SelectNodes('//testcase').Count
    }
    return [string]$testCount
}

function Invoke-LoggedCommand {
    param(
        [string]$Module,
        [string]$WorkingDirectory,
        [string]$ExecutablePath,
        [string[]]$Arguments,
        [string]$LogName,
        [string]$TestCount = 'N/A',
        [switch]$CountMavenTests,
        [switch]$CountNodeTests
    )

    $logPath = Join-Path $logDirectory $LogName
    Write-Host "Running $Module; log: $logPath"
    Push-Location $WorkingDirectory
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $ExecutablePath @Arguments *> $logPath
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
        Pop-Location
    }

    if ($CountMavenTests) {
        $TestCount = Get-MavenTestCount -ModuleDirectory $WorkingDirectory
    }
    elseif ($CountNodeTests -and (Test-Path -LiteralPath $logPath -PathType Leaf)) {
        $testSummary = Select-String -LiteralPath $logPath -Pattern '^# tests (\d+)$' |
            Select-Object -Last 1
        if ($testSummary) {
            $TestCount = $testSummary.Matches[0].Groups[1].Value
        }
    }

    $script:Results += [PSCustomObject]@{
        Module   = $Module
        ExitCode = $exitCode
        Tests    = $TestCount
    }
}

Invoke-LoggedCommand `
    -Module 'infrastructure/legalflow-events install' `
    -WorkingDirectory $eventsDirectory `
    -ExecutablePath $maven.Source `
    -Arguments @('-B', 'install') `
    -LogName 'legalflow-events-install.log'

foreach ($moduleDirectory in $moduleDirectories) {
    $relativeDirectory = $moduleDirectory.Substring($repositoryRoot.Length).TrimStart('\', '/')
    $moduleName = $relativeDirectory.Replace('\', '/')
    $logName = ($moduleName -replace '[^A-Za-z0-9._-]', '-') + '-verify.log'
    Invoke-LoggedCommand `
        -Module $moduleName `
        -WorkingDirectory $moduleDirectory `
        -ExecutablePath $maven.Source `
        -Arguments @('-B', 'verify') `
        -LogName $logName `
        -CountMavenTests
}

$frontendDirectory = Join-Path $repositoryRoot 'frontend'
if (-not (Test-Path -LiteralPath (Join-Path $frontendDirectory 'package.json') -PathType Leaf)) {
    throw "Frontend package.json not found: $frontendDirectory"
}

Invoke-LoggedCommand -Module 'frontend: npm ci' `
    -WorkingDirectory $frontendDirectory -ExecutablePath $npm.Source `
    -Arguments @('ci') -LogName 'frontend-npm-ci.log'
Invoke-LoggedCommand -Module 'frontend: npm run lint' `
    -WorkingDirectory $frontendDirectory -ExecutablePath $npm.Source `
    -Arguments @('run', 'lint') -LogName 'frontend-npm-lint.log'
Invoke-LoggedCommand -Module 'frontend: npm test' `
    -WorkingDirectory $frontendDirectory -ExecutablePath $npm.Source `
    -Arguments @('test') -LogName 'frontend-npm-test.log' -CountNodeTests
Invoke-LoggedCommand -Module 'frontend: npm run build' `
    -WorkingDirectory $frontendDirectory -ExecutablePath $npm.Source `
    -Arguments @('run', 'build') -LogName 'frontend-npm-build.log'

Write-Host ''
Write-Host 'Local CI results:'
$script:Results | Format-Table -Property Module, ExitCode, Tests -AutoSize
Write-Host "Full command output is in $logDirectory"

if (@($script:Results | Where-Object { $_.ExitCode -ne 0 }).Count -gt 0) {
    exit 1
}
exit 0
