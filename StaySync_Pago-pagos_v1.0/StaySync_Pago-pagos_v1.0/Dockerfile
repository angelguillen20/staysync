# ─────────────────────────────────────────────────────────────────────────────
# StaySync — pagos-service  |  Puerto 8085
# ─────────────────────────────────────────────────────────────────────────────

# ── Etapa 1: Compilación ──────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build

# Librerías compartidas (StaySync_Commons): hotel por petición y eventos SNS/SQS
COPY StaySync_Commons /commons
RUN mvn -q -f /commons/pom.xml install -DskipTests

WORKDIR /app

# El contexto de build es la raíz del repo (docker build -f StaySync_Pago-pagos_v1.0/StaySync_Pago-pagos_v1.0/Dockerfile .)
COPY StaySync_Pago-pagos_v1.0/StaySync_Pago-pagos_v1.0/pom.xml .
RUN mvn dependency:go-offline -q

COPY StaySync_Pago-pagos_v1.0/StaySync_Pago-pagos_v1.0/src ./src
RUN mvn package -DskipTests -q

# ── Etapa 2: Imagen de ejecución ──────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S staysync && adduser -S staysync -G staysync

WORKDIR /app

COPY --from=build /app/target/pagos-service-1.0.0.jar app.jar
RUN chown staysync:staysync app.jar

USER staysync

EXPOSE 8085

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8085/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
