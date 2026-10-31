package com.staysync.reservas.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staysync.eventos.EventoDominio;
import com.staysync.eventos.TiposEvento;
import com.staysync.reservas.dto.request.CrearReservaRequest;
import com.staysync.reservas.dto.response.ReservaResponse;
import com.staysync.reservas.exception.HabitacionNoDisponibleException;
import com.staysync.reservas.exception.ReservaNotFoundException;
import com.staysync.reservas.messaging.EventosReservaListener;
import com.staysync.reservas.model.Reserva;
import com.staysync.reservas.model.Reserva.EstadoReserva;
import com.staysync.reservas.model.Reserva.FuenteReserva;
import com.staysync.reservas.repository.ReservaNocheRepository;
import com.staysync.reservas.repository.ReservaRepository;
import com.staysync.reservas.service.ReservaService;
import com.staysync.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests de integración: Spring Boot completo con H2 (perfil "test").
 *
 * Sin @Transactional a nivel de clase a propósito: cada llamada al servicio abre su propia
 * sesión de Hibernate con el hotel del contexto, igual que en producción. Los datos se
 * limpian al final de cada test en modo sistema.
 * RestTemplate se mockea para no depender de habitaciones-service.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ReservaService - Tests de Integración (H2)")
class ReservaIntegrationTest {

    private static final String DEMO = "staysync-demo";
    private static final String ANDES = "hotel-andes";
    private static final LocalDate HOY = LocalDate.now(ReservaService.ZONA_HOTEL);

    @Autowired private ReservaService reservaService;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private ReservaNocheRepository nocheRepository;
    @Autowired private EventosReservaListener listener;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    // Mock de infraestructura externa
    @MockitoBean private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        // Simular respuesta de habitaciones-service con habitación de capacidad 3
        Map<String, Object> habitacionData = Map.of(
                "id", 1,
                "numero", "101",
                "precioPorNoche", 100.00,
                "capacidad", 3
        );
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(habitacionData);
        TenantContext.set(DEMO);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
        TenantContext.ejecutarComoSistema(() -> {
            nocheRepository.deleteAll();
            reservaRepository.deleteAll();
        });
        jdbc.update("DELETE FROM outbox_eventos");
        jdbc.update("DELETE FROM eventos_procesados");
    }

    private List<String> eventosEnOutbox() {
        return jdbc.queryForList("SELECT tipo FROM outbox_eventos ORDER BY id", String.class);
    }

    // ── Ciclo de vida de una reserva ──────────────────────────────────────────

    @Test
    @DisplayName("Debe persistir reserva en BD, con su hotel, y asignar código único")
    void debePersistirReserva() {
        CrearReservaRequest request = buildRequest(1L, 1L,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(12), 2);

        ReservaResponse response = reservaService.crear(request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getCodigo()).matches("RES-[A-Z0-9]{8}");
        assertThat(response.getPrecioTotal()).isEqualByComparingTo(BigDecimal.valueOf(200)); // 100 * 2 noches
        assertThat(response.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(response.getHabitacionNumero()).isEqualTo("101");

        Optional<Reserva> enBd = reservaRepository.findById(response.getId());
        assertThat(enBd).isPresent();
        assertThat(enBd.get().getTenantId()).isEqualTo(DEMO);
        assertThat(eventosEnOutbox()).containsExactly(TiposEvento.RESERVA_CREADA);
    }

    @Test
    @DisplayName("Una reserva PENDIENTE ya bloquea la habitación para esas fechas")
    void debeRechazarReservaPorConflicto() {
        LocalDate entrada = LocalDate.now().plusDays(10);
        LocalDate salida  = LocalDate.now().plusDays(12);

        reservaService.crear(buildRequest(1L, 2L, entrada, salida, 1));

        assertThatThrownBy(() -> reservaService.crear(buildRequest(2L, 2L, entrada, salida, 1)))
                .isInstanceOf(HabitacionNoDisponibleException.class);
    }

    @Test
    @DisplayName("Cancelar libera las noches: otra reserva puede tomarlas")
    void cancelarLiberaNoches() {
        LocalDate entrada = LocalDate.now().plusDays(10);
        LocalDate salida  = LocalDate.now().plusDays(12);
        ReservaResponse primera = reservaService.crear(buildRequest(1L, 7L, entrada, salida, 1));

        reservaService.cancelar(primera.getId());

        assertThat(reservaService.crear(buildRequest(2L, 7L, entrada, salida, 1)).getId()).isNotNull();
        assertThat(eventosEnOutbox()).contains(TiposEvento.RESERVA_CANCELADA);
    }

    @Test
    @DisplayName("Modificar: correr la estadía un día reusa sus propias noches y libera la que dejó")
    void modificarReBloqueaNoches() {
        ReservaResponse r = reservaService.crear(buildRequest(1L, 21L, HOY.plusDays(10), HOY.plusDays(12), 1));
        var cambio = new com.staysync.reservas.dto.request.ModificarReservaRequest();
        cambio.setFechaEntrada(HOY.plusDays(11));
        cambio.setFechaSalida(HOY.plusDays(13));

        ReservaResponse modificada = reservaService.modificar(r.getId(), cambio);

        assertThat(modificada.getFechaEntrada()).isEqualTo(HOY.plusDays(11));
        assertThat(nocheRepository.count()).isEqualTo(2);
        // La noche que dejó libre ya se puede reservar
        assertThat(reservaService.crear(buildRequest(2L, 21L, HOY.plusDays(10), HOY.plusDays(11), 1)).getId()).isNotNull();
        assertThat(reservaService.listarEnRango(HOY.plusDays(10), HOY.plusDays(11))).hasSize(1);
    }

    @Test
    @DisplayName("Anti-overbooking: 10 reservas simultáneas de la misma habitación → solo 1 se crea")
    void antiOverbookingConcurrente() throws Exception {
        LocalDate entrada = LocalDate.now().plusDays(20);
        LocalDate salida  = LocalDate.now().plusDays(23);
        int intentos = 10;
        ExecutorService pool = Executors.newFixedThreadPool(intentos);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();

        for (int i = 0; i < intentos; i++) {
            long usuario = i + 1;
            resultados.add(pool.submit(() -> {
                largada.await();
                return TenantContext.ejecutarCon(DEMO, () -> {
                    try {
                        reservaService.crear(buildRequest(usuario, 99L, entrada, salida, 1));
                        return true;
                    } catch (HabitacionNoDisponibleException e) {
                        return false;
                    }
                });
            }));
        }
        largada.countDown();
        int creadas = 0;
        for (Future<Boolean> r : resultados) {
            if (r.get(30, TimeUnit.SECONDS)) creadas++;
        }
        pool.shutdown();

        assertThat(creadas).isEqualTo(1);
        assertThat(nocheRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("Transición completa PENDIENTE → CONFIRMADA → CHECKIN → CHECKOUT publica un evento por paso")
    void debePasarPorTodosLosEstados() {
        // El check-in solo se permite desde el día de llegada
        ReservaResponse creada = reservaService.crear(buildRequest(1L, 3L, HOY, HOY.plusDays(2), 2));
        Long id = creada.getId();

        reservaService.cambiarEstado(id, EstadoReserva.CONFIRMADA);
        reservaService.cambiarEstado(id, EstadoReserva.CHECKIN);
        reservaService.cambiarEstado(id, EstadoReserva.CHECKOUT);

        assertThat(reservaRepository.findById(id).get().getEstado()).isEqualTo(EstadoReserva.CHECKOUT);
        assertThat(eventosEnOutbox()).containsExactly(
                TiposEvento.RESERVA_CREADA, TiposEvento.RESERVA_CONFIRMADA,
                TiposEvento.RESERVA_CHECKIN, TiposEvento.RESERVA_CHECKOUT);
        // La habitación ya no se sincroniza por HTTP: lo hace habitaciones-service con el evento
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(Object.class));
    }

    @Test
    @DisplayName("cerrarReservasVencidas() en modo sistema: no-show en un hotel, pendiente sin pago en otro")
    void debeCerrarVencidasEnBd() {
        // Confirmada que debía llegar hace 2 días (fecha movida por SQL: el servicio no crea reservas pasadas)
        ReservaResponse demo = reservaService.crear(buildRequest(1L, 4L, HOY, HOY.plusDays(4), 1));
        reservaService.cambiarEstado(demo.getId(), EstadoReserva.CONFIRMADA);
        jdbc.update("UPDATE reservas SET fecha_entrada = ? WHERE id = ?", HOY.minusDays(2), demo.getId());
        // Pendiente creada hace 25 h y nunca pagada
        ReservaResponse andes = TenantContext.ejecutarCon(ANDES, () ->
                reservaService.crear(buildRequest(1L, 4L, HOY.plusDays(10), HOY.plusDays(12), 1)));
        jdbc.update("UPDATE reservas SET created_at = ? WHERE id = ?",
                java.time.LocalDateTime.now().minusHours(25), andes.getId());
        // Pendiente recién creada: sigue reteniendo su habitación
        ReservaResponse vigente = reservaService.crear(buildRequest(2L, 12L, HOY.plusDays(3), HOY.plusDays(4), 1));
        jdbc.update("DELETE FROM outbox_eventos");

        TenantContext.ejecutarComoSistema(reservaService::cerrarReservasVencidas);

        assertThat(reservaRepository.findById(demo.getId()).get().getEstado()).isEqualTo(EstadoReserva.NO_SHOW);
        assertThat(TenantContext.ejecutarCon(ANDES, () -> reservaRepository.findById(andes.getId()).get().getEstado()))
                .isEqualTo(EstadoReserva.CANCELADA);
        assertThat(reservaRepository.findById(vigente.getId()).get().getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        // Solo quedan las noches de la reserva vigente
        assertThat(TenantContext.ejecutarComoSistema(() -> nocheRepository.count())).isEqualTo(1);
        // Solo la cancelación avisa (Pagos reembolsaría); el no-show no se reembolsa
        assertThat(jdbc.queryForList("SELECT tenant_id FROM outbox_eventos WHERE tipo = ?", String.class,
                TiposEvento.RESERVA_CANCELADA)).containsExactly(ANDES);
    }

    @Test
    @DisplayName("Una reserva de otro hotel no existe para este hotel")
    void aislamientoEntreHoteles() {
        ReservaResponse deAndes = TenantContext.ejecutarCon(ANDES, () -> reservaService.crear(
                buildRequest(1L, 8L, LocalDate.now().plusDays(3), LocalDate.now().plusDays(4), 1)));

        assertThatThrownBy(() -> reservaService.obtenerPorId(deAndes.getId()))
                .isInstanceOf(ReservaNotFoundException.class);
        assertThat(reservaService.listar(PageRequest.of(0, 10)).getContent()).isEmpty();
        // La misma habitación 8 y fechas sí están libres en este hotel: son habitaciones distintas
        assertThat(reservaService.crear(buildRequest(2L, 8L, LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(4), 1)).getId()).isNotNull();
    }

    @Test
    @DisplayName("El evento PagoEfectuado confirma la reserva; si llega duplicado no pasa nada")
    void pagoEfectuadoConfirma() throws Exception {
        ReservaResponse r = reservaService.crear(
                buildRequest(1L, 11L, LocalDate.now().plusDays(6), LocalDate.now().plusDays(8), 1));
        String evento = objectMapper.writeValueAsString(
                EventoDominio.nuevo(TiposEvento.PAGO_EFECTUADO, DEMO, Map.of("reservaId", r.getId())));
        TenantContext.clear(); // el listener corre sin hotel: lo toma del evento

        listener.recibir(evento);
        listener.recibir(evento);

        TenantContext.set(DEMO);
        assertThat(reservaRepository.findById(r.getId()).get().getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(eventosEnOutbox()).containsExactly(TiposEvento.RESERVA_CREADA, TiposEvento.RESERVA_CONFIRMADA);
    }

    @Test
    @DisplayName("getReservasHoy() debe separar correctamente los pendientes de check-in y check-out")
    void debeRetornarReservasDelDiaDesdeRealBd() {
        ReservaResponse checkin = reservaService.crear(buildRequest(1L, 5L, HOY, HOY.plusDays(2), 1));
        reservaService.cambiarEstado(checkin.getId(), EstadoReserva.CONFIRMADA);

        ReservaResponse checkout = reservaService.crear(buildRequest(2L, 6L, HOY, HOY.plusDays(1), 1));
        reservaService.cambiarEstado(checkout.getId(), EstadoReserva.CONFIRMADA);
        reservaService.cambiarEstado(checkout.getId(), EstadoReserva.CHECKIN);

        Map<String, Object> hoy = reservaService.getReservasHoy();

        assertThat((List<?>) hoy.get("pendientesCheckin")).isNotEmpty();
        assertThat((List<?>) hoy.get("pendientesCheckout")).isNotEmpty();
    }

    @Test
    @DisplayName("listar() debe paginar correctamente con múltiples reservas en BD")
    void debePaginarReservas() {
        for (int i = 0; i < 5; i++) {
            reservaService.crear(buildRequest((long)(i + 1), (long)(i + 10),
                    LocalDate.now().plusDays(i + 5), LocalDate.now().plusDays(i + 7), 1));
        }

        Page<ReservaResponse> pagina1 = reservaService.listar(PageRequest.of(0, 3));
        Page<ReservaResponse> pagina2 = reservaService.listar(PageRequest.of(1, 3));

        assertThat(pagina1.getContent()).hasSize(3);
        assertThat(pagina1.getTotalElements()).isEqualTo(5);
        assertThat(pagina2.getContent()).hasSize(2);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private CrearReservaRequest buildRequest(Long usuarioId, Long habitacionId,
                                              LocalDate entrada, LocalDate salida,
                                              int numHuespedes) {
        CrearReservaRequest req = new CrearReservaRequest();
        req.setUsuarioId(usuarioId);
        req.setHabitacionId(habitacionId);
        req.setFechaEntrada(entrada);
        req.setFechaSalida(salida);
        req.setNumHuespedes(numHuespedes);
        req.setFuente(FuenteReserva.DIRECTO);
        return req;
    }
}
