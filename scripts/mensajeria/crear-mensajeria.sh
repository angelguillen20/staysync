#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# StaySync — mensajería SNS + SQS
#
# Crea el topic "staysync-eventos", una cola SQS por servicio consumidor (cada una con
# su DLQ) y las suscripciones con filtro por el atributo "tipo". Es idempotente: se
# puede volver a ejecutar sin duplicar nada.
#
#   LocalStack (automático): docker-compose lo ejecuta al arrancar con AWS_CMD=awslocal
#   AWS (manual, p. ej. en CloudShell):
#       AWS_CMD=aws AWS_REGION=us-east-1 bash scripts/mensajeria/crear-mensajeria.sh
#
# Contrato de eventos: docs/eventos.md
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

AWS_CMD="${AWS_CMD:-aws}"
TOPIC="${TOPIC:-staysync-eventos}"
MAX_RECEIVE="${MAX_RECEIVE:-3}"        # intentos antes de mandar el mensaje a la DLQ
VISIBILITY="${VISIBILITY:-60}"         # segundos que un mensaje queda oculto mientras se procesa
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

topic_arn=$($AWS_CMD sns create-topic --name "$TOPIC" --query TopicArn --output text)
echo "Topic: $topic_arn"

# crear_cola <cola> <tipos de evento separados por espacio>
crear_cola() {
  local cola="$1" tipos="$2"
  local dlq="${cola%-q}-dlq"

  local dlq_url dlq_arn url arn
  dlq_url=$($AWS_CMD sqs create-queue --queue-name "$dlq" --query QueueUrl --output text)
  dlq_arn=$($AWS_CMD sqs get-queue-attributes --queue-url "$dlq_url" \
      --attribute-names QueueArn --query Attributes.QueueArn --output text)
  url=$($AWS_CMD sqs create-queue --queue-name "$cola" --query QueueUrl --output text)
  arn=$($AWS_CMD sqs get-queue-attributes --queue-url "$url" \
      --attribute-names QueueArn --query Attributes.QueueArn --output text)

  # DLQ tras MAX_RECEIVE fallos y permiso para que SNS (solo este topic) escriba en la cola
  cat > "$TMP/cola.json" <<EOF
{
  "VisibilityTimeout": "$VISIBILITY",
  "RedrivePolicy": "{\"deadLetterTargetArn\":\"$dlq_arn\",\"maxReceiveCount\":\"$MAX_RECEIVE\"}",
  "Policy": "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"sns.amazonaws.com\"},\"Action\":\"sqs:SendMessage\",\"Resource\":\"$arn\",\"Condition\":{\"ArnEquals\":{\"aws:SourceArn\":\"$topic_arn\"}}}]}"
}
EOF
  $AWS_CMD sqs set-queue-attributes --queue-url "$url" --attributes "file://$TMP/cola.json"

  # Filtro: la cola solo recibe los tipos que su servicio consume.
  # RawMessageDelivery: el cuerpo del mensaje SQS es directamente el evento (sin sobre de SNS).
  local lista=""
  for t in $tipos; do lista="$lista\\\"$t\\\","; done
  lista="${lista%,}"
  cat > "$TMP/suscripcion.json" <<EOF
{
  "RawMessageDelivery": "true",
  "FilterPolicy": "{\"tipo\":[$lista]}"
}
EOF
  $AWS_CMD sns subscribe --topic-arn "$topic_arn" --protocol sqs --notification-endpoint "$arn" \
      --attributes "file://$TMP/suscripcion.json" --query SubscriptionArn --output text > /dev/null

  echo "Cola $cola (DLQ $dlq) <- $tipos"
}

crear_cola notificaciones-q "ReservaCreada ReservaConfirmada ReservaCancelada ReservaCheckin ReservaCheckout PagoEfectuado ReembolsoEfectuado"
crear_cola reservas-q       "PagoEfectuado"
crear_cola habitaciones-q   "ReservaCheckin ReservaCheckout"
crear_cola servicios-q      "ReservaConfirmada ReservaCancelada ReservaCheckout"
crear_cola pagos-q          "ReservaCancelada"

echo "Mensajería lista."
