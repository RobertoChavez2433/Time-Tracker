param()

$ErrorActionPreference = "Stop"

$repoRootOutput = @(& git rev-parse --show-toplevel)
if ($LASTEXITCODE -ne 0) {
    Write-Host "Failed to resolve repository root." -ForegroundColor Red
    exit 1
}

$repoRoot = (($repoRootOutput | Select-Object -First 1) -as [string]).Trim()
$featureRoot = Join-Path -Path $repoRoot -ChildPath "feature"

if (-not (Test-Path -LiteralPath $featureRoot)) {
    exit 0
}

$violations = New-Object System.Collections.Generic.List[string]

$forbiddenProjectPattern = 'project\(":core:(database|datastore)"\)'
$forbiddenImportPattern = 'import\s+com\.robertochavez\.timetracker\.core\.(database|datastore)\b'

Get-ChildItem -LiteralPath $featureRoot -Recurse -Filter "build.gradle.kts" |
    Select-String -Pattern $forbiddenProjectPattern |
    ForEach-Object {
        $relativePath = Resolve-Path -LiteralPath $_.Path -Relative
        $violations.Add("${relativePath}:$($_.LineNumber): feature modules must not depend on persistence implementation modules.")
    }

Get-ChildItem -LiteralPath $featureRoot -Recurse -Include "*.kt", "*.kts" -File |
    Where-Object { $_.FullName -notmatch '[\\/](build|generated)[\\/]' } |
    Select-String -Pattern $forbiddenImportPattern |
    ForEach-Object {
        $relativePath = Resolve-Path -LiteralPath $_.Path -Relative
        $violations.Add("${relativePath}:$($_.LineNumber): feature code must depend on core repository contracts, not persistence implementations.")
    }

if ($violations.Count -gt 0) {
    Write-Host "Module boundary violations found:" -ForegroundColor Red
    $violations | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}

Write-Host "PASSED: module boundaries" -ForegroundColor Green
