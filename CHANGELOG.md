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
- Reservas: las PENDIENTE sin pagar se cancelan a las 24 h y liberan la habitación; el confirmado que no llega pasa a NO_SHOW
- Frontend: las páginas se cargan bajo demanda (bundle inicial 388 KB → 249 KB)
- Cuenta del huésped: estadía + servicios consumidos − pagos; recepción cobra el saldo y el check-out exige la cuenta saldada
- Catálogo de servicios administrable por el hotel (con foto); 6 categorías precargadas
- Fotos: logo del hotel, galería por habitación (con portada) y foto de servicio, subidas a `media/` del bucket del frontend
- Recepción: registrar huéspedes sin cuenta (walk-in) con contraseña temporal y modificar fechas/habitación de una reserva
- Calendario de ocupación (habitaciones × días) y reportes mensuales (ocupación, ingresos, tarifa promedio, RevPAR, CSV)
- Diseño: tipografía Onest, placa de número de habitación, superficies planas y menú agrupado por tarea

### Cambiado
- Mensajería asíncrona: RabbitMQ reemplazado por SNS + SQS con DLQ (LocalStack en local)
- El health de notificaciones ya no depende del servidor SMTP
- Reservas: sin check-in automático a medianoche; recepción lo hace desde el día de llegada
- Reservas: "hoy" es el día del hotel (America/Santiago), no el del servidor en UTC
- Pagos: el pago en recepción queda COMPLETADO (sin pasarela simulada) y la moneda por defecto es CLP
- El huésped paga en línea solo con Stripe; efectivo y transferencia se registran en recepción

### Corregido
- Pagos: el monto lo fijaba el navegador; ahora el BFF usa el `precioTotal` de la reserva y solo cobra reservas PENDIENTE
- Pagos: crédito, débito, transferencia y efectivo siempre fallaban (faltaba `gatewayToken`)
- Reservas: si habitaciones-service no respondía se cobraba un precio inventado de 100 por noche
- Recepción: la reserva manual mandaba `fuente: DIRECTA` y sin huésped, y nunca se creaba
- Frontend: las fechas se mostraban un día antes (`new Date('YYYY-MM-DD')` es UTC)
- BFF: el detalle de reserva leía `codigoReserva` y `precio` en vez de `codigo` y `precioPorNoche`
- Recepción solo veía las 10 reservas más recientes (el BFF no pedía `size`)
- Búsqueda de habitaciones y reserva manual mostraban el tipo vacío (`tipo` en vez de `tipoNombre`)

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
