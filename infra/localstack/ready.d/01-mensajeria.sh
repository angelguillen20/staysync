#!/bin/bash
# Hook de LocalStack (se ejecuta cuando SNS y SQS están listos): crea el topic, las colas
# y las suscripciones con el mismo script que se usa en AWS.
set -e
AWS_CMD=awslocal bash /opt/staysync/crear-mensajeria.sh
# Marca para el healthcheck de docker-compose: los servicios arrancan después de esto
touch /tmp/staysync-mensajeria-lista
