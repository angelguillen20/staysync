# Contrato de eventos de dominio

Los microservicios se comunican de forma asíncrona con eventos publicados en un topic SNS (`staysync-eventos`). Cada servicio consumidor tiene su propia cola SQS suscrita al topic, con un filtro que solo deja pasar los tipos que le interesan.

```
 ms_reservas ─┐                         ┌─► notificaciones-q ─► ms_notificaciones
              ├─► outbox ─► SNS topic ──┼─► reservas-q       ─► ms_reservas
 ms_pago     ─┘   (BD)    staysync-     ├─► habitaciones-q   ─► ms_habitaciones
                          eventos       ├─► servicios-q      ─► ms_servicios
                                        └─► pagos-q          ─► ms_pago
```

La librería común es `StaySync_Commons/staysync-eventos`. Incluye el sobre de los eventos, el outbox, el relay a SNS y el procesador idempotente para consumidores. Los servicios que la usan son `ms_reservas`, `ms_pago`, `ms_habitaciones`, `ms_notificaciones` y `ms_servicios`. `ms_usuarios`, `ms_OTA` y `ms_BFF` no participan.

## Sobre del evento

Todos los eventos usan el record `EventoDominio`. El cuerpo del mensaje es su JSON:

```json
{
  "eventId": "3f1c9a2e-7b4d-4e8a-9c1f-2d6b8e0a5f47",
  "tipo": "ReservaConfirmada",
  "version": 1,
  "tenantId": "hotel-andes",
  "ocurridoEn": "2026-11-04T17:35:21.123Z",
  "payload": { "reservaId": 42, "...": "..." }
}
```

| Campo | Tipo | Significado |
|---|---|---|
| `eventId` | String (UUID) | Identificador único. Los consumidores lo usan para descartar duplicados. |
| `tipo` | String | Uno de los tipos de [Catálogo](#catálogo-de-eventos). SNS filtra por él. |
| `version` | int | Versión del payload, para evolucionarlo sin romper consumidores. Hoy siempre vale `1`, y ningún consumidor la comprueba todavía. |
| `tenantId` | String | Hotel al que pertenece el evento. Debe cumplir `^[a-z0-9][a-z0-9-]{1,63}$`. |
| `ocurridoEn` | Instant | Momento en que ocurrió el hecho. |
| `payload` | objeto | Datos del evento. Depende del tipo. |

Cada mensaje SNS lleva además dos **atributos de mensaje** (`DataType: String`):

- `tipo`: el mismo valor que el campo `tipo`. Las suscripciones filtran por este atributo.
- `tenantId`: el hotel.

Las suscripciones usan `RawMessageDelivery=true`, así que el cuerpo del mensaje SQS es directamente el JSON del evento. Si llegara envuelto por SNS (con `Message` y `TopicArn`), el procesador lo desempaqueta igual.

## Catálogo de eventos

Las constantes están en `TiposEvento`.

| Tipo | Publica | Cuándo |
|---|---|---|
| `ReservaCreada` | ms_reservas | Se crea una reserva en estado `PENDIENTE` (falta el pago). `POST /api/v1/reservas`. |
| `ReservaConfirmada` | ms_reservas | La reserva pasa a `CONFIRMADA`: por cambio de estado manual o automáticamente al recibir `PagoEfectuado` si seguía `PENDIENTE`. |
| `ReservaCancelada` | ms_reservas | La reserva pasa a `CANCELADA`: cancelación del huésped (`PATCH /api/v1/reservas/{id}/cancelar` o `DELETE /api/v1/reservas/{id}`), cambio de estado manual o cierre automático de reservas `PENDIENTE` vencidas (cada hora y al arrancar ms_reservas). |
| `ReservaCheckin` | ms_reservas | La reserva pasa a `CHECKIN`. |
| `ReservaCheckout` | ms_reservas | La reserva pasa a `CHECKOUT`. |
| `PagoEfectuado` | ms_pago | Pago cobrado (`COMPLETADO`), en recepción (`POST /api/v1/pagos`) o por Stripe al confirmar una sesión pagada. |
| `ReembolsoEfectuado` | ms_pago | Pago marcado como `REEMBOLSADO`: reembolso manual (`POST /api/v1/pagos/{pagoId}/reembolso`) o automático al recibir `ReservaCancelada`, con un evento por cada pago. Solo cambia el estado en la base de datos: no devuelve el dinero en Stripe ni en caja. |

Los siguientes cambios **no** publican evento:

- El paso de `CONFIRMADA` a `NO_SHOW`.
- La modificación de una reserva (fechas, habitación o precio).

En consecuencia, los consumidores no se enteran de ellos. La tarea de aseo conserva la habitación y la fecha de salida originales, y en un `NO_SHOW` queda `PROGRAMADA`. Tampoco se envía email al huésped.

### Payload de los eventos de reserva

Todos los tipos `Reserva*` comparten el mismo payload (`ReservaService.publicar`):

| Clave | Tipo JSON | Notas |
|---|---|---|
| `reservaId` | número | |
| `codigo` | string | `RES-` seguido de 8 caracteres |
| `usuarioId` | número | |
| `habitacionId` | número | |
| `fechaEntrada` | string | `yyyy-MM-dd` |
| `fechaSalida` | string | `yyyy-MM-dd` |
| `precioTotal` | número | Decimal. No tiene un número fijo de decimales (en `ReservaCreada` suele llevar 1 y en el resto 2): no dependas de ello. |
| `estado` | string | Estado tras el cambio, que siempre corresponde al tipo: `PENDIENTE` (en `ReservaCreada`), `CONFIRMADA`, `CANCELADA`, `CHECKIN` o `CHECKOUT` |
| `huespedEmail` | string | Opcional: solo si la reserva lo tiene |
| `huespedNombre` | string | Opcional: solo si la reserva lo tiene |

### Payload de los eventos de pago

`PagoEfectuado` y `ReembolsoEfectuado` comparten el mismo payload (`PagoEventos`):

| Clave | Tipo JSON | Notas |
|---|---|---|
| `pagoId` | número | |
| `referencia` | string | `PAY-…` (recepción) o `STRIPE-…` (Stripe) |
| `reservaId` | número | |
| `usuarioId` | número | |
| `monto` | número | Importe. El número de decimales varía: entero en los pagos de Stripe, el valor recibido en los de recepción y 2 decimales en `ReembolsoEfectuado`. No dependas de ello. |
| `moneda` | string | Código de 3 letras (en la práctica `CLP`) |

## Colas y consumidores

`scripts/mensajeria/crear-mensajeria.sh` crea las colas y suscripciones:

| Cola | DLQ | Tipos que recibe | Servicio |
|---|---|---|---|
| `notificaciones-q` | `notificaciones-dlq` | Los 7 tipos | ms_notificaciones |
| `reservas-q` | `reservas-dlq` | `PagoEfectuado` | ms_reservas |
| `habitaciones-q` | `habitaciones-dlq` | `ReservaCheckin`, `ReservaCheckout` | ms_habitaciones |
| `servicios-q` | `servicios-dlq` | `ReservaConfirmada`, `ReservaCancelada`, `ReservaCheckout` | ms_servicios |
| `pagos-q` | `pagos-dlq` | `ReservaCancelada` | ms_pago |

Las 5 colas principales tienen `VisibilityTimeout=60` s y `maxReceiveCount=3`: tras 3 intentos fallidos (uno cada ~60 s) el mensaje pasa a la DLQ. Su política da permiso de escritura al topic `staysync-eventos`. Las DLQ se crean con los valores por defecto de SQS. Estos valores se cambian con las variables `TOPIC`, `VISIBILITY` y `MAX_RECEIVE` del script.

Qué hace cada consumidor:

- **ms_reservas**: `PagoEfectuado` confirma la reserva si sigue `PENDIENTE`. A su vez, eso publica `ReservaConfirmada`.
- **ms_pago**: `ReservaCancelada` marca como `REEMBOLSADO` todos los pagos `COMPLETADO` de la reserva y publica un `ReembolsoEfectuado` por cada uno. La devolución real del dinero (en Stripe o en caja) se hace a mano.
- **ms_habitaciones**: `ReservaCheckin` marca la habitación `OCUPADA` y `ReservaCheckout` la marca `DISPONIBLE`. En los dos casos avisa a OTA por HTTP, sin garantía de entrega: si OTA falla, solo queda un aviso en el log y el evento se da por procesado igualmente.
- **ms_servicios**: `ReservaConfirmada` programa el aseo para la fecha de salida. `ReservaCancelada` cancela el aseo programado. `ReservaCheckout` lo deja `PENDIENTE`.
- **ms_notificaciones**: los eventos de reserva guardan el contacto del huésped (email y nombre) y envían el email correspondiente. Los eventos de pago envían el email usando el contacto guardado antes. Por eso, si nunca llegó un evento de reserva con email, el email de pago no se envía (solo queda un aviso en el log). Si el envío por SMTP falla, el correo queda `PENDIENTE` y un job lo reintenta (ver [Configuración](#configuración)).

Flujo típico de una reserva pagada por Stripe:

```
POST /reservas ─► ReservaCreada ─────────────► notificaciones (email "reserva creada")
pago Stripe    ─► PagoEfectuado ──┬──────────► notificaciones (email "pago recibido")
                                  └► reservas ─► ReservaConfirmada ─┬► notificaciones
                                                                    └► servicios (programa aseo)
```

## Garantías

**Publicación: outbox transaccional.** El servicio no publica en SNS directamente. `Outbox.registrar(...)` inserta el evento en la tabla `outbox_eventos` de su propia base de datos, dentro de la misma transacción que guarda el cambio de negocio. Por eso debe llamarse desde un método `@Transactional`. Si no hay transacción, el evento se guarda igualmente, pero en autocommit y sin atarlo al cambio de negocio, y se registra un aviso en el log. Si la transacción se revierte, el evento no existe. Si se confirma, el evento se publica aunque SNS esté caído: el relay lo reintenta hasta que SNS vuelva.

**Relay.** `OutboxRelay` revisa la tabla cada 2 s (`staysync.eventos.relay-ms`). En cada pasada envía, por orden de inserción, hasta 50 eventos pendientes. Ningún evento se abandona. Si un envío falla, incrementa `intentos` y guarda `ultimo_error`, y lo que hace después depende del tipo de error:

- **Error general** (SNS caído, timeout, credenciales caducadas, throttling, un 5xx, topic inexistente o mal configurado): corta la pasada y **pausa el relay** con espera creciente (2 s, 4 s, 8 s… hasta **1 minuto**). Los eventos no se marcan, así que cuando SNS vuelve el relay sigue desde el más antiguo y **conserva el orden**. Cada 10 fallos seguidos registra un `ERROR` en el log.
- **SNS rechaza ese mensaje** (`InvalidParameter` sobre el propio mensaje, por ejemplo uno de más de 256 KB): aplaza solo ese evento (`proximo_intento`, con la misma espera creciente) y sigue con los demás, para que un evento defectuoso no bloquee al resto.

Cuando SNS vuelve tras una caída, los eventos atrasados salen en menos de un minuto.

**Orden.** El relay publica en orden de inserción, también después de una caída de SNS; solo un evento que SNS rechaza queda atrás. Aun así, el topic y las colas son estándar (no FIFO) y SQS puede entregar en otro orden: los consumidores no deben depender del orden de llegada.

**Entrega al menos una vez.** SQS puede entregar un mensaje más de una vez, y con varias instancias del servicio el relay también puede publicar un evento dos veces. Los consumidores deben ser idempotentes.

**Consumo idempotente.** `ProcesadorEventos.procesar(mensaje, manejadores)`:

1. Lee el evento. Si el mensaje no es un evento válido, lanza una excepción.
2. Si el servicio no tiene manejador para ese tipo, ignora el mensaje sin marcarlo.
3. Valida el `tenantId`. Si no es válido, lanza una excepción.
4. En una transacción, inserta el `eventId` en `eventos_procesados` y ejecuta el manejador con `TenantContext` fijado al hotel del evento. Las consultas del manejador quedan así filtradas por ese hotel.
5. Si el `eventId` ya estaba registrado, ignora el mensaje como duplicado.
6. Si el manejador lanza una excepción, la transacción se revierte y el `eventId` no queda marcado.

En todos los casos de excepción, SQS reintenta el mensaje y, tras 3 fallos, lo mueve a la DLQ.

Además, los manejadores de ms_pago, ms_servicios, ms_reservas y ms_habitaciones están escritos para que repetir un evento no tenga efecto. Por ejemplo, no se reembolsa un pago ya reembolsado ni se duplican tareas de aseo. ms_notificaciones depende solo de `eventos_procesados`: si su transacción falla después de enviar el email, el reintento lo vuelve a enviar.

Las tablas `outbox_eventos`, `eventos_procesados` y `eventos_migraciones` las crea la librería al arrancar (`EsquemaEventos`, con `CREATE TABLE IF NOT EXISTS`) en la base de datos de cada servicio.

## Configuración

| Propiedad | Variable de entorno | Valor por defecto | Efecto |
|---|---|---|---|
| `staysync.eventos.publicar` | `EVENTOS_ENABLED` | `false` | Activa el relay. Con `false`, los eventos se acumulan en el outbox y se publican cuando se active. |
| `spring.cloud.aws.sqs.enabled` | `EVENTOS_ENABLED` | `false` | Activa el consumo de la cola SQS. |
| `staysync.eventos.topic-arn` | `EVENTOS_TOPIC_ARN` | `arn:aws:sns:us-east-1:000000000000:staysync-eventos` | Topic al que publica el relay. El valor por defecto solo sirve en LocalStack. |
| `staysync.eventos.cola` | `EVENTOS_COLA` | `<servicio>-q` | Cola que escucha el `@SqsListener` del servicio. |
| `staysync.eventos.relay-ms` | — | `2000` | Intervalo del relay, en ms. |
| `spring.cloud.aws.region.static` | `AWS_REGION` | `us-east-1` | |
| `notificaciones.reintentos.espera-base` | `MAIL_REINTENTO_ESPERA` | `5m` | Solo ms_notificaciones. Espera antes de reintentar un correo fallido; se duplica en cada fallo (5, 10, 20, 40 min). Al agotar `max_intentos` (5 en los correos nuevos, envío inicial incluido; los creados antes conservan 3) el correo queda `FALLIDO`. |
| `notificaciones.reintentos.revision-ms` | — | `60000` | Solo ms_notificaciones. Cada cuánto busca el job correos para reintentar. |
| `notificaciones.reintentos.caducidad` | — | `24h` | Solo ms_notificaciones. Un correo que lleva más que esto en `PENDIENTE` ya no se envía: queda `FALLIDO` con `error_msg` "Caducado sin enviar". |
| `spring.cloud.aws.endpoint` | `SPRING_CLOUD_AWS_ENDPOINT` | — (endpoint real de AWS) | Solo para LocalStack (`http://localstack:4566`). En AWS no se define. |
| `spring.cloud.aws.credentials.access-key` / `secret-key` | `SPRING_CLOUD_AWS_CREDENTIALS_ACCESS_KEY` / `..._SECRET_KEY` | — (credenciales por defecto del SDK) | `test`/`test` en LocalStack. En ECS se usan las del rol de la tarea. |

Con los valores por defecto (tests, ejecución local sin Docker) la mensajería está apagada.

## Entornos

**Local (docker compose).** El ancla `x-eventos` de `docker-compose.yml` activa la mensajería (`EVENTOS_ENABLED=true`) y apunta los servicios a LocalStack (`http://localstack:4566`, credenciales `test`/`test`). Cuando LocalStack tiene SNS y SQS listos, su hook `infra/localstack/ready.d/01-mensajeria.sh` ejecuta `crear-mensajeria.sh` con `awslocal` y crea el fichero marcador `/tmp/staysync-mensajeria-lista`. El healthcheck espera ese fichero, y los servicios con eventos dependen de que LocalStack esté sano. El puerto 4566 no se publica en el host. Para inspeccionar:

```bash
docker exec staysync-localstack awslocal sqs list-queues
docker exec staysync-localstack awslocal sqs receive-message --queue-url http://localhost:4566/000000000000/notificaciones-dlq
```

**AWS.** El topic y las colas se crean a mano, por ejemplo desde CloudShell:

```bash
AWS_CMD=aws AWS_REGION=us-east-1 bash scripts/mensajeria/crear-mensajeria.sh
```

El script es idempotente mientras no cambien los filtros (ver [Limitaciones](#limitaciones-conocidas)). El rol de la tarea de ECS necesita permiso `sns:Publish` sobre el topic y permisos para leer y borrar mensajes de su cola. En las task definitions hay que definir `EVENTOS_ENABLED=true` y `EVENTOS_TOPIC_ARN` con el ARN real del topic. El CI no los define (`task_definition.py` conserva el `environment` que ya tenga cada task definition).

## Añadir un evento nuevo

1. Declarar la constante en `TiposEvento`.
2. En el servicio que publica, llamar a `outbox.registrar(tipo, tenantId, payload)` dentro de la transacción que guarda el cambio.
3. En cada servicio consumidor, añadir el manejador al mapa `manejadores()` de su listener. El manejador debe ser idempotente.
4. Añadir el tipo al filtro de cada cola consumidora en `crear-mensajeria.sh`, incluida `notificaciones-q` si debe enviar email. En local, reiniciar LocalStack para que el hook vuelva a crear las suscripciones. En AWS, actualizar la `FilterPolicy` de cada suscripción existente con `aws sns set-subscription-attributes` (volver a ejecutar el script no basta).
5. Documentarlo en este fichero.

Para un cambio incompatible en el payload de un tipo existente, incrementar `version`. Como hoy ningún consumidor comprueba `version`, antes hay que hacer que los manejadores distingan las versiones y mantener el soporte de la anterior mientras convivan.

## Limitaciones conocidas

- No hay limpieza de `outbox_eventos` ni de `eventos_procesados`: las tablas crecen indefinidamente.
- Un evento que SNS rechaza siempre (por ejemplo, por superar los 256 KB) no bloquea a los demás, pero se reintenta indefinidamente, una vez por minuto. Solo deja rastro en el log (`ultimo_error` y un `ERROR` cada 10 intentos): no hay alarma.
- **Eventos aparcados.** Al actualizar desde la versión anterior del relay, que abandonaba un evento tras 10 fallos, esos eventos abandonados no se publican solos: podrían tener semanas y aplicar cambios fuera de contexto (por ejemplo, un check-in viejo dejaría ocupada una habitación libre). Quedan con `proximo_intento = 2037-12-31` y un aviso en el log al arrancar. El aparcado se hace una sola vez por base de datos y queda registrado en la tabla `eventos_migraciones`. Para revisarlos: `SELECT * FROM outbox_eventos WHERE publicado_en IS NULL AND proximo_intento >= '2037-01-01'`. Para publicar uno: `UPDATE outbox_eventos SET proximo_intento = NULL WHERE id = ...`. Si no aplica, bórralo.
- Las DLQ no tienen alarmas y usan la retención por defecto de SQS (4 días): un mensaje que nadie revise en ese plazo se pierde.
- Si se cambian los tipos de una cola ya creada, volver a ejecutar `crear-mensajeria.sh` en AWS falla en esa cola: `sns subscribe` rechaza la suscripción existente porque tiene otros atributos, y el script se detiene sin procesar las colas siguientes. El filtro hay que actualizarlo a mano con `aws sns set-subscription-attributes --subscription-arn <arn> --attribute-name FilterPolicy --attribute-value '{"tipo":[...]}'`. En LocalStack no ocurre, porque el hook crea todo desde cero al arrancar.
- ms_notificaciones no relanza los errores SMTP, así que un email fallido no pasa por la DLQ. El job de reintentos lo reenvía. Si la caída de SMTP dura más que la suma de las esperas (~75 min con los valores por defecto), o el correo lleva más de 24 h sin enviarse, queda `FALLIDO` y no hay alarma. Al desplegar esta versión, los correos atascados en `PENDIENTE` de las últimas 24 h se reintentan y los más antiguos pasan a `FALLIDO`.
- Los reembolsos (`ReembolsoEfectuado`) solo cambian el estado del pago: no hay integración con la API de reembolsos de Stripe.
- Si un pago llega cuando la reserva ya está `CANCELADA` (por ejemplo, una sesión de Stripe confirmada después de que la tarea horaria cerrara la reserva vencida), ms_reservas ignora el `PagoEfectuado`. El pago queda `COMPLETADO` sin reembolso automático, porque `ReservaCancelada` se procesó antes de que existiera el pago. Hay que revisarlo a mano.
