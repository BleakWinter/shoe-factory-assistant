param(
    [string]$HostName = "192.168.245.130",
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
$archive = Join-Path $env:TEMP "shoe-factory-assistant-web.tar.gz"
$remoteArchive = "/tmp/shoe-factory-assistant-web.tar.gz"
$remote = "${User}@${HostName}"

Write-Host "Building frontend..."
Push-Location $webDir
try {
    npm.cmd run build
} finally {
    Pop-Location
}

if (Test-Path -LiteralPath $archive) {
    Remove-Item -LiteralPath $archive -Force
}

Write-Host "Packing dist..."
tar -czf $archive -C $distDir .

Write-Host "Uploading to $remote..."
ssh -i $KeyPath $remote "mkdir -p '$RemoteDir'"
scp -i $KeyPath $archive "${remote}:${remoteArchive}"

Write-Host "Deploying to $RemoteDir..."
ssh -i $KeyPath $remote "find '$RemoteDir' -mindepth 1 -maxdepth 1 -exec rm -rf {} + && tar -xzf '$remoteArchive' -C '$RemoteDir' && rm -f '$remoteArchive' && docker restart '$NginxContainer'"

Write-Host "Frontend deployed: http://$HostName/"
