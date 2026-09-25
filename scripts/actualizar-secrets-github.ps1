<#
.SYNOPSIS
    Copia las credenciales temporales de AWS Academy (~/.aws/credentials)
    a los secrets de GitHub Actions. Ejecutar cada vez que inicies el Learner Lab.

.EXAMPLE
    .\scripts\actualizar-secrets-github.ps1
#>
$ErrorActionPreference = 'Stop'
$Repo = 'angelguillen20/staysync'

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "    OK: $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "    FALLO: $msg" -ForegroundColor Red }

Write-Step "Verificando credenciales de AWS..."
aws sts get-caller-identity | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Fail "Credenciales inválidas. Copia las de AWS Details -> AWS CLI a ~/.aws/credentials y vuelve a intentar."
    exit 1
}
Write-Ok "Sesión activa."

Write-Step "Subiendo credenciales a los secrets de $Repo..."
gh secret set AWS_ACCESS_KEY_ID     --repo $Repo --body (aws configure get aws_access_key_id)
gh secret set AWS_SECRET_ACCESS_KEY --repo $Repo --body (aws configure get aws_secret_access_key)
gh secret set AWS_SESSION_TOKEN     --repo $Repo --body (aws configure get aws_session_token)
if ($LASTEXITCODE -ne 0) { Write-Fail "No se pudieron actualizar los secrets (¿hiciste 'gh auth login'?)."; exit 1 }
Write-Ok "Secrets de GitHub actualizados."
