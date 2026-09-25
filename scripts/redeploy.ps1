<#
.SYNOPSIS
    Rebuild + push + redeploy de los microservicios de StaySync a ECR/ECS.

.DESCRIPTION
    Cada Dockerfile compila el jar por dentro (multi-stage build), así que este
    script NO corre Maven en el host: solo hace `docker build` -> `docker push`
    -> `aws ecs update-service --force-new-deployment` por cada servicio.

    Requiere que ya hayas corrido `aws configure` con las credenciales temporales
    de la sesión activa de AWS Academy (expiran cada pocas horas).

.PARAMETER Services
    Lista de servicios a procesar. Por defecto, todos.
    Valores válidos: bff, usuarios, habitaciones, reservas, servicios, pagos, notificaciones, ota

.PARAMETER SkipDeploy
    Si se pasa, solo hace build+push a ECR, sin tocar ECS (útil para probar el build).

.PARAMETER Tag
    Tag de la imagen Docker. Por defecto "latest".

.EXAMPLE
    .\scripts\redeploy.ps1
    Rebuildea y redespliega TODOS los servicios.

.EXAMPLE
    .\scripts\redeploy.ps1 -Services bff,habitaciones
    Solo esos dos servicios.

.EXAMPLE
    .\scripts\redeploy.ps1 -Services bff -SkipDeploy
    Solo build+push del bff, sin forzar el deployment en ECS (por si quieres
    cambiar variables de entorno a mano antes de desplegar).
#>
param(
    [ValidateSet('bff','usuarios','habitaciones','reservas','servicios','pagos','notificaciones','ota')]
    [string[]]$Services = @('bff','usuarios','habitaciones','reservas','servicios','pagos','notificaciones','ota'),
    [switch]$SkipDeploy,
    [string]$Tag = 'latest'
)

$ErrorActionPreference = 'Stop'

# ── Config: ajusta esto una vez con tus datos reales ────────────────────────
$AwsAccountId = '348143777102'
$AwsRegion    = 'us-east-1'
$EcsCluster   = 'staysync-cluster'   # <-- CAMBIA esto por el nombre real de tu cluster en ECS

$EcrRegistry = "$AwsAccountId.dkr.ecr.$AwsRegion.amazonaws.com"
$RepoRoot    = Split-Path -Parent $PSScriptRoot

# service key -> { carpeta relativa al repo, nombre del repo ECR, nombre del service en ECS }
$ServiceMap = @{
    bff            = @{ Path = 'StaySync_BFF-bff_v1.0\StaySync_BFF-bff_v1.0';                         Repo = 'staysync-bff';            EcsService = 'bff' }
    usuarios       = @{ Path = 'StaySync_Usuarios-usuarios_v1.0\StaySync_Usuarios-usuarios_v1.0';     Repo = 'staysync-usuarios';       EcsService = 'usuarios' }
    habitaciones   = @{ Path = 'StaySync_Habitaciones-habitaciones_v1.0\StaySync_Habitaciones-habitaciones_v1.0'; Repo = 'staysync-habitaciones'; EcsService = 'habitaciones' }
    reservas       = @{ Path = 'StaySync_Reservas-reservas_v1.1\StaySync_Reservas-reservas_v1.1';     Repo = 'staysync-reservas';       EcsService = 'reservas' }
    servicios      = @{ Path = 'StaySync_Servicios-servicios_v1.0\StaySync_Servicios-servicios_v1.0'; Repo = 'staysync-servicios';      EcsService = 'servicios' }
    pagos          = @{ Path = 'StaySync_Pago-pagos_v1.0\StaySync_Pago-pagos_v1.0';                   Repo = 'staysync-pagos';          EcsService = 'pagos' }
    notificaciones = @{ Path = 'StaySync_Notificaciones-notificaciones_v1.0\StaySync_Notificaciones-notificaciones_v1.0'; Repo = 'staysync-notificaciones'; EcsService = 'notificaciones' }
    ota            = @{ Path = 'StaySync_OTA-ota_v1.0\StaySync_OTA-ota_v1.0';                         Repo = 'staysync-ota';            EcsService = 'ota' }
}

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "    OK: $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "    FALLO: $msg" -ForegroundColor Red }

Write-Step "Verificando credenciales de AWS..."
$identity = aws sts get-caller-identity 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Fail "No hay credenciales válidas. Corre 'aws configure' con las temporales de AWS Academy y vuelve a intentar."
    Write-Host $identity
    exit 1
}
Write-Ok "Sesión activa."

Write-Step "Autenticando Docker contra ECR ($EcrRegistry)..."
aws ecr get-login-password --region $AwsRegion | docker login --username AWS --password-stdin $EcrRegistry
if ($LASTEXITCODE -ne 0) { Write-Fail "No se pudo autenticar contra ECR."; exit 1 }
Write-Ok "Login a ECR correcto."

$results = @()

foreach ($svc in $Services) {
    $cfg = $ServiceMap[$svc]
    $fullPath = Join-Path $RepoRoot $cfg.Path
    $localImage  = "$($cfg.Repo):$Tag"
    $remoteImage = "$EcrRegistry/$($cfg.Repo):$Tag"

    Write-Step "[$svc] Build de la imagen Docker..."
    if (-not (Test-Path $fullPath)) {
        Write-Fail "No existe la carpeta $fullPath"
        $results += [pscustomobject]@{ Servicio = $svc; Build = 'FALLO (carpeta no encontrada)'; Push = '-'; Deploy = '-' }
        continue
    }

    docker build -t $localImage $fullPath
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "Build falló para $svc"
        $results += [pscustomobject]@{ Servicio = $svc; Build = 'FALLO'; Push = '-'; Deploy = '-' }
        continue
    }
    Write-Ok "Imagen construida: $localImage"

    Write-Step "[$svc] Tag + push a ECR..."
    docker tag $localImage $remoteImage
    docker push $remoteImage
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "Push falló para $svc"
        $results += [pscustomobject]@{ Servicio = $svc; Build = 'OK'; Push = 'FALLO'; Deploy = '-' }
        continue
    }
    Write-Ok "Imagen subida: $remoteImage"

    if ($SkipDeploy) {
        $results += [pscustomobject]@{ Servicio = $svc; Build = 'OK'; Push = 'OK'; Deploy = 'omitido (-SkipDeploy)' }
        continue
    }

    Write-Step "[$svc] Forzando nuevo deployment en ECS (cluster '$EcsCluster', service '$($cfg.EcsService)')..."
    aws ecs update-service --cluster $EcsCluster --service $cfg.EcsService --force-new-deployment --region $AwsRegion | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "No se pudo forzar el deployment de $svc (¿nombre de cluster/service correcto?)"
        $results += [pscustomobject]@{ Servicio = $svc; Build = 'OK'; Push = 'OK'; Deploy = 'FALLO' }
        continue
    }
    Write-Ok "Deployment forzado."
    $results += [pscustomobject]@{ Servicio = $svc; Build = 'OK'; Push = 'OK'; Deploy = 'OK' }
}

Write-Step "Resumen"
$results | Format-Table -AutoSize

if (-not $SkipDeploy) {
    Write-Host "`nRevisa el estado de cada servicio en ECS -> Clusters -> $EcsCluster -> Services -> pestaña Deployments." -ForegroundColor Yellow
}
