# Runs one benchmark on Windows. All arguments are passed through.
#
#   .\scripts\run.ps1 --target redis --operation GET --threads 16
#
# Heap defaults match the RHEL VM profile; override with $env:JVM_OPTS.

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot

try {
    $jar = if ($env:JAR) { $env:JAR } else { Join-Path $projectRoot "target\cache-benchmark.jar" }
    if (-not (Test-Path $jar)) {
        Write-Error "$jar not found. Run .\scripts\build.ps1 first."
    }

    $jvmOpts = if ($env:JVM_OPTS) { $env:JVM_OPTS -split ' ' } else { @("-Xms2g", "-Xmx4g") }

    $config = if ($env:CONFIG) { $env:CONFIG } else { Join-Path $projectRoot "config\benchmark.yaml" }
    $configArgs = if (Test-Path $config) { @("--config", $config) } else { @() }

    & java @jvmOpts -jar $jar @configArgs @args
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
