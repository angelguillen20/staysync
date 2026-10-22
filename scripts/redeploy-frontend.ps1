<#
.SYNOPSIS
    Build + deploy del frontend a S3.

.EXAMPLE
    .\scripts\redeploy-frontend.ps1
#>
$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$FrontendPath = Join-Path $RepoRoot 'Frontend-StaySync-StaySync_Front_v1.1\Frontend-StaySync-StaySync_Front_v1.1'

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "    OK: $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "    FALLO: $msg" -ForegroundColor Red }

Write-Step "Verificando credenciales de AWS..."
aws sts get-caller-identity | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Fail "No hay credenciales válidas. Corre 'aws configure' con las temporales de AWS Academy."
    exit 1
}
Write-Ok "Sesión activa."

# Mismo nombre que usa el workflow: staysync-front-<id de la cuenta>
$S3Bucket = "staysync-front-$(aws sts get-caller-identity --query Account --output text)"

Write-Step "Instalando dependencias y compilando el frontend..."
Push-Location $FrontendPath
try {
    npm install
    if ($LASTEXITCODE -ne 0) { throw "npm install falló" }

    npm run build
    if ($LASTEXITCODE -ne 0) { throw "npm run build falló" }
} finally {
    Pop-Location
}
Write-Ok "Build generado en dist/"

Write-Step "Sincronizando con S3 ($S3Bucket)..."
aws s3 sync (Join-Path $FrontendPath 'dist') "s3://$S3Bucket" --delete
if ($LASTEXITCODE -ne 0) {
    Write-Fail "Falló el sync a S3."
    exit 1
}
Write-Ok "Frontend publicado en s3://$S3Bucket"
