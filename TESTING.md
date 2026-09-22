# StaySync — Documentación de Testing

**Proyecto:** StaySync Hotel Management System  
**Stack:** Spring Boot 3.4.1 · JUnit 5 · Mockito 5 · AssertJ · JaCoCo 0.8.12  
**Cobertura mínima exigida:** 60% de líneas por módulo (JaCoCo)  
**Última auditoría:** 2026-06-20

---

## Índice

1. [Estrategia de testing](#1-estrategia-de-testing)
2. [Resumen de cobertura](#2-resumen-de-cobertura)
3. [Herramientas y dependencias](#3-herramientas-y-dependencias)
4. [Tests por microservicio](#4-tests-por-microservicio)
5. [Bugs encontrados y corregidos](#5-bugs-encontrados-y-corregidos)
6. [Cómo ejecutar los tests](#6-cómo-ejecutar-los-tests)
7. [Cobertura con JaCoCo](#7-cobertura-con-jacoco)
8. [Convenciones y buenas prácticas](#8-convenciones-y-buenas-prácticas)
9. [Próximos pasos](#9-próximos-pasos)

---

## 1. Estrategia de testing

StaySync implementa tres niveles de testing:

```
         ┌─────────────────────────┐
         │      E2E / Manual       │  ← Flujo completo usuario real
         ├─────────────────────────┤
         │  Integración            │  ← @SpringBootTest + H2 en memoria
         │  @WebMvcTest            │  ← Capa HTTP sin servidor real
         ├─────────────────────────┤
         │  Unitarios              │  ← @ExtendWith(MockitoExtension)
         └─────────────────────────┘
              (mayoría del proyecto)
```

| Nivel | Velocidad | Confianza | Cuándo usarlo |
|---|---|---|---|
| Unitario | Muy rápido (~ms) | Lógica de negocio aislada | Siempre — son el núcleo |
| Web (`@WebMvcTest`) | Rápido (~100ms) | HTTP status, validación, serialización | Por cada endpoint crítico |
| Integración (`@SpringBootTest`) | Lento (~2-5s) | Repositorio + transacciones + BD real | Flujos completos con persistencia |

---

## 2. Resumen de cobertura

### Total de tests por microservicio

| Microservicio | Unitarios | Controller/@WebMvc | Integración | **Total** |
|---|---|---|---|---|
| **reservas-service** | 21 | 11 | 6 | **38** |
| **bff-service** | 6 | 8 | 0 | **14** |
| **usuarios-service** | 14 | 4 | 0 | **18** |
| **pagos-service** | 12 | 0 | 0 | **12** |
| **habitaciones-service** | 11 | 0 | 0 | **11** |
| **servicios-service** | 9 | 0 | 0 | **9** |
| **notificaciones-service** | 9 | 0 | 0 | **9** |
| **ota-service** | 7 | 0 | 0 | **7** |
| **TOTAL** | **89** | **23** | **6** | **118** |

### Archivos de test (16 en total)

| # | Archivo | Servicio | Tipo | Tests |
|---|---|---|---|---|
| 1 | `ReservaServiceTest` | reservas | Unitario | 21 |
| 2 | `ReservaControllerTest` | reservas | @WebMvcTest | 11 |
| 3 | `ReservaIntegrationTest` | reservas | @SpringBootTest | 6 |
| 4 | `AuthBffControllerTest` | bff | @WebMvcTest | 5 |
| 5 | `DashboardBffControllerTest` | bff | @WebMvcTest | 3 |
| 6 | `JwtServiceTest` | bff | Unitario | 6 |
| 7 | `AuthServiceTest` | usuarios | Unitario | 6 |
| 8 | `UsuarioServiceTest` | usuarios | Unitario | 8 |
| 9 | `AuthControllerTest` | usuarios | @WebMvcTest | 4 |
| 10 | `StripeServiceTest` | pagos | Unitario | 7 |
| 11 | `PagoServiceTest` | pagos | Unitario | 5 |
| 12 | `HabitacionServiceTest` | habitaciones | Unitario | 11 |
| 13 | `ServicioServiceTest` | servicios | Unitario | 9 |
| 14 | `NotificacionServiceTest` | notificaciones | Unitario | 9 |
| 15 | `CanalOtaServiceTest` | ota | Unitario | 4 |
| 16 | `HabitacionEventListenerTest` | ota | Unitario | 3 |

---

## 3. Herramientas y dependencias

Todas presentes en cada `pom.xml` vía `spring-boot-starter-test`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- Para tests de seguridad en BFF y Usuarios -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- Base de datos en memoria para tests de integración -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

### Versiones clave

| Herramienta | Versión | Propósito |
|---|---|---|
| JUnit 5 (Jupiter) | 5.11.x | Framework de tests |
| Mockito | 5.x | Mocks, stubs, MockedStatic |
| AssertJ | 3.26.x | Aserciones fluidas |
| JaCoCo | 0.8.12 | Reporte de cobertura |
| H2 Database | 2.x | BD en memoria para integración |
| MockMvc | (Spring) | Tests de controladores sin servidor |

### Anotación para mocks en Spring Boot 3.4+

```java
// ANTES (deprecada en Spring Boot 3.4)
import org.springframework.boot.test.mock.mockito.MockBean;
@MockBean private ServicioX servicio;

// AHORA (reemplazante oficial)
import org.springframework.test.context.bean.override.mockito.MockitoBean;
@MockitoBean private ServicioX servicio;
```

Todos los archivos del proyecto ya usan `@MockitoBean`.

---

## 4. Tests por microservicio

---

### 4.1 reservas-service

**Directorio:** `StaySync_Reservas-reservas_v1.1/src/test/java/com/staysync/reservas/`

#### ReservaServiceTest.java — 21 tests unitarios

Verifica toda la lógica de negocio del servicio de reservas con dependencias mockeadas.

```
ReservaServiceTest
 ├── crear()
 │    ├── debeCrearReservaConPrecioReal               → precio real desde habitaciones-service
 │    ├── debeCrearReservaConPrecioPorDefecto          → fallback cuando el servicio no responde
 │    ├── debeLanzarExcepcionCapacidadExcedida         → numHuespedes > capacidad → excepción
 │    ├── debeLanzarExcepcionConConflicto              → fechas solapadas → HabitacionNoDisponible
 │    ├── debeGuardarHuespedesAdicionales              → save() llamado 2 veces
 │    └── debeLanzarExcepcionFechasInvalidas           → fechaSalida <= fechaEntrada → excepción
 │
 ├── obtenerPorId() / listar()
 │    ├── debeRetornarReserva                          → happy path
 │    ├── debeLanzarExcepcionNoExiste                  → Optional.empty → ReservaNotFoundException
 │    └── debeListarReservas                           → Page<Reserva> correctamente mapeado
 │
 ├── cambiarEstado()
 │    ├── debeCambiarEstadoPendienteAConfirmada        → publica evento reserva.confirmada
 │    ├── debeCambiarEstadoConfirmadaACheckin          → publica evento reserva.checkin
 │    └── debeLanzarExcepcionTransicionInvalida        → CHECKOUT → CHECKIN → excepción
 │
 ├── cancelar()
 │    ├── debeCancelarReserva                          → > 24h anticipación → OK, publica evento
 │    ├── debeLanzarExcepcionCancelacionFueraDePlazo   → < 24h → CancelacionRestringidaException
 │    ├── debeLanzarExcepcionSiYaCancelada             → estado CANCELADA → TransicionInválida
 │    └── debeLanzarExcepcionSiYaCheckout              → estado CHECKOUT → TransicionInválida
 │
 ├── autoTransicionarACheckin()  (scheduler)
 │    ├── debeAutoTransicionarReservasConfirmadasDeHoy → cambia estado + publica evento por cada una
 │    ├── noDebeHacerNadaSinReservasParaHoy            → lista vacía → save() nunca llamado
 │    └── debeProcessarMultiplesReservasEnAutoCheckin  → 2 reservas → 2 saves + 2 eventos
 │
 └── getReservasHoy()
      ├── debeRetornarReservasDelDia                   → separa pendientesCheckin / pendientesCheckout
      └── debeRetornarListasVaciasConSinReservas       → listas vacías cuando no hay reservas
```

**Dependencias mockeadas:**
- `ReservaRepository` — sin BD real
- `ReservaEventPublisher` — sin RabbitMQ real
- `RestTemplate` — sin llamada a habitaciones-service

---

#### ReservaControllerTest.java — 11 tests de capa web

Usa `@WebMvcTest` para probar solo la capa HTTP. Verifica: status codes, JSON, mapeo de excepciones.

```
ReservaControllerTest (@WebMvcTest + @AutoConfigureMockMvc(addFilters=false))
 ├── POST /api/v1/reservas
 │    ├── debeRetornar201AlCrear                    → 201 + body con código, horaCheckin, horaCheckout
 │    ├── debeRetornar409ConConflicto               → HabitacionNoDisponibleException → 409
 │    ├── debeRetornar400SinHabitacionId            → Bean Validation → 400 + validationErrors
 │    └── debeRetornar400CapacidadExcedida          → IllegalArgumentException → 400
 │
 ├── GET /api/v1/reservas/{id}
 │    ├── debeRetornar200PorId                      → 200 + codigo en body
 │    └── debeRetornar404NoExiste                   → ReservaNotFoundException → 404
 │
 ├── GET /api/v1/reservas/hoy
 │    └── debeRetornarReservasDelDia                → pendientesCheckin[].codigo correcto
 │
 ├── PATCH /api/v1/reservas/{id}/estado
 │    ├── debeRetornar200CambioEstado               → transición válida → 200
 │    └── debeRetornar400TransicionInvalida         → TransicionEstadoInvalidaException → 400
 │
 └── PATCH /api/v1/reservas/{id}/cancelar
      ├── debeRetornar204AlCancelar                 → 204 No Content
      └── debeRetornar422CancelacionFueraDePlazo    → CancelacionRestringidaException → 422 + mensaje
```

---

#### ReservaIntegrationTest.java — 6 tests de integración

`@SpringBootTest` con perfil `test` (H2 en memoria). Cada test es `@Transactional` → rollback automático.

```
ReservaIntegrationTest (@SpringBootTest + @ActiveProfiles("test") + @Transactional)
 ├── debePersistirReserva                    → código único RES-[A-Z0-9]{8}, precio 100*2=200
 ├── debeRechazarReservaPorConflicto         → mismas fechas → HabitacionNoDisponibleException
 ├── debePasarPorTodosLosEstados             → PENDIENTE→CONFIRMADA→CHECKIN→CHECKOUT + 3 eventos
 ├── debeAutoTransicionarEnBd               → scheduler cambia estado en H2 real
 ├── debeRetornarReservasDelDiaDesdeRealBd  → getReservasHoy() con datos reales en H2
 └── debePaginarReservas                    → 5 reservas, page size 3 → páginas 1(3) y 2(2)
```

**Perfil de test** (`src/test/resources/application-test.yml`):
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:reservas_test;MODE=MySQL
  jpa:
    hibernate:
      ddl-auto: create-drop
```

---

### 4.2 bff-service

**Directorio:** `StaySync_BFF-bff_v1.0/src/test/java/com/staysync/bff/`

#### JwtServiceTest.java — 6 tests unitarios

Cubre la generación y validación de JWT con HMAC-SHA256. Usa `ReflectionTestUtils` para inyectar el secreto.

```
JwtServiceTest (@ExtendWith(MockitoExtension))
 ├── debeExtraerEmailDelToken               → extractEmail() retorna email correcto
 ├── debeExtraerRolDelToken                 → extractRol() retorna "ADMIN"
 ├── debeValidarTokenVigente                → isTokenValid() → true
 ├── debeRechazarTokenExpirado              → token con fecha pasada → isTokenValid() → false
 ├── debeRechazarTokenMalformado            → string basura → isTokenValid() → false
 └── debeRechazarTokenVacio                 → string vacío → isTokenValid() → false
```

**Técnica clave:**
```java
@BeforeEach void setUp() {
    jwtService = new JwtService();
    // @Value no es procesado por @InjectMocks — se inyecta manualmente
    ReflectionTestUtils.setField(jwtService, "secret",
        "staysync-test-secret-key-must-be-at-least-64-characters-for-hs256-hmac-sha256");
}
```

---

#### AuthBffControllerTest.java — 5 tests de capa web

Verifica el proxy del BFF hacia usuarios-service y la publicación de eventos de notificación.

```
AuthBffControllerTest (@WebMvcTest + @AutoConfigureMockMvc(addFilters=false))
 ├── POST /bff/auth/login   → proxy a UsuariosClient.login → retorna AuthResponse
 ├── POST /bff/auth/registro → proxy a UsuariosClient.registro + publica UsuarioRegistradoEvent
 ├── POST /bff/auth/registro (fallo) → UsuariosClient lanza excepción → sin evento publicado
 ├── POST /bff/auth/refresh → retorna nuevo accessToken
 └── POST /bff/auth/logout (sin header) → 400 Bad Request
```

---

#### DashboardBffControllerTest.java — 3 tests de capa web

```
DashboardBffControllerTest (@WebMvcTest + @WithMockUser)
 ├── debeRetornarDashboardAgregado         → rol ADMIN → datos de reservas + habitaciones
 ├── debeRetornarDashboardConDatosVacios   → rol RECEPCIONISTA → fallback con ceros
 └── sinAutenticacionDebe403               → sin @WithMockUser → 403 Forbidden
```

---

### 4.3 usuarios-service

**Directorio:** `StaySync_Usuarios-usuarios_v1.0/src/test/java/com/staysync/usuarios/`

#### UsuarioServiceTest.java — 8 tests unitarios

Organizado con `@Nested` por método (patrón recomendado para servicios con muchos métodos).

```
UsuarioServiceTest
 ├── @Nested RegistrarTests
 │    ├── debeRegistrarUsuario               → encripta password, guarda, retorna response
 │    └── debeLanzarExcepcionEmailDuplicado  → existsByEmail=true → EmailDuplicadoException, save() nunca llamado
 │
 ├── @Nested ObtenerPorIdTests
 │    ├── debeRetornarUsuarioActivo          → findByIdAndActivoTrue → UsuarioResponse
 │    └── debeLanzarExcepcionNoEncontrado    → Optional.empty → UsuarioNotFoundException
 │
 ├── @Nested ListarTests
 │    └── debeRetornarPaginaUsuarios         → Page<Usuario> → Page<UsuarioResponse>
 │
 ├── @Nested DesactivarTests
 │    ├── debeDesactivarUsuario              → activo=false, save() verificado
 │    └── debeLanzarExcepcionAlDesactivarInexistente → Optional.empty → UsuarioNotFoundException
 │
 └── @Nested ActualizarTests
      └── debeActualizarUsuario              → mapper.updateEntityFromRequest verificado con eq()
```

---

#### AuthServiceTest.java — 6 tests unitarios

Cubre el flujo completo de autenticación JWT con refresh tokens.

```
AuthServiceTest
 ├── debeRetornarAuthResponseEnLoginExitoso         → accessToken + refreshToken + email + rol
 ├── debeLanzarExcepcionConCredencialesIncorrectas  → BadCredentialsException propagada
 ├── debeLanzarExcepcionConRefreshTokenInexistente  → "no encontrado" en mensaje
 ├── debeLanzarExcepcionConRefreshTokenExpirado     → token expirado → delete(expired) + excepción
 ├── debeEliminarRefreshTokenEnLogout               → delete(rt) verificado
 └── noDebeLanzarExcepcionEnLogoutConTokenInexistente → logout idempotente (no lanza excepción)
```

---

#### AuthControllerTest.java — 4 tests de capa web

Usa `@Import({SecurityConfig.class, JwtAuthFilter.class})` — prueba con seguridad real activa.

```
AuthControllerTest (@WebMvcTest + @Import SecurityConfig + JwtAuthFilter)
 ├── POST /auth/login     → 200 + accessToken + email en body
 ├── POST /auth/login     → 400 con email inválido (Bean Validation)
 ├── POST /auth/registro  → 201 + datos del usuario creado
 └── POST /auth/registro  → 400 con contraseña débil (Bean Validation)
```

---

### 4.4 pagos-service

**Directorio:** `StaySync_Pago-pagos_v1.0/src/test/java/com/staysync/pagos/`

#### PagoServiceTest.java — 5 tests unitarios

```
PagoServiceTest
 ├── debeProcesarPagoExitoso          → token válido → COMPLETADO, evento pago.completado publicado
 ├── debeLanzarExcepcionSinToken      → token null → PagoFallidoException, evento pago.fallido publicado
 ├── debeRetornarPagoPorId            → findById → PagoResponse con referencia correcta
 ├── debeLanzarExcepcionPagoNoExiste  → Optional.empty → PagoNotFoundException
 └── debeRetornarPagosPorReserva      → findByReservaId → List<PagoResponse> con 1 elemento
```

---

#### StripeServiceTest.java — 7 tests unitarios

Usa el patrón `StripeGateway` (interfaz inyectable) en lugar de `MockedStatic<Session>`, que es incompatible con el Stripe Java SDK 25.x.

```
StripeServiceTest (@ExtendWith(MockitoExtension) + @Mock StripeGateway)
 ├── crearCheckout()
 │    ├── debeCrearCheckoutExitosamente        → stripeGateway.createSession() → sessionId + checkoutUrl
 │    ├── debeLanzarExcepcionSiStripeFalla      → createSession() lanza StripeException → PagoFallidoException "Error al crear sesión"
 │    └── debeConvertirMontoCorrectamente       → BigDecimal 89500.50 redondeado HALF_UP → long 89501
 │
 └── confirmarPago()
      ├── debeConfirmarPagoExitosamente         → paymentStatus="paid" → COMPLETADO + evento pago.completado
      ├── debeSerIdempotente                    → existsByGatewayId=true → retorna existente, retrieveSession() nunca llamado
      ├── debeLanzarExcepcionSiPagoNoProcesado  → paymentStatus="unpaid" → PagoFallidoException "no está aprobado"
      ├── debeLanzarExcepcionSiStripeAPIFalla   → retrieveSession() lanza StripeException → PagoFallidoException "No se pudo verificar"
      └── debeContinuarSiConfirmarReservaFalla  → reservas-service lanza RuntimeException → pago guardado, sin excepción propagada
```

**Dependencias mockeadas:**
```java
@Mock private StripeConfig   stripeConfig;    // success/cancel URLs
@Mock private PagoRepository pagoRepository;  // persistencia
@Mock private RabbitTemplate rabbitTemplate;  // eventos
@Mock private RestTemplate   restTemplate;    // llamada a reservas-service
@Mock private StripeGateway  stripeGateway;   // abstracción sobre Stripe SDK
```

**Por qué `StripeGateway` en lugar de `MockedStatic`:**
```java
// PROBLEMA: MockedStatic<Session> no funciona con Stripe SDK 25.x
// → java.lang.Error: Unresolved — StripeResponseGetter interfiere con el proxy de Mockito

// SOLUCIÓN: interfaz inyectable que envuelve las llamadas estáticas
public interface StripeGateway {
    Session createSession(SessionCreateParams params) throws StripeException;
    Session retrieveSession(String sessionId) throws StripeException;
}

// En test: se mockea normalmente
@Mock private StripeGateway stripeGateway;
when(stripeGateway.createSession(any())).thenReturn(sessionMock);

// En producción: StripeGatewayImpl delega al SDK
Session.create(params);   // ← solo aquí se llama a Stripe real
```

**Stubs `lenient()` en `@BeforeEach`:**
```java
@BeforeEach void setUp() {
    // lenient evita UnnecessaryStubbingException:
    // getSuccessUrl/getCancelUrl solo se usan en crearCheckout(), no en confirmarPago()
    lenient().when(stripeConfig.getSuccessUrl()).thenReturn("http://localhost:3000/...");
    lenient().when(stripeConfig.getCancelUrl()).thenReturn("http://localhost:3000/...");
}
```

---

### 4.5 habitaciones-service

**Directorio:** `StaySync_Habitaciones-habitaciones_v1.0/src/test/java/com/staysync/habitaciones/`

#### HabitacionServiceTest.java — 11 tests unitarios

```
HabitacionServiceTest
 ├── debeCrearHabitacion                    → sin número duplicado → save() llamado
 ├── debeLanzarExcepcionNumeroExistente     → existsByNumero=true → excepción, save() nunca
 ├── debeRetornarHabitacion                 → findByIdAndActivaTrue → HabitacionResponse
 ├── debeLanzarExcepcionNoExiste            → Optional.empty → HabitacionNotFoundException
 ├── debeCambiarEstadoYPublicarEvento       → estado actualizado + eventPublisher.publicarCambioEstado
 ├── debeListarDisponibles                  → findByEstadoAndActivaTrue(DISPONIBLE) → lista
 ├── debeDesactivarHabitacion               → activa=false, save() verificado
 ├── debeListarSoloActivas                  → findByActivaTrue → lista filtrada
 ├── debeRetornarListaVaciaConSinActivas    → findByActivaTrue → lista vacía
 ├── debeLanzarExcepcionNumeroNoEncontrado  → findByNumero → Optional.empty → excepción
 └── debeFiltrarYOrdenar (buscarDisponibles) → capacidad≥4 + sort=precio_desc → orden correcto
```

---

### 4.6 servicios-service

**Directorio:** `StaySync_Servicios-servicios_v1.0/src/test/java/com/staysync/servicios/`

#### ServicioServiceTest.java — 9 tests unitarios

```
ServicioServiceTest
 ├── debeListarServiciosDisponibles         → findByActivoTrue → lista correcta
 ├── debeRetornarServicioPorId              → findById → ServicioResponse
 ├── debeCrearSolicitudServicio             → precio calculado, save() llamado
 ├── debeListarSolicitudesPorReserva        → findByReservaId → lista
 ├── debeCalcularPrecioCorrectamente        → cantidad * precioUnitario verificado
 ├── debeLanzarExcepcionServicioNoExiste    → ServicioNotFoundException
 ├── debeListarSolicitudesPorUsuario        → findByUsuarioId → lista filtrada
 ├── debeListarTodasSolicitudes             → findAll → lista completa
 └── debeCambiarEstadoDeSolicitud           → actualizarEstadoSolicitud → estado cambiado
```

---

### 4.7 notificaciones-service

**Directorio:** `StaySync_Notificaciones-notificaciones_v1.0/src/test/java/com/staysync/notificaciones/`

#### NotificacionServiceTest.java — 9 tests unitarios

```
NotificacionServiceTest
 ├── procesarEvento()
 │    ├── debeCrearNotificacionYEnviarEmail  → plantilla encontrada → mailSender.send() + save() ×2
 │    └── debeManejarPlantillaInexistente    → sin plantilla → email enviado igualmente (asunto genérico)
 │
 ├── procesarRegistroUsuario()
 │    └── debeProcesarRegistroUsuario        → plantilla USUARIO_REGISTRO → email enviado
 │
 ├── procesarReservaCreada()
 │    └── debeProcesarReservaCreada          → plantilla RESERVA_CREADA → email enviado
 │
 ├── procesarCambioEstado()
 │    └── debeProcesarCambioEstado           → plantilla RESERVA_ESTADO → email enviado
 │
 └── enviarEmail()
      ├── debeMarcarEnviadoTrasEnvioExitoso          → estado=ENVIADO, enviadoEn no null
      ├── debeMarcarFallidoTrasMáxIntentos           → 3/3 intentos → FALLIDO, errorMsg guardado
      └── noDebeMarcarFallidoSiHayIntentosRestantes  → 1/3 intentos → no FALLIDO, intentos=1
```

**Técnicas clave:**
```java
// FIX 1: @InjectMocks no procesa @Value — mailEnabled queda false sin esto
ReflectionTestUtils.setField(notificacionService, "mailEnabled", true);

// FIX 2: los eventos son Java records — constructor canónico, NO setters
UsuarioRegistradoEvent event = new UsuarioRegistradoEvent(1L, "Ana", "ana@test.com");
ReservaCreadaEvent event = new ReservaCreadaEvent(
    1L, 1L, "juan@test.com", "Juan", "RES-001", "101", "2026-12-01", "2026-12-05", 400.0);
```

---

### 4.8 ota-service

**Directorio:** `StaySync_OTA-ota_v1.0/src/test/java/com/staysync/ota/`

#### CanalOtaServiceTest.java — 4 tests unitarios

```
CanalOtaServiceTest
 ├── debeListarActivos                  → findByActivoTrue → lista con BOOKING
 ├── debeRetornarCanalPorId             → findById → CanalOtaResponse
 ├── debeLanzarExcepcionNoExiste        → Optional.empty → CanalOtaNotFoundException
 └── debeRegistrarNuevaReservaOta       → no existe → save() → nueva ReservaOta
```

#### HabitacionEventListenerTest.java — 3 tests unitarios

Verifica el listener de RabbitMQ que sincroniza disponibilidad con canales OTA.

```
HabitacionEventListenerTest
 ├── debeProcesarEventoDisponibilidad           → Map con habitacionId válido → listarActivos() llamado
 ├── debeProcesarConListaCanalesVacia           → sin canales → sin error, forEach no itera
 └── debeManejarHabitacionIdInvalidoSinExcepcion → habitacionId="no-es-numero" → catch silencioso
```

---

## 5. Bugs encontrados y corregidos

### Bug 1 — Test flaky por hora de ejecución (CRÍTICO)

**Archivo:** `ReservaServiceTest.java`

**Problema:**
```java
// ANTES — fallaba si el test corría después de las ~15:00h Chile
.fechaEntrada(LocalDate.now().plusDays(1))
```
La regla de negocio exige >24h de anticipación. Con `plusDays(1)` = mañana, si el test corre a las 16:00h, `horasRestantes` ≈ 23h < 24h → `CancelacionRestringidaException` inesperada → test rojo.

**Solución:**
```java
// DESPUÉS — siempre > 24h sin importar la hora
.fechaEntrada(LocalDate.now().plusDays(5))
```

---

### Bug 2 — RestTemplate sin mock probaba el fallback (MODERADO)

**Archivo:** `ReservaServiceTest.java`

**Problema:** Sin mock de `RestTemplate`, Mockito devuelve `null` → el servicio ejecuta el path de fallback (precio = 100.00, sin validación de capacidad). El test pasaba pero probaba el caso de error en lugar del caso normal.

**Solución:** Dos tests explícitos:
```java
debeCrearReservaConPrecioReal()        // mock devuelve { precioPorNoche: 90, capacidad: 3 }
debeCrearReservaConPrecioPorDefecto()  // mock lanza RuntimeException → precio por defecto
```

---

### Bug 3 — `@Value` no inyectado por `@InjectMocks` (CRÍTICO)

**Archivo:** `NotificacionServiceTest.java`

**Problema:** `NotificacionService` tiene `@Value("${notificaciones.mail.enabled:true}")`. `@InjectMocks` **no procesa** anotaciones de Spring. El campo `mailEnabled` quedaba en `false` (default Java para boolean) → el servicio cortaba el flujo sin llamar a `mailSender.send()` → todos los `verify(mailSender).send(...)` fallaban.

**Solución:**
```java
@BeforeEach void setUp() {
    ReflectionTestUtils.setField(notificacionService, "mailEnabled", true);
    // ... setup plantillas
}
```

---

### Bug 4 — Constructor de records mal usado (CRÍTICO)

**Archivo:** `NotificacionServiceTest.java`

**Problema:** Los eventos son Java `record`. Los records no tienen setters ni constructor sin argumentos. El test original intentaba usar `new UsuarioRegistradoEvent()` + setters → error de compilación.

**Solución:**
```java
// INCORRECTO — records no tienen setters
UsuarioRegistradoEvent event = new UsuarioRegistradoEvent();
event.setNombre("Ana");  // no existe

// CORRECTO — constructor canónico del record
UsuarioRegistradoEvent event = new UsuarioRegistradoEvent(1L, "Ana", "ana@test.com");
```

---

### Bug 5 — `@MockBean` deprecada en Spring Boot 3.4+ (MENOR)

**Archivos afectados:** `AuthControllerTest.java` (Usuarios), `ReservaControllerTest.java`, `ReservaIntegrationTest.java`

**Problema:** Spring Boot 3.4 deprecó `org.springframework.boot.test.mock.mockito.MockBean` en favor de `org.springframework.test.context.bean.override.mockito.MockitoBean`.

**Solución aplicada en los 3 archivos:**
```java
// ANTES
import org.springframework.boot.test.mock.mockito.MockBean;
@MockBean private ReservaService reservaService;

// DESPUÉS
import org.springframework.test.context.bean.override.mockito.MockitoBean;
@MockitoBean private ReservaService reservaService;
```

---

### Bug 6 — `MockedStatic<Session>` incompatible con Stripe SDK 25.x (CRÍTICO)

**Archivo:** `StripeServiceTest.java`

**Problema:** Mockito's `MockedStatic` intenta interceptar el bytecode del método estático `Session.create()`. El Stripe Java SDK 25.x usa internamente un `StripeResponseGetter` que confunde al agente de instrumentación de Mockito, produciendo:
```
java.lang.Error: Unresolved compilation problem: ...
org.mockito.exceptions.misusing.UnnecessaryStubbingException
```

**Causa raíz:** Los métodos estáticos del SDK de Stripe **no son simples delegadores**; internamente gestionan estado global (`Stripe.apiKey`) que Mockito no puede interceptar limpiamente.

**Solución — patrón StripeGateway:**
```java
// 1. Crear interfaz para abstraer las llamadas estáticas
public interface StripeGateway {
    Session createSession(SessionCreateParams params) throws StripeException;
    Session retrieveSession(String sessionId) throws StripeException;
}

// 2. Implementación real (solo esta clase llama al SDK)
@Component
public class StripeGatewayImpl implements StripeGateway {
    public Session createSession(SessionCreateParams p) throws StripeException {
        return Session.create(p);      // ← única llamada real al SDK
    }
    public Session retrieveSession(String id) throws StripeException {
        return Session.retrieve(id);   // ← única llamada real al SDK
    }
}

// 3. StripeService inyecta la interfaz (no el SDK directamente)
private final StripeGateway stripeGateway;  // ← @RequiredArgsConstructor

// 4. En test: mock normal, sin MockedStatic
@Mock private StripeGateway stripeGateway;
when(stripeGateway.createSession(any())).thenReturn(sessionMock);  // ← funciona perfecto
```

**Archivos creados/modificados:**
- **Nuevo:** `StripeGateway.java` (interfaz)
- **Nuevo:** `StripeGatewayImpl.java` (implementación)
- **Modificado:** `StripeService.java` — inyecta `StripeGateway`, ya no llama `Session.create()` directamente
- **Reescrito:** `StripeServiceTest.java` — usa `@Mock StripeGateway` en lugar de `MockedStatic`

---

## 6. Cómo ejecutar los tests

### Prerequisito: Maven instalado

Si `mvn` no está en el PATH, instalarlo con:
```powershell
# Opción A: winget (Windows 11)
winget install Apache.Maven

# Opción B: Chocolatey
choco install maven

# Opción C: IntelliJ IDEA — tiene Maven integrado, click derecho en pom.xml → "Run Maven" → test
```

### Ejecutar todos los tests de un microservicio

```bash
cd StaySync_Reservas-reservas_v1.1/StaySync_Reservas-reservas_v1.1
mvn test
```

### Ejecutar solo tests unitarios (rápidos, ~ms cada uno)

```bash
mvn test -Dtest="*ServiceTest,*ListenerTest"
```

### Ejecutar solo tests de capa web

```bash
mvn test -Dtest="*ControllerTest"
```

### Ejecutar solo tests de integración

```bash
mvn test -Dtest="*IntegrationTest"
```

### Ejecutar un test específico

```bash
mvn test -Dtest="ReservaServiceTest#debeCancelarReserva"
```

### Ejecutar tests + generar reporte de cobertura

```bash
mvn verify
# Reporte en: target/site/jacoco/index.html
```

### Verificar que la cobertura cumple el mínimo (60%)

```bash
mvn verify -pl StaySync_Reservas-reservas_v1.1
# Si cobertura < 60%, el build falla:
# [ERROR] Rule violated: lines covered ratio is 0.55, but expected minimum is 0.60
```

### Ejecutar todos los servicios desde la raíz (si hay POM padre)

```bash
# Desde la raíz del workspace
mvn test --file pom.xml
```

---

## 7. Cobertura con JaCoCo

### Configuración en cada pom.xml

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.60</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Ver el reporte

Después de `mvn verify`, abrir en el navegador:
```
target/site/jacoco/index.html
```

Muestra cobertura por: clase, método, línea y rama (branch).

### Excluir clases de configuración/modelo (recomendado)

```xml
<configuration>
    <excludes>
        <exclude>**/config/**</exclude>
        <exclude>**/model/**</exclude>
        <exclude>**/dto/**</exclude>
        <exclude>**/*Application.class</exclude>
    </excludes>
</configuration>
```

---

## 8. Convenciones y buenas prácticas

### Nomenclatura de tests

```java
// Patrón: debe<AcciónEsperada>[Cuando<Condición>]
void debeCrearReserva()                          // happy path simple
void debeLanzarExcepcionCapacidadExcedida()      // error con condición explícita
void noDebeHacerNadaSinReservasParaHoy()         // acción negada con condición
```

### Estructura AAA (Arrange — Act — Assert)

```java
@Test
void debeCrearReserva() {
    // ARRANGE — preparar datos y mocks
    CrearReservaRequest request = buildRequest(2);
    when(reservaRepository.existeConflicto(...)).thenReturn(false);
    when(reservaRepository.save(any())).thenReturn(reservaBase);

    // ACT — ejecutar el método bajo test
    ReservaResponse response = reservaService.crear(request);

    // ASSERT — verificar resultado Y efectos secundarios
    assertThat(response.getCodigo()).isNotBlank();
    verify(eventPublisher).publicar(eq("reserva.confirmada"), anyMap());
}
```

### Reglas de oro

| Regla | Por qué |
|---|---|
| `plusDays(5)` para fechas futuras en tests | Evita tests flaky que fallan según la hora del día |
| Siempre mockear RestTemplate en unit tests | Sin mock devuelve `null` → se prueba el fallback sin saberlo |
| `verify()` para efectos secundarios | Un `assertThat(result).isNotNull()` solo verifica que no es null, no el comportamiento |
| `@MockitoBean` en lugar de `@MockBean` | `@MockBean` está deprecado desde Spring Boot 3.4 |
| `ReflectionTestUtils.setField()` para `@Value` | `@InjectMocks` no procesa anotaciones de Spring |
| Constructor canónico para Java records | Los records no tienen setters ni constructor sin args |
| `@Transactional` en tests de integración | Cada test revierte cambios → no se contaminan entre sí |

### Qué NO mockear

- La clase bajo test (nunca hacer `@Spy` de la clase que se testea)
- Clases simples sin efectos secundarios (`LocalDate`, `BigDecimal`)
- El repositorio en tests de integración (queremos que use H2 real)

---

## 9. Próximos pasos

### Tests pendientes de alta prioridad

- [ ] `AuthServiceTest` — agregar test para `refresh()` happy path (renovar token exitosamente)
- [ ] `UsuarioServiceTest` — agregar test para actualizar email a uno ya existente → `EmailDuplicadoException`
- [ ] `ReservaIntegrationTest` — test de `cancelar()` con regla 24h desde BD real

### Tests de integración pendientes para otros servicios

```
pagos-service          → PagoIntegrationTest con H2
habitaciones-service   → HabitacionIntegrationTest con H2
notificaciones-service → NotificacionIntegrationTest (mock JavaMailSender)
```

### Tests E2E (fase futura con Testcontainers)

Cuando el proyecto esté en Docker, se puede implementar un test E2E completo:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class StaySyncE2ETest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

    // 1. POST /auth/registro → 201
    // 2. POST /auth/login    → 200 + JWT
    // 3. POST /reservas      → 201 + código reserva
    // 4. POST /pagos/stripe/checkout → 200 + checkoutUrl
    // 5. PATCH /reservas/{id}/estado → CHECKIN → 200
    // 6. PATCH /reservas/{id}/estado → CHECKOUT → 200
    // 7. Verificar notificación en BD → estado ENVIADO
}
```

Dependencias:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>rabbitmq</artifactId>
    <scope>test</scope>
</dependency>
```

---

*Documentación actualizada el 2026-06-20 — StaySync Hotel Management System — 16 archivos de test, 118 tests totales*
