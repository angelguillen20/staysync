package com.staysync.reservas.service;

import com.staysync.reservas.dto.request.CrearReservaRequest;
import com.staysync.reservas.dto.response.ReservaResponse;
import com.staysync.reservas.exception.*;
import com.staysync.reservas.model.HuespedAdicional;
import com.staysync.reservas.model.Reserva;
import com.staysync.reservas.model.Reserva.EstadoReserva;
import com.staysync.reservas.model.Reserva.FuenteReserva;
import com.staysync.eventos.Outbox;
import com.staysync.eventos.TiposEvento;
import com.staysync.reservas.repository.ReservaNocheRepository;
import com.staysync.reservas.repository.ReservaRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservaService - Tests Unitarios")
class ReservaServiceTest {

    @Mock private ReservaRepository reservaRepository;
    @Mock private ReservaNocheRepository nocheRepository;
    @Mock private RestTemplate restTemplate;
    @Mock private Outbox outbox;

    @InjectMocks private ReservaService reservaService;

    private static final LocalDate HOY = LocalDate.now(ReservaService.ZONA_HOTEL);

    private Reserva reservaBase;

    @BeforeEach
    void setUp() {
        // FIX Bug 1: plusDays(5) garantiza siempre > 24h de anticipación
        // Con plusDays(1) el test fallaba en la tarde (hora Chile) porque
        // horasRestantes < 24 → CancelacionRestringidaException inesperada
        reservaBase = Reserva.builder()
                .id(1L).codigo("RES-ABCD1234")
                .usuarioId(1L).habitacionId(1L)
                .fechaEntrada(LocalDate.now().plusDays(5))
                .fechaSalida(LocalDate.now().plusDays(7))
                .numHuespedes(2)
                .estado(EstadoReserva.PENDIENTE)
                .precioTotal(BigDecimal.valueOf(200))
                .fuente(FuenteReserva.DIRECTO)
                .huespedesAdicionales(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── CREAR ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("crear() - debe crear reserva con precio de habitación cuando el servicio responde")
    void debeCrearReservaConPrecioReal() {
        CrearReservaRequest request = buildRequest(2);

        // FIX Bug 2: mockear restTemplate para que el test cubra el camino real
        Map<String, Object> habitacionData = new HashMap<>();
        habitacionData.put("precioPorNoche", 90.0);
        habitacionData.put("capacidad", 3);

        when(reservaRepository.existeConflicto(anyLong(), any(), any())).thenReturn(false);
        when(reservaRepository.existsByCodigo(anyString())).thenReturn(false);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(habitacionData);
        when(reservaRepository.save(any())).thenReturn(reservaBase);

        ReservaResponse response = reservaService.crear(request);

        assertThat(response).isNotNull();
        assertThat(response.getCodigo()).isNotBlank();
    }

    @Test
    @DisplayName("crear() - sin habitaciones-service no reserva: no hay precio real que cobrar")
    void rechazaSinPrecioDeHabitacion() {
        CrearReservaRequest request = buildRequest(2);

        when(reservaRepository.existeConflicto(anyLong(), any(), any())).thenReturn(false);
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> reservaService.crear(request))
                .isInstanceOf(ServicioNoDisponibleException.class);
        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear() - rechaza una fecha de entrada pasada según el día del hotel")
    void rechazaEntradaPasada() {
        CrearReservaRequest request = buildRequest(1);
        request.setFechaEntrada(HOY.minusDays(1));
        request.setFechaSalida(HOY.plusDays(1));

        assertThatThrownBy(() -> reservaService.crear(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pasado");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("crear() - debe lanzar excepción si numHuespedes supera la capacidad de la habitación")
    void debeLanzarExcepcionCapacidadExcedida() {
        CrearReservaRequest request = buildRequest(5); // pide 5 huéspedes

        Map<String, Object> habitacionData = new HashMap<>();
        habitacionData.put("capacidad", 3); // capacidad máxima 3
        habitacionData.put("precioPorNoche", 100.0);

        when(reservaRepository.existeConflicto(anyLong(), any(), any())).thenReturn(false);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(habitacionData);

        assertThatThrownBy(() -> reservaService.crear(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacidad máxima");

        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear() - debe lanzar excepción si hay conflicto de fechas")
    void debeLanzarExcepcionConConflicto() {
        CrearReservaRequest request = buildRequest(2);

        when(reservaRepository.existeConflicto(anyLong(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> reservaService.crear(request))
                .isInstanceOf(HabitacionNoDisponibleException.class);

        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear() - debe guardar huéspedes adicionales cuando se incluyen en el request")
    void debeGuardarHuespedesAdicionales() {
        CrearReservaRequest.HuespedAdicionalRequest h = new CrearReservaRequest.HuespedAdicionalRequest();
        h.setNombre("María");
        h.setApellido("López");
        h.setDocumento("12345678");

        CrearReservaRequest request = buildRequest(2);
        request.setHuespedesAdicionales(List.of(h));

        Reserva reservaConHuespedes = reservaBase.toBuilder()
                .huespedesAdicionales(new ArrayList<>(List.of(
                        HuespedAdicional.builder().nombre("María").apellido("López").build()
                ))).build();

        when(reservaRepository.existeConflicto(anyLong(), any(), any())).thenReturn(false);
        when(reservaRepository.existsByCodigo(anyString())).thenReturn(false);
        habitacionDisponible();
        when(reservaRepository.save(any())).thenReturn(reservaBase)
                                           .thenReturn(reservaConHuespedes);

        ReservaResponse response = reservaService.crear(request);

        assertThat(response).isNotNull();
        // save se llama 2 veces: 1a para la reserva, 2a para los huéspedes adicionales
        verify(reservaRepository, times(2)).save(any(Reserva.class));
    }

    @Test
    @DisplayName("crear() - rechaza una habitación que no existe en el hotel (404 de habitaciones)")
    void rechazaHabitacionDeOtroHotel() {
        CrearReservaRequest request = buildRequest(1);
        when(reservaRepository.existeConflicto(anyLong(), any(), any())).thenReturn(false);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenThrow(
                org.springframework.web.client.HttpClientErrorException.create(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertThatThrownBy(() -> reservaService.crear(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no existe en este hotel");
        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("crear() - debe lanzar excepción si fechaSalida no es posterior a fechaEntrada")
    void debeLanzarExcepcionFechasInvalidas() {
        CrearReservaRequest request = new CrearReservaRequest();
        request.setUsuarioId(1L);
        request.setHabitacionId(1L);
        request.setFechaEntrada(LocalDate.now().plusDays(5));
        request.setFechaSalida(LocalDate.now().plusDays(5)); // igual, no posterior
        request.setNumHuespedes(2);
        request.setFuente(FuenteReserva.DIRECTO);

        assertThatThrownBy(() -> reservaService.crear(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posterior");
    }

    // ── OBTENER ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("obtenerPorId() - debe retornar reserva existente")
    void debeRetornarReserva() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));

        ReservaResponse response = reservaService.obtenerPorId(1L);

        assertThat(response).isNotNull();
        assertThat(response.getCodigo()).isEqualTo("RES-ABCD1234");
    }

    @Test
    @DisplayName("obtenerPorId() - debe lanzar excepción si no existe")
    void debeLanzarExcepcionNoExiste() {
        when(reservaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservaService.obtenerPorId(99L))
                .isInstanceOf(ReservaNotFoundException.class);
    }

    @Test
    @DisplayName("listar() - debe retornar página de reservas")
    void debeListarReservas() {
        Pageable pageable = PageRequest.of(0, 10);
        when(reservaRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(reservaBase)));

        Page<ReservaResponse> result = reservaService.listar(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ── CAMBIAR ESTADO ────────────────────────────────────────────────────────

    @Test
    @DisplayName("cambiarEstado() - PENDIENTE → CONFIRMADA publica ReservaConfirmada")
    void debeCambiarEstadoPendienteAConfirmada() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));
        when(reservaRepository.save(any())).thenReturn(reservaBase);

        reservaService.cambiarEstado(1L, EstadoReserva.CONFIRMADA);

        assertThat(reservaBase.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        verify(outbox).registrar(eq(TiposEvento.RESERVA_CONFIRMADA), any(), anyMap());
    }

    @Test
    @DisplayName("cambiarEstado() - CONFIRMADA → CHECKIN publica ReservaCheckin (Habitaciones la marca OCUPADA)")
    void debeCambiarEstadoConfirmadaACheckin() {
        reservaBase.setEstado(EstadoReserva.CONFIRMADA);
        reservaBase.setFechaEntrada(HOY);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));
        when(reservaRepository.save(any())).thenReturn(reservaBase);

        reservaService.cambiarEstado(1L, EstadoReserva.CHECKIN);

        assertThat(reservaBase.getEstado()).isEqualTo(EstadoReserva.CHECKIN);
        verify(outbox).registrar(eq(TiposEvento.RESERVA_CHECKIN), any(), anyMap());
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("cambiarEstado() - no hay check-in antes del día de llegada")
    void rechazaCheckinAnticipado() {
        reservaBase.setEstado(EstadoReserva.CONFIRMADA); // llega en 5 días
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));

        assertThatThrownBy(() -> reservaService.cambiarEstado(1L, EstadoReserva.CHECKIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("día de llegada");
        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("cambiarEstado() - debe lanzar excepción en transición inválida CHECKOUT → CHECKIN")
    void debeLanzarExcepcionTransicionInvalida() {
        reservaBase.setEstado(EstadoReserva.CHECKOUT);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));

        assertThatThrownBy(() -> reservaService.cambiarEstado(1L, EstadoReserva.CHECKIN))
                .isInstanceOf(TransicionEstadoInvalidaException.class);
    }

    // ── CANCELAR ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelar() - debe cancelar reserva CONFIRMADA con > 24h de anticipación")
    void debeCancelarReserva() {
        // FIX Bug 1: reservaBase tiene fechaEntrada en 5 días → siempre > 24h
        reservaBase.setEstado(EstadoReserva.CONFIRMADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));
        when(reservaRepository.save(any())).thenReturn(reservaBase);

        reservaService.cancelar(1L);

        assertThat(reservaBase.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        verify(nocheRepository).liberarNoches(1L);
        verify(outbox).registrar(eq(TiposEvento.RESERVA_CANCELADA), any(), anyMap());
    }

    @Test
    @DisplayName("cancelar() - debe lanzar CancelacionRestringidaException con < 24h de anticipación")
    void debeLanzarExcepcionCancelacionFueraDePlazo() {
        // Check-in es mañana temprano → menos de 24h de anticipación
        reservaBase.setEstado(EstadoReserva.CONFIRMADA);
        reservaBase.setFechaEntrada(LocalDate.now()); // hoy mismo (check-in pasado)
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));

        assertThatThrownBy(() -> reservaService.cancelar(1L))
                .isInstanceOf(CancelacionRestringidaException.class)
                .hasMessageContaining("24 horas");

        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelar() - debe lanzar excepción si la reserva ya está CANCELADA")
    void debeLanzarExcepcionSiYaCancelada() {
        reservaBase.setEstado(EstadoReserva.CANCELADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));

        assertThatThrownBy(() -> reservaService.cancelar(1L))
                .isInstanceOf(TransicionEstadoInvalidaException.class);
    }

    @Test
    @DisplayName("cancelar() - debe lanzar excepción si la reserva ya hizo CHECKOUT")
    void debeLanzarExcepcionSiYaCheckout() {
        reservaBase.setEstado(EstadoReserva.CHECKOUT);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));

        assertThatThrownBy(() -> reservaService.cancelar(1L))
                .isInstanceOf(TransicionEstadoInvalidaException.class);
    }

    // ── SCHEDULER ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cerrarReservasVencidas() - el confirmado que no llegó pasa a NO_SHOW, libera noches y no se reembolsa")
    void marcaNoShow() {
        Reserva noLlego = reservaBase.toBuilder().estado(EstadoReserva.CONFIRMADA)
                .fechaEntrada(HOY.minusDays(2)).build();
        when(reservaRepository.findByEstadoAndFechaEntradaBefore(eq(EstadoReserva.CONFIRMADA), any()))
                .thenReturn(List.of(noLlego));
        when(reservaRepository.findPendientesVencidas(any(), any())).thenReturn(List.of());

        reservaService.cerrarReservasVencidas();

        assertThat(noLlego.getEstado()).isEqualTo(EstadoReserva.NO_SHOW);
        verify(nocheRepository).liberarNoches(noLlego.getId());
        verifyNoInteractions(outbox); // sin ReservaCancelada: Pagos no reembolsa un no-show
    }

    @Test
    @DisplayName("cerrarReservasVencidas() - la pendiente sin pago se cancela y publica ReservaCancelada")
    void cancelaPendienteSinPago() {
        when(reservaRepository.findByEstadoAndFechaEntradaBefore(eq(EstadoReserva.CONFIRMADA), any()))
                .thenReturn(List.of());
        when(reservaRepository.findPendientesVencidas(any(), eq(HOY))).thenReturn(List.of(reservaBase));

        reservaService.cerrarReservasVencidas();

        assertThat(reservaBase.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        verify(nocheRepository).liberarNoches(1L);
        verify(outbox).registrar(eq(TiposEvento.RESERVA_CANCELADA), any(), anyMap());
    }

    @Test
    @DisplayName("cerrarReservasVencidas() - nunca hace check-in: eso lo hace recepción")
    void noHaceCheckinAutomatico() {
        when(reservaRepository.findByEstadoAndFechaEntradaBefore(any(), any())).thenReturn(List.of());
        when(reservaRepository.findPendientesVencidas(any(), any())).thenReturn(List.of());

        reservaService.cerrarReservasVencidas();

        verify(reservaRepository, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("cerrarReservasVencidas() - el no-show espera hasta el check-out (12:00) del día siguiente")
    void graciaNoShow() {
        when(reservaRepository.findByEstadoAndFechaEntradaBefore(any(), any())).thenReturn(List.of());
        when(reservaRepository.findPendientesVencidas(any(), any())).thenReturn(List.of());

        reservaService.cerrarReservasVencidas();

        ArgumentCaptor<LocalDate> limite = ArgumentCaptor.forClass(LocalDate.class);
        verify(reservaRepository).findByEstadoAndFechaEntradaBefore(eq(EstadoReserva.CONFIRMADA), limite.capture());
        assertThat(limite.getValue()).isEqualTo(
                java.time.ZonedDateTime.now(ReservaService.ZONA_HOTEL).minusHours(12).toLocalDate());
    }

    // ── ANTI-OVERBOOKING Y EVENTOS ────────────────────────────────────────────

    @Test
    @DisplayName("crear() - bloquea una noche por día de estadía y publica ReservaCreada")
    void crearBloqueaNochesYPublica() {
        habitacionDisponible();
        when(reservaRepository.existeConflicto(anyLong(), any(), any())).thenReturn(false);
        when(reservaRepository.existsByCodigo(anyString())).thenReturn(false);
        when(reservaRepository.save(any())).thenReturn(reservaBase);

        reservaService.crear(buildRequest(2));

        ArgumentCaptor<List<com.staysync.reservas.model.ReservaNoche>> noches = ArgumentCaptor.forClass(List.class);
        verify(nocheRepository).saveAllAndFlush(noches.capture());
        assertThat(noches.getValue()).hasSize(2); // 2 noches: entrada+5 y entrada+6
        verify(outbox).registrar(eq(TiposEvento.RESERVA_CREADA), any(), anyMap());
    }

    @Test
    @DisplayName("crear() - si otra reserva tomó la noche a la vez, responde habitación no disponible")
    void crearConcurrenteRechazado() {
        habitacionDisponible();
        when(reservaRepository.existeConflicto(anyLong(), any(), any())).thenReturn(false);
        when(reservaRepository.existsByCodigo(anyString())).thenReturn(false);
        when(reservaRepository.save(any())).thenReturn(reservaBase);
        when(nocheRepository.saveAllAndFlush(anyList()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_noche"));

        assertThatThrownBy(() -> reservaService.crear(buildRequest(2)))
                .isInstanceOf(HabitacionNoDisponibleException.class);
        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("confirmarPorPago() - confirma la reserva pendiente")
    void confirmarPorPagoConfirma() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));
        when(reservaRepository.save(any())).thenReturn(reservaBase);

        reservaService.confirmarPorPago(1L);

        assertThat(reservaBase.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
    }

    @Test
    @DisplayName("confirmarPorPago() - un pago repetido sobre una reserva confirmada no cambia nada")
    void confirmarPorPagoIdempotente() {
        reservaBase.setEstado(EstadoReserva.CONFIRMADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));

        reservaService.confirmarPorPago(1L);

        verify(reservaRepository, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    @DisplayName("confirmarPorPago() - reserva inexistente se ignora sin error")
    void confirmarPorPagoInexistente() {
        when(reservaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> reservaService.confirmarPorPago(99L));
    }

    // ── GET RESERVAS HOY ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getReservasHoy() - debe separar pendientes de check-in y check-out activos")
    void debeRetornarReservasDelDia() {
        Reserva checkinPendiente = reservaBase.toBuilder()
                .estado(EstadoReserva.CONFIRMADA)
                .fechaEntrada(LocalDate.now())
                .build();
        Reserva checkoutActivo = reservaBase.toBuilder()
                .id(2L).codigo("RES-ZZ999999")
                .estado(EstadoReserva.CHECKIN)
                .build();

        // Llegadas de hoy y atrasadas: fechaEntrada < mañana
        when(reservaRepository.findByEstadoAndFechaEntradaBefore(EstadoReserva.CONFIRMADA, HOY.plusDays(1)))
                .thenReturn(List.of(checkinPendiente));
        when(reservaRepository.findByEstado(EstadoReserva.CHECKIN))
                .thenReturn(List.of(checkoutActivo));

        Map<String, Object> result = reservaService.getReservasHoy();

        assertThat(result).containsKey("pendientesCheckin").containsKey("pendientesCheckout");
        @SuppressWarnings("unchecked")
        List<ReservaResponse> checkins = (List<ReservaResponse>) result.get("pendientesCheckin");
        @SuppressWarnings("unchecked")
        List<ReservaResponse> checkouts = (List<ReservaResponse>) result.get("pendientesCheckout");
        assertThat(checkins).hasSize(1);
        assertThat(checkouts).hasSize(1);
    }

    @Test
    @DisplayName("getReservasHoy() - debe retornar listas vacías si no hay reservas")
    void debeRetornarListasVaciasConSinReservas() {
        when(reservaRepository.findByEstadoAndFechaEntradaBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservaRepository.findByEstado(any()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = reservaService.getReservasHoy();

        @SuppressWarnings("unchecked")
        List<?> checkins = (List<?>) result.get("pendientesCheckin");
        assertThat(checkins).isEmpty();
    }

    // ── MODIFICAR Y RANGO ─────────────────────────────────────────────────────

    private static com.staysync.reservas.dto.request.ModificarReservaRequest cambio(LocalDate e, LocalDate s, Long hab) {
        var r = new com.staysync.reservas.dto.request.ModificarReservaRequest();
        r.setFechaEntrada(e);
        r.setFechaSalida(s);
        r.setHabitacionId(hab);
        return r;
    }

    @Test
    @DisplayName("modificar() - recalcula el precio y vuelve a bloquear las noches en la otra habitación")
    void modificaFechasYHabitacion() {
        reservaBase.setEstado(EstadoReserva.CONFIRMADA);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));
        when(reservaRepository.existeConflictoExcluyendo(eq(2L), any(), any(), eq(1L))).thenReturn(false);
        habitacionDisponible(); // 100 por noche
        when(reservaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReservaResponse r = reservaService.modificar(1L, cambio(HOY.plusDays(10), HOY.plusDays(13), 2L));

        assertThat(r.getHabitacionId()).isEqualTo(2L);
        assertThat(r.getPrecioTotal()).isEqualByComparingTo("300");
        verify(nocheRepository).liberarNoches(1L);
        ArgumentCaptor<List<com.staysync.reservas.model.ReservaNoche>> noches = ArgumentCaptor.forClass(List.class);
        verify(nocheRepository).saveAllAndFlush(noches.capture());
        assertThat(noches.getValue()).hasSize(3).allMatch(n -> n.getHabitacionId().equals(2L));
    }

    @Test
    @DisplayName("modificar() - una reserva ya iniciada (CHECKIN) no se modifica")
    void noModificaReservaIniciada() {
        reservaBase.setEstado(EstadoReserva.CHECKIN);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));

        assertThatThrownBy(() -> reservaService.modificar(1L, cambio(HOY.plusDays(1), HOY.plusDays(2), null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CHECKIN");
        verifyNoInteractions(nocheRepository);
    }

    @Test
    @DisplayName("modificar() - si las nuevas fechas chocan con otra reserva no se toca nada")
    void noModificaConConflicto() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaBase));
        when(reservaRepository.existeConflictoExcluyendo(anyLong(), any(), any(), eq(1L))).thenReturn(true);

        assertThatThrownBy(() -> reservaService.modificar(1L, cambio(HOY.plusDays(1), HOY.plusDays(2), null)))
                .isInstanceOf(HabitacionNoDisponibleException.class);
        verify(nocheRepository, never()).liberarNoches(any());
        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("listarEnRango() - rechaza rangos vacíos o de más de 400 días")
    void rangoValido() {
        assertThatThrownBy(() -> reservaService.listarEnRango(HOY, HOY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservaService.listarEnRango(HOY, HOY.plusDays(401))).isInstanceOf(IllegalArgumentException.class);
        when(reservaRepository.findEnRango(HOY, HOY.plusDays(30))).thenReturn(List.of(reservaBase));
        assertThat(reservaService.listarEnRango(HOY, HOY.plusDays(30))).hasSize(1);
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private void habitacionDisponible() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("precioPorNoche", 100.0, "capacidad", 4));
    }

    private CrearReservaRequest buildRequest(int numHuespedes) {
        CrearReservaRequest req = new CrearReservaRequest();
        req.setUsuarioId(1L);
        req.setHabitacionId(1L);
        req.setFechaEntrada(LocalDate.now().plusDays(5));
        req.setFechaSalida(LocalDate.now().plusDays(7));
        req.setNumHuespedes(numHuespedes);
        req.setFuente(FuenteReserva.DIRECTO);
        return req;
    }
}
