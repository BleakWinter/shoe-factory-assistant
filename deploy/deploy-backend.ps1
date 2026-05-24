param(
    [string]$HostName = "192.168.10.62",
    [string]$User = "root",
    [string]$KeyPath = "$env:USERPROFILE\.ssh\id_rsa",
    [string]$RemoteAppDir = "/data/backend/shoe-factory-assistant",
    [string]$ContainerName = "shoe-factory-backend",
    [string]$Image = "eclipse-temurin:17-jre-jammy"
)

$ErrorActionPreference = "Stop"

$deployDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $deployDir
$backendDir = Join-Path $repoRoot "backend"
$jarPath = Join-Path $backendDir "target\shoe-factory-backend-0.1.0-SNAPSHOT.jar"
$remote = "${User}@${HostName}"
$ssh = Join-Path $env:WINDIR "System32\OpenSSH\ssh.exe"
$scp = Join-Path $env:WINDIR "System32\OpenSSH\scp.exe"

function Invoke-Native {
    param(
        [string]$FilePath,
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
    }
}

Write-Host "Building backend jar..."
Push-Location $backendDir
try {
    mvn.cmd -q -Pprod -DskipTests package
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "Jar not found: $jarPath"
}

Write-Host "Uploading backend to $remote..."
Invoke-Native $ssh @("-i", $KeyPath, $remote, "mkdir -p '$RemoteAppDir' '$RemoteAppDir/logs' /data/shoe-factory-assistant/files")
Invoke-Native $scp @("-i", $KeyPath, $jarPath, "${remote}:${RemoteAppDir}/app.jar")

Write-Host "Starting backend container..."
$remoteCommand = @"
docker pull '$Image' &&
if docker ps -a --format '{{.Names}}' | grep -qx '$ContainerName'; then
  docker restart '$ContainerName'
else
  docker run -d \
    --name '$ContainerName' \
    --restart unless-stopped \
    --network host \
    -e TZ=Asia/Shanghai \
    -e APP_FILE_STORAGE_ROOT_PATH=/data/shoe-factory-assistant/files \
    -v '$RemoteAppDir/app.jar:/app/app.jar:ro' \
    -v '/data/shoe-factory-assistant/files:/data/shoe-factory-assistant/files' \
    -v '$RemoteAppDir/logs:/app/logs' \
    '$Image' \
    sh -lc 'java -jar /app/app.jar --spring.profiles.active=prod >> /app/logs/backend.log 2>> /app/logs/backend-error.log'
fi
"@
Invoke-Native $ssh @("-i", $KeyPath, $remote, $remoteCommand)

Write-Host "Backend deployed: http://$HostName:8080/"
Write-Host "Logs: docker logs -f $ContainerName"
