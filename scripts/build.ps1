# Builds the executable fat JAR on Windows.
#
#   .\scripts\build.ps1
#   .\scripts\build.ps1 -SkipTests
#
# The RHEL 9 deployment target uses scripts/build.sh; this is a local
# development convenience only.

param([switch]$SkipTests)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot

try {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        Write-Error "maven not found on PATH. Install it with: winget install Apache.Maven"
    }

    $mavenArgs = @("clean", "package")
    if ($SkipTests) { $mavenArgs += "-DskipTests" }

    & mvn @mavenArgs
    if ($LASTEXITCODE -ne 0) { Write-Error "maven build failed" }

    $jar = Join-Path $projectRoot "target\cache-benchmark.jar"
    if (-not (Test-Path $jar)) { Write-Error "build finished but $jar is missing" }

    Write-Host ""
    Write-Host "Built: $jar"
    & java -jar $jar --version
}
finally {
    Pop-Location
}
