# Eventos de StaySync (SNS + SQS)

Contrato de los eventos entre microservicios. Si cambias un evento, actualiza este documento y sube la `version` del payload.

## Cómo viajan

```
Servicio (en su transacción)                    SNS                       SQS (una por consumidor)
┌──────────────────────────┐   relay cada 2 s   ┌──────────────────┐ filtro  ┌───────────────────┐
│ cambio de negocio        │ ─────────────────▶ │ staysync-eventos │ ──────▶ │ <servicio>-q      │ ──▶ @SqsListener
│ + fila en outbox_eventos │                    └──────────────────┘ "tipo"  │  └─ <servicio>-dlq│
└──────────────────────────┘                                                 └───────────────────┘
```

1. **Publicar (outbox).** El servicio guarda el evento en la tabla `outbox_eventos`, en la **misma transacción** que el cambio de negocio. Si la transacción se revierte, el evento no existe. Si se confirma, `OutboxRelay` lo publica en SNS, aunque SNS haya estado caído un rato.
2. **Repartir (SNS).** El topic copia el evento a cada cola cuyo **filtro** incluye su `tipo`. Las suscripciones usan *raw message delivery*: el cuerpo del mensaje SQS es directamente el evento.
3. **Consumir (SQS).** El servicio lo procesa con `ProcesadorEventos`:
   - fija el **hotel del evento**, y todas las consultas quedan filtradas por ese hotel;
   - ignora los **duplicados** por `eventId` (tabla `eventos_procesados`);
   - si el manejador falla, el mensaje se reintenta y, **tras 3 fallos, va a la DLQ**.

Código: `StaySync_Commons/staysync-eventos`. Infraestructura: `scripts/mensajeria/crear-mensajeria.sh`.

## Sobre (igual para todos los eventos)

```json
{
  "eventId":    "7f3c9a2e-1b4d-4e8a-9c11-0d2f5e6a7b80",
  "tipo":       "PagoEfectuado",
  "version":    1,
  "tenantId":   "hotel-andes",
  "ocurridoEn": "2026-10-18T15:42:08Z",
  "payload":    { "...": "..." }
}
```

| Campo | Para qué |
|---|---|
| `eventId` | Identificador único. El consumidor lo usa para no procesar dos veces el mismo evento. |
| `tipo` | Nombre del evento. También va como atributo de mensaje `tipo`, por el que filtra SNS. |
| `version` | Versión del payload. Un cambio incompatible sube la versión. |
| `tenantId` | Hotel al que pertenece el evento. También va como atributo `tenantId`. |
| `ocurridoEn` | Cuándo ocurrió el hecho (UTC). |

## Eventos

### Reservas

Los publica **reservas-service**. Todos llevan el mismo payload:

| Campo | Ejemplo |
|---|---|
| `reservaId` | `1042` |
| `codigo` | `"RES-1A2B3C4D"` |
| `usuarioId` | `7` |
| `habitacionId` | `12` |
| `fechaEntrada` / `fechaSalida` | `"2026-11-18"` / `"2026-11-20"` |
| `precioTotal` | `180000` |
| `estado` | `"CONFIRMADA"` |
| `huespedEmail` / `huespedNombre` | `"ana@correo.cl"` / `"Ana Rojas"` (si se conocen) |

| Tipo | Cuándo |
|---|---|
| `ReservaCreada` | Se crea una reserva (queda PENDIENTE de pago). |
| `ReservaConfirmada` | Pasa a CONFIRMADA (normalmente tras el pago). |
| `ReservaCancelada` | Se cancela (por el huésped o por recepción). |
| `ReservaCheckin` | Check-in (manual o el automático de medianoche). |
| `ReservaCheckout` | Check-out. |

### Pagos

Los publica **pagos-service**:

| Campo | Ejemplo |
|---|---|
| `pagoId` | `55` |
| `referencia` | `"STRIPE-9F8E7D6C5B"` |
| `reservaId` | `1042` |
| `usuarioId` | `7` |
| `monto` | `180000` |
| `moneda` | `"CLP"` |

| Tipo | Cuándo |
|---|---|
| `PagoEfectuado` | Se cobró un pago asociado a una reserva. |
| `ReembolsoEfectuado` | Se devolvió un pago. |

## Quién consume qué

| Evento | notificaciones-q | reservas-q | habitaciones-q | servicios-q | pagos-q |
|---|:-:|:-:|:-:|:-:|:-:|
| `ReservaCreada` | correo "reserva registrada" | | | | |
| `ReservaConfirmada` | correo | | | programa el aseo | |
| `ReservaCancelada` | correo | | | cancela el aseo | reembolsa los pagos |
| `ReservaCheckin` | correo | | habitación OCUPADA | | |
| `ReservaCheckout` | correo | | habitación DISPONIBLE | aseo listo para limpiar | |
| `PagoEfectuado` | recibo | confirma la reserva | | | |
| `ReembolsoEfectuado` | correo | | | | |

Ejemplo de la cadena completa (el huésped paga):

1. El huésped paga. Pagos guarda el pago y publica `PagoEfectuado`.
2. Reservas confirma la reserva y publica `ReservaConfirmada`. Notificaciones envía el recibo.
3. Servicios programa el aseo para el día de salida. Notificaciones avisa que la reserva quedó confirmada.

## Reglas para quien agregue un evento o consumidor

- **Publicar siempre con `Outbox.registrar(...)`** dentro de la transacción del cambio, nunca llamando a SNS directamente.
- **Los manejadores deben ser idempotentes.** El procesador ya descarta los `eventId` repetidos, pero un mismo hecho puede llegar en dos eventos distintos. Por ejemplo, `confirmarPorPago` no hace nada si la reserva ya estaba confirmada.
- **Un tipo nuevo hay que agregarlo** en tres lugares: `TiposEvento`, este documento y el filtro de las colas que lo consumen en `crear-mensajeria.sh`.
- **No guardes datos sensibles en el payload** (tarjetas, contraseñas). Viaja por SNS/SQS y se guarda en `outbox_eventos`.

## Operación

| Qué | Cómo |
|---|---|
| Activar la mensajería en un servicio | `EVENTOS_ENABLED=true` + `EVENTOS_TOPIC_ARN`. Con `false` (tests), los eventos se acumulan en el outbox y se publican cuando se active. |
| Ver colas en local | `docker exec staysync-localstack awslocal sqs list-queues` |
| Mensajes que fallaron 3 veces | Quedan en `<servicio>-dlq`. Se revisan y se reenvían a la cola original una vez corregido el problema. |
| Eventos aún no publicados | `SELECT * FROM outbox_eventos WHERE publicado_en IS NULL` en la base del servicio. `ultimo_error` dice por qué falló. |
| Crear la mensajería en AWS | `AWS_CMD=aws AWS_REGION=us-east-1 bash scripts/mensajeria/crear-mensajeria.sh` (por ejemplo, desde CloudShell). Las tareas ECS necesitan permisos SNS `Publish` y SQS `Receive/Delete/GetQueueUrl/GetQueueAttributes`; en AWS Academy los da el `LabRole`. |
