param(
    [string]$HostName = "192.168.10.62",
    [string]$User = "root",
    [string]$KeyPath = "$env:USERPROFILE\.ssh\id_rsa",
    [string]$RemoteDir = "/data/web/shoe-factory-assistant",
    [string]$NginxContainer = "nginx"
)

$ErrorActionPreference = "Stop"

$deployDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $deployDir
$webDir = Join-Path $repoRoot "web"
$distDir = Join-Path $webDir "dist"
$remoteStage = "/tmp/shoe-factory-assistant-web"
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

Write-Host "Building frontend..."
Push-Location $webDir
try {
    npm.cmd run build
} finally {
    Pop-Location
}

Write-Host "Uploading to $remote..."
Invoke-Native $ssh @("-i", $KeyPath, $remote, "rm -rf '$remoteStage' && mkdir -p '$remoteStage' '$RemoteDir'")
Invoke-Native $scp @("-i", $KeyPath, "-r", $distDir, "${remote}:${remoteStage}/")

Write-Host "Deploying to $RemoteDir..."
Invoke-Native $ssh @("-i", $KeyPath, $remote, "find '$RemoteDir' -mindepth 1 -maxdepth 1 -exec rm -rf {} + && cp -a '$remoteStage'/dist/. '$RemoteDir'/ && chmod -R 755 '$RemoteDir' && rm -rf '$remoteStage' && if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx '$NginxContainer'; then docker restart '$NginxContainer'; elif command -v nginx >/dev/null 2>&1; then nginx -s reload || systemctl reload nginx; fi")

Write-Host "Frontend deployed: http://$HostName/"
