# Seguridad de StaySync

Estado a noviembre de 2026. Documenta las correcciones de seguridad aplicadas, cómo está protegido hoy el sistema y los riesgos que siguen abiertos, por prioridad.

**Contenido**

1. [Correcciones aplicadas](#1-correcciones-aplicadas)
2. [Acciones necesarias al desplegar](#2-acciones-necesarias-al-desplegar)
3. [Estado actual de la seguridad](#3-estado-actual-de-la-seguridad)
4. [Riesgos pendientes](#4-riesgos-pendientes)

---

## 1. Correcciones aplicadas

| # | Corrección | Servicio | Tests nuevos |
|---|---|---|---|
| 1.1 | Límite de peticiones por IP | ms_BFF | `LimitePeticionesFilterTest` (6) |
| 1.2 | Bloqueo temporal de la cuenta por intentos fallidos | ms_usuarios | `BloqueoLoginIntegrationTest` (4), `AuthServiceTest` (+1) |
| 1.3 | Errores de login mostrados como "servicio no disponible" | ms_BFF | `ClientesHttpTest` (ampliado) |
| 1.4 | Remitente de correo configurable (ya no está fijo en el código) | ms_notificaciones | `NotificacionServiceTest` (+2) |
| 1.5 | Firma HMAC en los webhooks de canales OTA | ms_OTA | `FirmaWebhookTest` (6), `WebhookOtaIntegrationTest` (4) |
| 1.6 | Actualización automática de dependencias (Dependabot) | repositorio | — |
| 1.7 | Cuentas demo solo con opt-in y super admin inicial por configuración | ms_usuarios, frontend | `DataInitializerTest` (7), `DatosInicialesIntegrationTest` (3) |

Todos los tests de los servicios modificados pasan: ms_BFF 93, ms_usuarios 83, ms_OTA 17 y ms_notificaciones 18.

### 1.1 Límite de peticiones por IP (ms_BFF)

**Problema.** Nada limitaba cuántas peticiones podía hacer un cliente. Se podía probar contraseñas sin freno contra `/bff/auth/login` (fuerza bruta, relleno de credenciales) o saturar la API con un script.

**Solución.** Nuevo filtro [LimitePeticionesFilter](../StaySync_BFF-bff_v1.0/StaySync_BFF-bff_v1.0/src/main/java/com/staysync/bff/security/LimitePeticionesFilter.java). Se ejecuta antes de validar el JWT y cuenta las peticiones por IP en ventanas de un minuto:

| Rutas | Límite por IP y minuto | Variable |
|---|---|---|
| `POST /bff/auth/login`, `/bff/auth/registro`, `/bff/auth/google` (comparten contador) | 10 | `LIMITE_AUTH_POR_MINUTO` |
| Resto de `/bff/**` | 600 | `LIMITE_API_POR_MINUTO` |

Al superarlo, la respuesta es `429 Too Many Requests` con el header `Retry-After` y un mensaje que el frontend ya muestra, por ejemplo "Demasiados intentos de inicio de sesión. Espera 42 segundos e inténtalo de nuevo.". Los health checks y Swagger (rutas fuera de `/bff/`) no se limitan.

Para que el límite sea por cliente y no por balanceador, el BFF usa ahora `server.forward-headers-strategy: native`. Tomcat toma la IP real de `X-Forwarded-For` solo si la petición llega desde una red privada, como el ALB o nginx, así que un cliente no puede falsearla enviando ese header.

**Limitaciones.**
- **Contadores en memoria.** Cada instancia del BFF lleva sus propios contadores, así que con N instancias el límite efectivo puede ser hasta N veces mayor. Si se escala horizontalmente, conviene moverlo a Redis o a un WAF (AWS WAF tiene reglas de rate limiting).
- **IP compartida.** Toda una recepción detrás de la misma IP comparte el límite general. Por eso es amplio (600 por minuto).

### 1.2 Bloqueo temporal de la cuenta (ms_usuarios)

**Problema.** El límite por IP no frena un ataque repartido entre muchas IPs contra una misma cuenta.

**Solución.** Tras **5 contraseñas incorrectas seguidas**, la cuenta queda bloqueada **15 minutos**. Mientras dura el bloqueo, el login responde `423 Locked` con el mensaje "Cuenta bloqueada temporalmente por varios intentos fallidos. Inténtalo de nuevo en N minutos.", aunque la contraseña sea correcta. Un login correcto reinicia el contador.

- [IntentosLoginService](../StaySync_Usuarios-usuarios_v1.0/StaySync_Usuarios-usuarios_v1.0/src/main/java/com/staysync/usuarios/service/IntentosLoginService.java) guarda el contador en la base de datos, en las columnas nuevas `usuarios.intentos_fallidos` y `usuarios.bloqueado_hasta`. Por eso vale para todas las instancias.
- El fallo se registra en su propia transacción: el login fallido se revierte, el contador no. La fila se bloquea al actualizarla para que dos intentos simultáneos no se pisen.
- Un email que no existe responde `401` como siempre y no deja rastro.
- El login con Google no se ve afectado, porque no usa contraseña.

Configuración: `LOGIN_MAX_INTENTOS` (5 por defecto) y `LOGIN_BLOQUEO` (`15m` por defecto).

**Para desbloquear una cuenta a mano:** `UPDATE usuarios SET bloqueado_hasta = NULL, intentos_fallidos = 0 WHERE email = '...';`

**Contrapartida.** El mensaje de bloqueo confirma que el email existe, porque solo las cuentas reales se bloquean. Es la práctica habitual, pero un atacante podría usarlo para saber qué emails están registrados.

### 1.3 Errores de login mostrados como "servicio no disponible" (ms_BFF)

**Problema.** En `UsuariosClient`, el fallback del circuit breaker para el login convertía **cualquier** error en `503 "El servicio de autenticación no está disponible"`, incluida una contraseña incorrecta (401). El resto de fallbacks del cliente ya reenviaba los errores 4xx. Sin este arreglo, el mensaje de cuenta bloqueada (1.2) tampoco llegaría al usuario.

**Solución.** `loginFallback` reenvía ahora los errores 4xx tal cual. El usuario ve "Credenciales incorrectas" o "Cuenta bloqueada…" según el caso, y solo una caída real muestra "servicio no disponible".

### 1.4 Remitente de correo configurable (ms_notificaciones)

**Problema.** El remitente de todos los correos estaba fijo en el código con una cuenta personal de Gmail (`NotificacionService.java`). Exponía un dato personal en el repositorio y obligaba a cambiar código para usar el dominio del hotel.

**Solución.** El remitente se configura con la variable **`MAIL_FROM`** (propiedad `notificaciones.mail.from`). Si el envío está activo (`MAIL_ENABLED=true`, el valor por defecto) y `MAIL_FROM` falta, **el servicio no arranca** y muestra un mensaje claro: sin remitente, el proveedor SMTP rechazaría todos los correos, y es mejor detectarlo al desplegar que perderlos en silencio. Para desarrollo sin correo: `MAIL_ENABLED=false`, y los correos solo se escriben en el log.

El remitente debe ser una dirección verificada en el proveedor SMTP (Brevo, Gmail…). `docker-compose.yml` y `.env.example` incluyen ya `MAIL_FROM` y `MAIL_ENABLED`.

### 1.5 Firma de los webhooks OTA (ms_OTA)

**Problema.** `POST /api/v1/webhooks/{codigoCanal}` aceptaba cualquier JSON sin autenticar. Cualquiera que conociera la URL podía registrar reservas externas falsas.

**Solución.** Cada canal firma sus webhooks con un secreto compartido (uno por canal), con el mismo esquema que usan Stripe o GitHub. [FirmaWebhook](../StaySync_OTA-ota_v1.0/StaySync_OTA-ota_v1.0/src/main/java/com/staysync/ota/security/FirmaWebhook.java) lo verifica **antes** de interpretar el cuerpo:

```
X-Webhook-Timestamp: <segundos Unix del envío>
X-Webhook-Signature: sha256=<hex(HMAC-SHA256(secreto_del_canal, timestamp + "." + cuerpo))>
```

Se responde `401 "Firma del webhook inválida"` en estos casos:
- el canal no tiene secreto configurado (por defecto se rechaza);
- faltan los headers o la firma no coincide (cuerpo alterado u otro secreto);
- el timestamp se aleja más de 5 minutos (`ota.webhooks.tolerancia`), lo que impide reenviar un webhook capturado.

La comparación de firmas es en tiempo constante, y el motivo concreto del rechazo solo queda en el log. Un cuerpo firmado que no es JSON responde `400`.

**Configurar un canal:** define `OTA_WEBHOOKS_SECRETOS_<CODIGO>` (por ejemplo `OTA_WEBHOOKS_SECRETOS_BOOKING`) con un secreto aleatorio (`openssl rand -hex 32`) y compártelo con el canal por un medio seguro.

**Ejemplo de envío firmado** (para el canal o para probar):

```bash
SECRETO='...'
TS=$(date +%s)
BODY='{"id":"BK-1001","huesped":"Ana Rojas"}'
SIG="sha256=$(printf '%s.%s' "$TS" "$BODY" | openssl dgst -sha256 -hmac "$SECRETO" -hex | sed 's/^.* //')"
curl -X POST http://localhost:8087/api/v1/webhooks/BOOKING \
  -H 'Content-Type: application/json' \
  -H "X-Webhook-Timestamp: $TS" -H "X-Webhook-Signature: $SIG" \
  -d "$BODY"
```

### 1.6 Dependabot

**Problema.** Nada avisaba de dependencias con vulnerabilidades conocidas en los 9 proyectos Maven, el frontend npm, las imágenes Docker o las GitHub Actions.

**Solución.** [.github/dependabot.yml](../.github/dependabot.yml) abre cada lunes PRs con actualizaciones de:
- Maven: los 8 microservicios y `commons`;
- npm: el frontend;
- Docker: las imágenes base de cada Dockerfile;
- GitHub Actions.

Las versiones menores y los parches se agrupan en un solo PR por ecosistema, y las mayores llegan por separado para revisarlas. Cada PR pasa por el CI existente.

### 1.7 Cuentas demo con contraseñas publicadas (ms_usuarios)

**Problema.** [DataInitializer](../StaySync_Usuarios-usuarios_v1.0/StaySync_Usuarios-usuarios_v1.0/src/main/java/com/staysync/usuarios/config/DataInitializer.java) creaba en cada arranque, en **cualquier entorno**, 7 cuentas con contraseñas escritas en el repositorio, incluido un `SUPER_ADMIN` (`superadmin@staysync.com`) que administra todos los hoteles. Cualquiera que leyera el código podía entrar como superadministrador en producción. Además, era la única forma de tener un primer super admin.

**Solución.**
- **Datos demo solo con opt-in.** El segundo hotel (`hotel-andes`) y las 7 cuentas demo se crean solo con `DEMO_DATA_ENABLED=true`. El valor por defecto del servicio es `false`. El hotel por defecto se sigue creando siempre, porque los registros sin `hotelSlug` lo necesitan.
- **Saneamiento de bases ya desplegadas.** Con el modo demo apagado, cada arranque **desactiva** las cuentas demo que todavía usan su contraseña publicada, y lo deja en el log como `WARN`. Las cuentas a las que alguien ya cambió la contraseña no se tocan. Al desactivar una cuenta, su refresh token deja de funcionar. Su access token vigente expira en 15 minutos como máximo.
- **Vuelta al modo demo.** Si se vuelve a `DEMO_DATA_ENABLED=true`, se reactivan las cuentas demo que conservan la contraseña publicada.
- **Super admin inicial por configuración.** Con `SUPERADMIN_EMAIL` y `SUPERADMIN_PASSWORD`, al arrancar se crea un `SUPER_ADMIN` si no hay ninguno activo. La creación se omite en estos casos:
  - ya hay un super admin activo;
  - el email ya pertenece a otra cuenta: una cuenta existente nunca se promueve ni cambia de contraseña desde la configuración.

  El servicio **no arranca** si la configuración es inválida:
  - falta una de las dos variables;
  - la contraseña no cumple la política del registro (8–100 caracteres, con mayúscula, minúscula, número y `@$!%*?&`);
  - la contraseña es una de las demo.

  La contraseña nunca se escribe en el log.
- **Frontend.** Los botones con credenciales demo del login pasan a ser opt-in:
  - en un build solo aparecen con `VITE_DEMO_CREDENCIALES=true`;
  - con `npm run dev` aparecen salvo que esa variable valga `false`;
  - si no aparecen, el build de producción no incluye las contraseñas.

**Configuración por entorno.**

| Entorno | `DEMO_DATA_ENABLED` | `VITE_DEMO_CREDENCIALES` |
|---|---|---|
| `docker compose` local | `true` por defecto (`.env` lo puede cambiar); compose usa el mismo valor para el frontend | — |
| ECS / producción | sin definir (`false`) | variable de repositorio de GitHub sin definir |
| Demo pública en AWS (presentaciones) | `true` en la task definition de `usuarios` | `true` como variable de repositorio (*Settings → Variables*) |

**Limitación.** Las contraseñas demo siguen en el repositorio: no deben reutilizarse en ningún otro sitio, y un entorno demo no debe contener datos reales.

---

## 2. Acciones necesarias al desplegar

| Acción | Dónde | Si no se hace |
|---|---|---|
| Definir `MAIL_FROM` con un remitente verificado: `gh variable set MAIL_FROM --body <remitente>` (el CI lo pasa a la tarea) | Variable del repositorio en GitHub y `.env` local | El deploy de `notificaciones` se detiene con un error y la versión anterior sigue corriendo; en local, **ms_notificaciones no arranca** (o desactivar el envío con `MAIL_ENABLED=false`) |
| Definir `OTA_WEBHOOKS_SECRETOS_<CODIGO>` por cada canal que envíe webhooks | Entorno de ms_OTA | Los webhooks de ese canal se rechazan con 401 (hoy ms_OTA no se despliega en ECS) |
| Activar *Dependabot alerts* y *Dependabot security updates* | GitHub → Settings → Code security | Solo llegan los PRs semanales; no hay alertas inmediatas de vulnerabilidades |
| Decidir si el entorno de AWS es una demo pública. El CI pasa `DEMO_DATA_ENABLED` al backend y `VITE_DEMO_CREDENCIALES` al build del frontend desde la variable de repositorio `DEMO_DATA_ENABLED` (`true` si no existe). Para apagar la demo: `gh variable set DEMO_DATA_ENABLED --body false` y, en el primer despliegue, `SUPERADMIN_EMAIL` y `SUPERADMIN_PASSWORD` en la tarea de `usuarios` | GitHub → Settings → Variables y task definition de `usuarios` | Con la demo apagada, al arrancar se **desactivan** las cuentas demo que conserven su contraseña publicada (incluida `superadmin@staysync.com`) y desaparecen los botones demo del login. Sin super admin inicial no queda ningún `SUPER_ADMIN` activo: nadie puede dar de alta hoteles |
| Tras crear el super admin inicial, **quitar `SUPERADMIN_PASSWORD`** de la task definition | Task definition de `usuarios` | La contraseña queda en texto plano en la tarea (ver riesgo de secretos en la sección 4) |
| (Opcional) Ajustar `LIMITE_AUTH_POR_MINUTO`, `LIMITE_API_POR_MINUTO`, `LOGIN_MAX_INTENTOS`, `LOGIN_BLOQUEO` | Entorno de ms_BFF / ms_usuarios | Se usan los valores por defecto (10, 600, 5, 15m) |

Las columnas nuevas de `usuarios` (`intentos_fallidos`, `bloqueado_hasta`) las crea Hibernate al arrancar (`ddl-auto: update`). Las filas existentes quedan con `intentos_fallidos = 0`.

---

## 3. Estado actual de la seguridad

| Área | Cómo está protegido hoy |
|---|---|
| **Contraseñas** | BCrypt con coste 12 (ms_usuarios). Bloqueo de cuenta tras 5 intentos fallidos y límite de 10 intentos por minuto por IP. |
| **Cuentas iniciales** | Sin cuentas con contraseñas conocidas fuera del modo demo (1.7). El primer super admin se crea desde la configuración, con la misma política de contraseñas que el registro. |
| **Sesión** | JWT propio firmado con HMAC (`JWT_SECRET`): access token de 15 min y refresh token de 7 días guardado en base de datos. El refresh token se **rota** en cada uso (se borra el anterior) y se revoca al cerrar sesión. Login con Google mediante Cognito Hosted UI con PKCE. |
| **Autorización** | Reglas por ruta y método en el BFF ([SecurityConfig](../StaySync_BFF-bff_v1.0/StaySync_BFF-bff_v1.0/src/main/java/com/staysync/bff/config/SecurityConfig.java)) con los roles `HUESPED`, `RECEPCIONISTA`, `ADMIN` y `SUPER_ADMIN` (`SUPER_ADMIN` hereda todo lo de `ADMIN`), más `@PreAuthorize` en ms_usuarios. Sin JWT válido la respuesta es 401; sin permiso, 403. |
| **Aislamiento entre hoteles** | El BFF saca el hotel del JWT firmado y lo envía a los servicios en `X-Tenant-Id`, reemplazando cualquier valor que venga del cliente. Los servicios de negocio rechazan peticiones sin hotel (`staysync.tenant.requerido: true`). Hibernate filtra **automáticamente** cada consulta por hotel (`@TenantId`): una consulta que olvida el hotel no ve nada en vez de ver todo. Los mensajes SQS llevan el hotel y se valida su formato. Ver [eventos.md](eventos.md). |
| **Datos personales** | El teléfono del usuario se cifra en la base de datos con AES-256-GCM (`AesGcmStringConverter`, clave `AES_ENCRYPTION_KEY`). El resto de datos personales (nombre, email, datos del huésped en reservas) se guarda en claro. |
| **Pagos** | Stripe Checkout alojado: los datos de la tarjeta nunca pasan por los servidores de StaySync, lo que deja el alcance PCI DSS en el nivel más bajo (SAQ A). La confirmación del pago se verifica contra Stripe en el servidor, sin confiar en el navegador. |
| **Webhooks** | Firma HMAC con timestamp en los webhooks OTA (1.5). |
| **Secretos** | Por variables de entorno, sin valores reales en el repositorio. `.env` está en `.gitignore`. En AWS se leen de Secrets Manager al desplegar (`scripts/deploy/task_definition.py`), y la clave de RDS rota cada 7 días. |
| **CORS** | Lista blanca de orígenes con `CORS_ALLOWED_ORIGINS` en el BFF. |
| **Cabeceras HTTP** | El BFF envía las cabeceras por defecto de Spring Security: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-store` en las respuestas de la API, y HSTS cuando la conexión es HTTPS. CSRF está desactivado porque la API no usa cookies de sesión (el JWT viaja en el header `Authorization`). |
| **Validación de entrada** | Bean Validation (`@Valid`) en los DTO de entrada de los servicios. |
| **Abuso** | Límite de peticiones por IP en el BFF (1.1). |
| **Dependencias** | Dependabot semanal (1.6). Análisis estático con SonarQube disponible (`docker-compose.sonar.yml`), aunque el CI no lo exige. |

---

## 4. Riesgos pendientes

Ordenados por prioridad. Ninguno se ha corregido todavía. El único riesgo crítico conocido, las cuentas demo con contraseñas publicadas, se corrigió en 1.7.

### Alto

- **Claves con valor por defecto versionado.** `jwt.secret` (ms_BFF y ms_usuarios) y `security.encryption.aes-key` (ms_usuarios) tienen un valor por defecto escrito en `application.yml`. Si un despliegue olvida `JWT_SECRET`, el servicio arranca con una clave pública y cualquiera puede firmar un JWT de `SUPER_ADMIN`. Si olvida `AES_ENCRYPTION_KEY`, los teléfonos se cifran con una clave conocida. Hay que eliminar esos valores por defecto y hacer que el servicio no arranque sin ellos, igual que con `MAIL_FROM` (1.4). Los tests ya fijan su propia clave en `application-test.yml`.

- **Sin webhook de Stripe.** El pago solo se registra si el navegador vuelve a la página de confirmación. Si el huésped cierra la pestaña tras pagar, Stripe cobra y StaySync no se entera. Hay que escuchar `checkout.session.completed` verificando la firma de Stripe. Los reembolsos tampoco llegan a Stripe (ver [eventos.md](eventos.md#limitaciones-conocidas)).
- **Los servicios internos confían en la red.** Solo el BFF y ms_usuarios validan el JWT. El resto acepta cualquier petición con `X-Tenant-Id`, porque se supone que solo el BFF puede alcanzarlos a través del ALB interno. Si un servicio queda expuesto por error, sus datos quedan accesibles. Para defensa en profundidad, validar también el JWT (o un token de servicio) en cada microservicio y restringir los security groups.
- **Secretos en texto plano en la task definition.** AWS Academy impide que ECS lea Secrets Manager, así que el deploy copia los valores a variables de entorno de la tarea. Quien tenga `ecs:DescribeTaskDefinition` puede verlos. En una cuenta AWS propia hay que volver a `secrets` nativos de ECS.
- **Sin registro de auditoría.** No queda constancia de quién cancela, reembolsa, cambia roles o modifica precios. Es clave ante fraude interno y disputas. Hay que guardar en una tabla de solo inserción el actor, la acción, el recurso, la fecha y el hotel.
- **Ley 21.719 de protección de datos personales** (vigente desde diciembre de 2026). Falta una política de retención y borrado, el derecho de supresión, el registro de tratamientos y ampliar el cifrado a más datos del huésped.

### Medio

- **Información expuesta en el BFF.** Swagger UI (`/swagger-ui.html`) es público, y `/actuator/health` muestra el detalle de cada componente (`show-details: always`). En producción conviene desactivar Swagger y usar `show-details: when-authorized`.
- **Sin cabeceras de seguridad en el frontend.** Ni nginx ni el hosting en S3 envían `Content-Security-Policy`, `Strict-Transport-Security` o `Referrer-Policy`. Se pueden añadir con CloudFront (*response headers policy*).
- **Límite de peticiones por instancia** (ver 1.1). Con varias instancias del BFF, migrar a Redis o a AWS WAF.
- **Esquema sin migraciones versionadas.** `ddl-auto: update` en los 7 servicios: cambios de esquema sin historial ni revisión. Conviene migrar a Flyway.
- **Valor por defecto del usuario SMTP** en `ms_notificaciones/application.yml` (`MAIL_USERNAME`). No es un secreto, pero identifica la cuenta del proveedor: mejor dejarlo solo en variables de entorno.
- **Escaneo de código e imágenes.** Dependabot cubre las dependencias; faltan CodeQL o Sonar con quality gate en el CI y el escaneo de imágenes Docker (Trivy o el *image scanning* de ECR).
