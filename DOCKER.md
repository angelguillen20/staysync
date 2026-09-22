# Docker — StaySync

Guía completa para construir, levantar y desplegar todos los contenedores de StaySync.

---

## Arquitectura de contenedores

```
Internet
   │
   ▼
┌──────────┐     puerto 3000
│ Frontend │ (React 18 + Nginx)
│ :80      │
└────┬─────┘
     │ API calls → /api/*
     ▼
┌──────────┐     puerto 8080
│   BFF    │ (Spring Boot — API Gateway / JWT)
│ :8080    │
└────┬─────┘
     │ HTTP interno (red Docker)
     ├──► usuarios      :8081  ←→  mysql-usuarios
     ├──► habitaciones  :8082  ←→  mysql-habitaciones
     ├──► reservas      :8083  ←→  mysql-reservas  ←→  RabbitMQ
     ├──► servicios     :8084  ←→  mysql-servicios
     └──► pagos         :8085  ←→  mysql-pagos  ←→  Stripe

                   notificaciones :8086  ←→  mysql-notificaciones
                                          ←→  RabbitMQ (consumer)
                                          ←→  SMTP (JavaMail)

                   ota            :8087  ←→  mysql-ota
```

---

## Estructura de Dockerfiles

| Servicio | Dockerfile | Puerto | Base runtime |
|---|---|---|---|
| BFF | `StaySync_BFF-bff_v1.0/.../Dockerfile` | 8080 | `eclipse-temurin:17-jre-alpine` |
| Usuarios | `StaySync_Usuarios-usuarios_v1.0/.../Dockerfile` | 8081 | `eclipse-temurin:17-jre-alpine` |
| Habitaciones | `StaySync_Habitaciones-habitaciones_v1.0/.../Dockerfile` | 8082 | `eclipse-temurin:17-jre-alpine` |
| Reservas | `StaySync_Reservas-reservas_v1.1/.../Dockerfile` | 8083 | `eclipse-temurin:17-jre-alpine` |
| Servicios | `StaySync_Servicios-servicios_v1.0/.../Dockerfile` | 8084 | `eclipse-temurin:17-jre-alpine` |
| Pagos | `StaySync_Pago-pagos_v1.0/.../Dockerfile` | 8085 | `eclipse-temurin:17-jre-alpine` |
| Notificaciones | `StaySync_Notificaciones-notificaciones_v1.0/.../Dockerfile` | 8086 | `eclipse-temurin:17-jre-alpine` |
| OTA | `StaySync_OTA-ota_v1.0/.../Dockerfile` | 8087 | `eclipse-temurin:17-jre-alpine` |
| Frontend | `Frontend-StaySync-.../Dockerfile` | 80 | `nginx:1.27-alpine` |

### Patrón multi-stage (Java)

Todos los microservicios Java usan el mismo patrón de dos etapas:

```dockerfile
# Etapa 1 — compilación (imagen pesada, descartada al final)
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q          # capa cacheada
COPY src ./src
RUN mvn package -DskipTests -q

# Etapa 2 — runtime (imagen final ~200 MB vs ~500 MB con Maven)
FROM eclipse-temurin:17-jre-alpine AS runtime
RUN addgroup -S staysync && adduser -S staysync -G staysync
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN chown staysync:staysync app.jar
USER staysync                              # nunca root en producción
EXPOSE <puerto>
HEALTHCHECK ...
ENTRYPOINT ["java",
  "-XX:+UseContainerSupport",             # respeta los límites de CPU/RAM del contenedor
  "-XX:MaxRAMPercentage=75.0",            # usa 75% de la RAM asignada (no toda)
  "-Djava.security.egd=file:/dev/./urandom",  # inicio más rápido en Linux
  "-jar", "app.jar"]
```

### Frontend (React + Nginx)

```dockerfile
# Etapa 1 — build con Node
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --silent
COPY . .
RUN npm run build          # genera /app/dist

# Etapa 2 — Nginx sirve los estáticos
FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

El archivo `nginx.conf` incluye:
- `try_files $uri $uri/ /index.html` — SPA fallback para React Router
- Cache agresivo (`immutable`) para assets con hash de Vite
- Gzip activado
- Endpoint `/health` para AWS ALB

---

## Uso local con Docker Compose

### 1. Configurar variables de entorno

```bash
cp .env.example .env
# Editar .env con valores reales
```

Variables obligatorias en `.env`:

```
JWT_SECRET=<cadena aleatoria de mínimo 64 caracteres>
STRIPE_SECRET_KEY=sk_test_...
MAIL_USERNAME=tu_correo@gmail.com
MAIL_PASSWORD=<app password de Gmail>
```

### 2. Levantar todo el stack

```bash
# Primera vez (construye imágenes)
docker compose up --build -d

# Ver logs en tiempo real
docker compose logs -f

# Solo logs de un servicio
docker compose logs -f reservas
```

### 3. Verificar que todo está saludable

```bash
docker compose ps
```

Todos los servicios deben mostrar `healthy` o `running`. Si alguno aparece como `unhealthy`, revisar con:

```bash
docker compose logs <nombre-servicio>
```

### 4. Endpoints disponibles

| URL | Qué es |
|---|---|
| `http://localhost:3000` | Frontend React |
| `http://localhost:8080/actuator/health` | BFF health |
| `http://localhost:8081/actuator/health` | Usuarios health |
| `http://localhost:8082/actuator/health` | Habitaciones health |
| `http://localhost:8083/actuator/health` | Reservas health |
| `http://localhost:8084/actuator/health` | Servicios health |
| `http://localhost:8085/actuator/health` | Pagos health |
| `http://localhost:8086/actuator/health` | Notificaciones health |
| `http://localhost:15672` | RabbitMQ Management (user: staysync) |

### 5. Detener y limpiar

```bash
# Detener sin borrar datos
docker compose down

# Detener Y borrar volúmenes (borra todas las BBDDs)
docker compose down -v
```

---

## Comandos útiles por imagen

```bash
# Reconstruir solo un servicio tras cambios en código
docker compose up --build -d reservas

# Entrar al contenedor (debug)
docker compose exec reservas sh

# Ver variables de entorno de un servicio
docker compose exec reservas env

# Ejecutar SQL en una BD específica
docker compose exec mysql-reservas mysql -u staysync -p reservas_db
```

---

## Despliegue en AWS

### Opción A — EC2 + Docker Compose (recomendado para empezar)

1. **Lanzar instancia EC2**
   - AMI: Amazon Linux 2023
   - Tipo: `t3.medium` mínimo (8 servicios + MySQL + RabbitMQ)
   - Abrir puertos: 22 (SSH), 80, 443, 3000, 8080-8087

2. **Instalar Docker**
   ```bash
   sudo dnf install -y docker
   sudo systemctl enable --now docker
   sudo usermod -aG docker ec2-user
   # Instalar Compose plugin
   sudo mkdir -p /usr/local/lib/docker/cli-plugins
   sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
     -o /usr/local/lib/docker/cli-plugins/docker-compose
   sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
   ```

3. **Subir código al servidor**
   ```bash
   # Desde tu máquina local
   rsync -avz --exclude='node_modules' --exclude='target' \
     ./ ec2-user@<IP_EC2>:/home/ec2-user/staysync/
   ```

4. **Configurar .env en el servidor**
   ```bash
   ssh ec2-user@<IP_EC2>
   cd /home/ec2-user/staysync
   nano .env   # pegar variables reales
   ```

5. **Levantar el stack**
   ```bash
   docker compose up --build -d
   ```

### Opción B — ECS Fargate (producción real)

Para ECS necesitás un registro de imágenes (Amazon ECR). Por cada servicio:

```bash
# Autenticarse en ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin <account>.dkr.ecr.us-east-1.amazonaws.com

# Build y push (ejemplo para reservas)
docker build -t staysync-reservas \
  ./StaySync_Reservas-reservas_v1.1/StaySync_Reservas-reservas_v1.1

docker tag staysync-reservas:latest \
  <account>.dkr.ecr.us-east-1.amazonaws.com/staysync-reservas:latest

docker push <account>.dkr.ecr.us-east-1.amazonaws.com/staysync-reservas:latest
```

Luego crear Task Definitions y Services en ECS apuntando a esas imágenes. Las variables de entorno se pasan vía **AWS Secrets Manager** o **Parameter Store**, nunca en el código.

### Variables de entorno en AWS (Secrets Manager)

```bash
# Crear secreto para Stripe
aws secretsmanager create-secret \
  --name "staysync/prod/stripe-secret-key" \
  --secret-string "sk_live_..."

# Crear secreto para JWT
aws secretsmanager create-secret \
  --name "staysync/prod/jwt-secret" \
  --secret-string "<cadena aleatoria>"
```

En ECS Task Definition, referenciar los secretos:
```json
{
  "secrets": [
    {
      "name": "STRIPE_SECRET_KEY",
      "valueFrom": "arn:aws:secretsmanager:us-east-1:xxx:secret:staysync/prod/stripe-secret-key"
    }
  ]
}
```

---

## Seguridad

- **Nunca hardcodear credenciales** en Dockerfiles ni en `application.yml`
- El usuario que ejecuta el proceso Java dentro del contenedor es `staysync` (no root)
- `.env` está en `.gitignore` — solo `.env.example` se sube a git
- `STRIPE_SECRET_KEY` sin valor por defecto — la app falla al arrancar si no está definida (intencional)
- En producción usar HTTPS: poner un ALB (Application Load Balancer) de AWS delante del frontend y el BFF

---

## Troubleshooting

| Síntoma | Causa probable | Solución |
|---|---|---|
| Servicio en `unhealthy` | MySQL aún iniciando | Esperar 30-60s al primer `up --build` |
| `Connection refused` entre servicios | Nombre de host incorrecto | Usar el nombre del `container_name` en la URL |
| Frontend muestra 404 en refresh | SPA fallback no configurado | Verificar `nginx.conf` con `try_files` |
| Stripe error `No API key provided` | `STRIPE_SECRET_KEY` no está en `.env` | Agregar la variable y reiniciar pagos |
| OOM del contenedor Java | RAM insuficiente en EC2 | Subir a `t3.large` o ajustar `MaxRAMPercentage` |
