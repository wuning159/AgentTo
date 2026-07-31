$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sharedEnvironment = Join-Path $root '..\backend\.env.local'
if (-not (Test-Path $sharedEnvironment)) {
    throw "Environment file not found: $sharedEnvironment"
}

Get-Content $sharedEnvironment | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
    }
}

$javaHome = [Environment]::GetEnvironmentVariable('AGENTTO_JAVA_HOME', 'Process')
if (-not $javaHome) {
    throw 'AGENTTO_JAVA_HOME is not configured'
}

$java = Join-Path $javaHome 'bin\java.exe'
$jar = Join-Path $root 'backend\target\agentto-rag-service-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path $jar)) {
    throw "Backend jar not found: $jar"
}

& $java -jar $jar
