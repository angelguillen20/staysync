# Changelog — StaySync

Todas las versiones lanzadas a clientes se documentan aquí.
Formato basado en [Keep a Changelog](https://keepachangelog.com/es/1.1.0/).

---

## [1.0.0] — 2026-06-18

### Primera versión estable para clientes

#### Incluye
- **BFF v1.0.0** — API Gateway con autenticación JWT, circuit breaker (Resilience4j) y proxy a todos los microservicios
- **Usuarios v1.0.0** — Registro, login, refresh token, gestión de perfiles y roles (ADMIN, RECEPCIONISTA, HUESPED)
- **Habitaciones v1.0.0** — Inventario de habitaciones con amenidades, tipos, filtros por capacidad y disponibilidad
- **Reservas v1.1.0** — Creación, confirmación, cancelación (regla 24h), check-in/check-out automático por cron
- **Servicios v1.0.0** — Catálogo de servicios adicionales y solicitudes por reserva
- **Pagos v1.0.0** — Checkout y confirmación de pagos vía Stripe, idempotencia por gateway ID
- **Notificaciones v1.0.0** — Envío de emails por eventos asincrónicos (RabbitMQ): registro, reserva, cambios de estado
- **OTA v1.0.0** — Integración con canales de distribución externos
- **Frontend v1.1.0** — Interfaz web React 18 con panel de recepción, gestión de reservas y portal de huésped

#### Infraestructura
- Docker multi-stage para todos los servicios (imagen ~200 MB vs ~500 MB)
- Docker Compose completo para despliegue local y AWS EC2
- Health checks en todos los contenedores vía Spring Boot Actuator
- MySQL 8.0 por servicio (base de datos independiente)
- RabbitMQ 3.13 para mensajería asíncrona entre servicios

#### Seguridad
- JWT stateless en todos los microservicios
- Stripe Secret Key solo via variable de entorno (sin valores hardcodeados)
- Usuario no-root en todos los contenedores Docker
- HTTPS recomendado via AWS ALB en producción

---

## [Sin publicar]

### Agregado
- Multi-hotel (tenant): hotel en el JWT, `X-Tenant-Id` desde el BFF, filtrado automático por hotel y SUPER_ADMIN
- Marca blanca por hotel en el frontend
- `StaySync_Commons`: librerías compartidas de tenant y eventos
- GitHub Actions CI/CD: tests, imágenes en ECR, despliegue en ECS Fargate y frontend en S3

### Cambiado
- Mensajería asíncrona: RabbitMQ reemplazado por SNS + SQS con DLQ (LocalStack en local)
- El health de notificaciones ya no depende del servidor SMTP

### Eliminado
- `docker-compose.prod.yml` (despliegue en EC2) y `scripts/redeploy.ps1`: el despliegue lo hace el CI

---

## [Próximo] — Sin fecha

### Planificado
- Playwright E2E tests para flujos críticos (login → reserva → pago)

---

> Para ver el código de una versión específica, usa el tag de git correspondiente:
> ```bash
> git checkout v1.0.0
> ```
