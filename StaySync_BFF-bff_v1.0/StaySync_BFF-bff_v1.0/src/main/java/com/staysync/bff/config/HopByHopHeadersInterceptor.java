source ~/staysync-academy/helpers.sh
JS=$(curl -s $API_URL/ | grep -o 'assets/[^"]*\.js' | head -1)
echo "1. Front (HTTPS):        $(curl -s -o /dev/null -w '%{http_code}' $API_URL/)"
echo "2. JS principal:         $(curl -s -o /dev/null -w '%{http_code} %{content_type}' $API_URL/$JS)"
[ "$(curl -s $API_URL/ | md5sum)" = "$(curl -s $API_URL/auth/callback | md5sum)" ] && echo "3. Ruta SPA:             OK" || echo "3. Ruta SPA:             FALLA"
echo "4. Marca del hotel demo: $(curl -s -o /dev/null -w '%{http_code}' $API_URL/bff/public/hoteles/staysync-demo/theme)"
curl -s -o /tmp/login.json -w "5. Login SUPER_ADMIN:    %{http_code}\n" -X POST $API_URL/bff/auth/login \
  -H 'Content-Type: application/json' -d '{"email":"superadmin@staysync.com","password":"Super@1234"}'
echo "6. Interna sin token:    $(curl -s -o /dev/null -w '%{http_code}' -H 'X-Tenant-Id: staysync-demo' $API_URL/api/v1/habitaciones)"
SECRET=$(aws cognito-idp describe-user-pool-client --user-pool-id $USER_POOL_ID --client-id $M2M_CLIENT_ID --query UserPoolClient.ClientSecret --output text)
TOKEN=$(curl -s -u "$M2M_CLIENT_ID:$SECRET" -d 'grant_type=client_credentials&scope=staysync-api/interno' $COGNITO_DOMAIN/oauth2/token | jq -r .access_token)
echo "7. Interna con token:    $(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" -H 'X-Tenant-Id: staysync-demo' $API_URL/api/v1/habitaciones)"
for Q in notificaciones reservas habitaciones servicios pagos; do
  U=$(aws sqs get-queue-url --queue-name $Q-dlq --query QueueUrl --output text)
  echo "8. DLQ $Q: $(aws sqs get-queue-attributes --queue-url $U --attribute-names ApproximateNumberOfMessages --query Attributes.ApproximateNumberOfMessages --output text)"
done
unset SECRET TOKEN
