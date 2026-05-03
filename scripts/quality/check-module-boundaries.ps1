param()

$ErrorActionPreference = "Stop"

$limpCheck = Join-Path -Path $PSScriptRoot -ChildPath "check-limp-policies.ps1"
& $limpCheck
exit $LASTEXITCODE
