param()

$ErrorActionPreference = "Stop"

$repoRootOutput = @(& git rev-parse --show-toplevel)
if ($LASTEXITCODE -ne 0) {
    Write-Host "Failed to resolve repository root." -ForegroundColor Red
    exit 1
}

$repoRoot = (($repoRootOutput | Select-Object -First 1) -as [string]).Trim()
$violations = New-Object System.Collections.Generic.List[string]

function Add-Violation {
    param([string]$Message)

    $violations.Add($Message)
}

function Get-RepoFileText {
    param([string]$RelativePath)

    $path = Join-Path -Path $repoRoot -ChildPath $RelativePath
    if (-not (Test-Path -LiteralPath $path)) {
        Add-Violation "Missing required file: $RelativePath"
        return $null
    }

    return Get-Content -LiteralPath $path -Raw
}

function Assert-Contains {
    param(
        [string]$RelativePath,
        [string]$Text,
        [string]$Pattern,
        [string]$Description
    )

    if ($null -eq $Text) {
        return
    }

    if ($Text -notmatch $Pattern) {
        Add-Violation "$RelativePath missing bucket rule: $Description"
    }
}

$bucketDocPath = "docs/BUCKET_CLASSIFICATION.md"
$bucketDoc = Get-RepoFileText -RelativePath $bucketDocPath

Assert-Contains -RelativePath $bucketDocPath -Text $bucketDoc -Pattern "\bHome\b" -Description "Home bucket definition"
Assert-Contains -RelativePath $bucketDocPath -Text $bucketDoc -Pattern "\bWork\b" -Description "Work bucket definition"
Assert-Contains -RelativePath $bucketDocPath -Text $bucketDoc -Pattern "\bDrive\b" -Description "Drive bucket definition"
Assert-Contains -RelativePath $bucketDocPath -Text $bucketDoc -Pattern "\bSite\b" -Description "Site bucket definition"
Assert-Contains -RelativePath $bucketDocPath -Text $bucketDoc -Pattern "\bIdle\b" -Description "Idle bucket definition"
Assert-Contains -RelativePath $bucketDocPath -Text $bucketDoc -Pattern "Needs review" -Description "legacy review compatibility"
Assert-Contains -RelativePath $bucketDocPath -Text $bucketDoc -Pattern "\bMiles\b" -Description "Miles definition"
Assert-Contains -RelativePath $bucketDocPath -Text $bucketDoc -Pattern "Home wins over Drive, Site, and Idle" -Description "Home priority"
Assert-Contains -RelativePath $bucketDocPath -Text $bucketDoc -Pattern "Work is total trackable time away from Home" -Description "Work as away-from-home time"
Assert-Contains -RelativePath $bucketDocPath -Text $bucketDoc -Pattern "Work = Drive \+ Site \+ Idle" -Description "complete Work partition"
Assert-Contains -RelativePath $bucketDocPath -Text $bucketDoc -Pattern "point-to-point speed" -Description "speed evidence rule"
Assert-Contains -RelativePath $bucketDocPath -Text $bucketDoc -Pattern "15 mph sustained for 1 minute" -Description "initial drive speed threshold"
Assert-Contains -RelativePath $bucketDocPath -Text $bucketDoc -Pattern "route geometry" -Description "no route geometry"
Assert-Contains -RelativePath $bucketDocPath -Text $bucketDoc -Pattern "speed history" -Description "no speed history"
Assert-Contains -RelativePath $bucketDocPath -Text $bucketDoc -Pattern "continuous route log" -Description "no continuous route log"
Assert-Contains -RelativePath $bucketDocPath -Text $bucketDoc -Pattern "Never rewrite historical activity records" -Description "historical records preserved"

$classifierPath = "core/common/src/main/kotlin/com/robertochavez/timetracker/core/common/domain/ActivityBucketPolicy.kt"
$classifier = Get-RepoFileText -RelativePath $classifierPath
if ($classifier -match '->\s*ActivityBucket\.NEEDS_REVIEW') {
    Add-Violation "Live classification must not generate the legacy Review bucket."
}
$reportPath = "core/common/src/main/kotlin/com/robertochavez/timetracker/core/common/domain/ReportCalculator.kt"
$report = Get-RepoFileText -RelativePath $reportPath
Assert-Contains -RelativePath $reportPath -Text $report -Pattern 'workRanges.subtract\(siteWorkRanges\).subtract\(driveRanges\)' -Description "remaining Work ranges belong to Idle"

$requiredLinks = @{
    "plan/TODO.md" = "docs/BUCKET_CLASSIFICATION.md"
    "docs/PRIVACY.md" = "BUCKET_CLASSIFICATION.md"
    "docs/MANUAL_DEVICE_TESTS.md" = "BUCKET_CLASSIFICATION.md"
    "docs/TESTING_NERVOUS_SYSTEM.md" = "BUCKET_CLASSIFICATION.md"
}

foreach ($entry in $requiredLinks.GetEnumerator()) {
    $text = Get-RepoFileText -RelativePath $entry.Key
    Assert-Contains -RelativePath $entry.Key -Text $text -Pattern ([regex]::Escape($entry.Value)) -Description "link to bucket classification source of truth"
}

$productionRoots = @("app", "core", "feature") |
    ForEach-Object { Join-Path -Path $repoRoot -ChildPath $_ } |
    Where-Object { Test-Path -LiteralPath $_ }

$forbiddenProductionPatterns = @(
    @{
        Pattern = "auto[-\s]?fix"
        Message = "production code must not introduce broad bucket auto-fix behavior"
    },
    @{
        Pattern = "silently\s+(place|classif|repair|rewrite|move|assign)"
        Message = "production code must not silently rewrite or assign buckets"
    },
    @{
        Pattern = "guess(ed|es|ing)?\s+(data|bucket|buckets|time|classification)"
        Message = "production code must not guess bucket placement"
    },
    @{
        Pattern = "(repair|rewrite|backfill)\w*\s+.*bucket"
        Message = "production bucket repair must be explicit and user-reviewed"
    },
    @{
        Pattern = "bucket.*(repair|rewrite|backfill)"
        Message = "production bucket repair must be explicit and user-reviewed"
    }
)

foreach ($root in $productionRoots) {
    $files = Get-ChildItem -LiteralPath $root -Recurse -File -Include "*.kt", "*.kts", "*.java", "*.xml" |
        Where-Object {
            $_.FullName -notmatch "[\\/](build|generated)[\\/]" -and
            $_.FullName -notmatch "[\\/]src[\\/](test|androidTest)[\\/]"
        }

    foreach ($file in $files) {
        $relativePath = [System.IO.Path]::GetRelativePath($repoRoot, $file.FullName).Replace("\", "/")
        $lines = @(Get-Content -LiteralPath $file.FullName)
        for ($index = 0; $index -lt $lines.Count; $index++) {
            foreach ($rule in $forbiddenProductionPatterns) {
                if ($lines[$index] -match $rule.Pattern) {
                    Add-Violation "${relativePath}:$($index + 1): $($rule.Message)"
                }
            }
        }
    }
}

$persistenceRoot = Join-Path -Path $repoRoot -ChildPath "core/database/src/main"
if (Test-Path -LiteralPath $persistenceRoot) {
    $persistenceFiles = Get-ChildItem -LiteralPath $persistenceRoot -Recurse -File -Include "*.kt", "*.kts", "*.java", "*.xml"
    foreach ($file in $persistenceFiles) {
        $relativePath = [System.IO.Path]::GetRelativePath($repoRoot, $file.FullName).Replace("\", "/")
        $lines = @(Get-Content -LiteralPath $file.FullName)
        for ($index = 0; $index -lt $lines.Count; $index++) {
            $line = $lines[$index]
            if ($line -match "DROP TABLE IF EXISTS ``drive_movement_observations``") {
                continue
            }
            if ($line -match "speedMilesPerHour|drive_movement_observations|routeGeometry|polyline") {
                Add-Violation "${relativePath}:$($index + 1): persistence must not store speed history or route geometry."
            }
        }
    }
}

if ($violations.Count -gt 0) {
    Write-Host "Bucket classification policy violations found:" -ForegroundColor Red
    $violations | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}

Write-Host "PASSED: bucket classification policies" -ForegroundColor Green
