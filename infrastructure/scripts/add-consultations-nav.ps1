# Ajoute le lien "Consultations" dans les menus Client, Secrétaire et Admin SANS écraser tes layouts :
# la ligne est insérée dans le tableau de navigation existant (idempotent, relançable).
param(
    [string]$LayoutsDir = (Join-Path $PSScriptRoot '..\..\frontend\src\layouts')
)
$ErrorActionPreference = 'Stop'

$targets = @(
    @{ File = 'ClientLayout.jsx';    After = "'/client/cases'";    Line = "  { to: '/client/consultations', labelKey: 'client.nav.consultations', label: 'Mes consultations' }," },
    @{ File = 'SecretaryLayout.jsx'; After = "'/secretary/cases'"; Line = "  { to: '/secretary/consultations', labelKey: 'secretary.nav.consultations', label: 'Consultations' }," },
    @{ File = 'AdminLayout.jsx';     After = "'/admin/cases'";     Line = "  { to: '/admin/consultations', labelKey: 'admin.nav.consultations', label: 'Consultations' }," }
)

$utf8NoBom = New-Object System.Text.UTF8Encoding $false

foreach ($target in $targets) {
    $path = Join-Path $LayoutsDir $target.File
    if (-not (Test-Path $path)) { Write-Output "ATTENTION  $($target.File) introuvable dans $LayoutsDir"; continue }

    $text = [IO.File]::ReadAllText($path)
    if ($text -match "consultations") { Write-Output "DEJA OK    $($target.File)"; continue }

    $pattern = "(?m)^(?<line>[^\r\n]*" + [regex]::Escape($target.After) + "[^\r\n]*)(?<nl>\r?\n)"
    $match = [regex]::Match($text, $pattern)
    if (-not $match.Success) {
        Write-Output "A FAIRE A LA MAIN  $($target.File) : ajoute cette ligne dans le tableau de navigation :"
        Write-Output "    $($target.Line.Trim())"
        continue
    }

    $newText = $text.Insert($match.Index + $match.Length, $target.Line + $match.Groups['nl'].Value)
    [IO.File]::WriteAllText($path, $newText, $utf8NoBom)
    Write-Output "AJOUTE     $($target.File)"
}
