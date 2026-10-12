# staysync-tenant

Librería compartida con el hotel (tenant) de cada petición. La usan los microservicios desde la Etapa 2.

## Cómo llega el hotel a un microservicio

1. MS Usuarios pone `tenantId` dentro del JWT al hacer login.
2. El BFF valida el JWT y llama al microservicio con el header `X-Tenant-Id`. Solo lo manda a servicios internos y reemplaza cualquier valor que venga del cliente.
3. `TenantFilter` (de esta librería) lee el header y lo deja en `TenantContext` y en el MDC de los logs.

## Uso en un servicio

```java
String tenantId = TenantContext.require();      // lanza TenantNotResolvedException si no hay hotel
TenantContext.get();                             // Optional<String>, para código que acepta "sin hotel"

CompletableFuture.supplyAsync(TenantContext.wrap(() -> repo.buscar()), pool);   // tareas en otro hilo
TenantContext.ejecutarCon(mensaje.tenantId(), () -> procesar(mensaje));        // consumidores SQS
```

`TenantPropagationInterceptor` reenvía el hotel en llamadas HTTP entre microservicios (por ejemplo, Habitaciones → OTA). Regístralo solo en un `RestTemplate` que apunte a servicios internos.

El filtro se registra solo al agregar la dependencia. Se puede apagar con `staysync.tenant.filter.enabled=false`.

## Agregarla a un servicio (Etapa 2)

Hoy cada Dockerfile compila con el contexto de su propia carpeta y no ve `StaySync_Commons`. Para usarla:

1. En el `pom.xml` del servicio:
   ```xml
   <dependency>
       <groupId>com.staysync</groupId>
       <artifactId>staysync-tenant</artifactId>
       <version>1.0.0</version>
   </dependency>
   ```
2. En el Dockerfile, instalar la librería antes de compilar el servicio:
   ```dockerfile
   COPY StaySync_Commons/staysync-tenant /commons
   RUN mvn -f /commons/pom.xml -q install -DskipTests
   ```
3. Cambiar el contexto de build a la raíz del repo, en `docker-compose.yml` y en `docker build` del CI. Ajustar los demás `COPY` del Dockerfile a rutas desde la raíz.
4. En el CI, antes de `mvn test` del servicio: `mvn -f StaySync_Commons/staysync-tenant/pom.xml -q install -DskipTests`.

## Tests

```bash
mvn test
```
