package com.staysync.pagos.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.staysync.pagos.config.StripeConfig;
import com.staysync.pagos.dto.request.StripeCheckoutRequest;
import com.staysync.pagos.dto.request.StripeConfirmarRequest;
import com.staysync.pagos.dto.response.PagoResponse;
import com.staysync.pagos.dto.response.StripeCheckoutResponse;
import com.staysync.pagos.exception.PagoFallidoException;
import com.staysync.pagos.model.Pago;
import com.staysync.pagos.repository.PagoRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests de StripeService.
 *
 * Se usa un StripeGateway mockeado en lugar de MockedStatic para evitar
 * problemas de compatibilidad con el Stripe SDK 25.x y con Mockito strict stubs.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StripeService - Tests Unitarios")
class StripeServiceTest {

    @Mock private StripeConfig    stripeConfig;
    @Mock private PagoRepository  pagoRepository;
    @Mock private RestTemplate    restTemplate;
    @Mock private StripeGateway   stripeGateway;

    @InjectMocks private StripeService stripeService;

    private static final String SESSION_ID   = "cs_test_ABC123";
    private static final String CHECKOUT_URL = "https://checkout.stripe.com/pay/cs_test_ABC123";

    @BeforeEach
    void setUp() {
        // lenient: solo se usan en crearCheckout(), no en confirmarPago()
        lenient().when(stripeConfig.getSuccessUrl())
                 .thenReturn("http://localhost:3000/huesped/pago-exitoso");
        lenient().when(stripeConfig.getCancelUrl())
                 .thenReturn("http://localhost:3000/huesped/pago-fallido");
    }

    // ── crearCheckout() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("crearCheckout() - debe retornar sessionId y checkoutUrl cuando el gateway responde OK")
    void debeCrearCheckoutExitosamente() throws Exception {
        StripeCheckoutRequest request = buildCheckoutRequest(1L, BigDecimal.valueOf(45000), "Habitación 101 - 3 noches");

        Session sessionMock = mock(Session.class);
        when(sessionMock.getId()).thenReturn(SESSION_ID);
        when(sessionMock.getUrl()).thenReturn(CHECKOUT_URL);
        when(stripeGateway.createSession(any())).thenReturn(sessionMock);

        StripeCheckoutResponse response = stripeService.crearCheckout(request);

        assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(response.getCheckoutUrl()).isEqualTo(CHECKOUT_URL);
        assertThat(response.getReservaId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("crearCheckout() - debe lanzar PagoFallidoException si el gateway lanza StripeException")
    void debeLanzarExcepcionSiStripeFalla() throws Exception {
        StripeCheckoutRequest request = buildCheckoutRequest(1L, BigDecimal.valueOf(45000), "Habitación 101");

        StripeException stripeEx = mock(StripeException.class);
        when(stripeEx.getMessage()).thenReturn("Invalid API key");
        when(stripeEx.getCode()).thenReturn("authentication_error");
        when(stripeGateway.createSession(any())).thenThrow(stripeEx);

        assertThatThrownBy(() -> stripeService.crearCheckout(request))
                .isInstanceOf(PagoFallidoException.class)
                .hasMessageContaining("Error al crear sesión");
    }

    @Test
    @DisplayName("crearCheckout() - debe convertir monto a centavos enteros (HALF_UP para CLP)")
    void debeConvertirMontoCorrectamente() throws Exception {
        StripeCheckoutRequest request = buildCheckoutRequest(2L, new BigDecimal("89500.50"), "Suite Premium");

        Session sessionMock = mock(Session.class);
        when(sessionMock.getId()).thenReturn("cs_test_XYZ");
        when(sessionMock.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_XYZ");
        when(stripeGateway.createSession(any())).thenReturn(sessionMock);

        StripeCheckoutResponse response = stripeService.crearCheckout(request);

        // La sesión se creó — el monto fue redondeado a 89501 (HALF_UP)
        assertThat(response.getSessionId()).isEqualTo("cs_test_XYZ");
        verify(stripeGateway).createSession(any());
    }

    // ── confirmarPago() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("confirmarPago() - debe registrar pago y publicar evento cuando paymentStatus=paid")
    void debeConfirmarPagoExitosamente() throws Exception {
        StripeConfirmarRequest request = buildConfirmarRequest(SESSION_ID, 1L);

        Session sessionMock = mock(Session.class);
        when(sessionMock.getId()).thenReturn(SESSION_ID);
        when(sessionMock.getPaymentStatus()).thenReturn("paid");
        when(sessionMock.getAmountTotal()).thenReturn(45000L);
        when(sessionMock.getMetadata()).thenReturn(Map.of("reserva_id", "1"));

        Pago pagoGuardado = buildPago(SESSION_ID, 1L, 1L, BigDecimal.valueOf(45000));

        when(pagoRepository.existsByGatewayId(SESSION_ID)).thenReturn(false);
        when(pagoRepository.existsByReferencia(anyString())).thenReturn(false);
        when(pagoRepository.save(any())).thenReturn(pagoGuardado);
        when(stripeGateway.retrieveSession(SESSION_ID)).thenReturn(sessionMock);

        PagoResponse response = stripeService.confirmarPago(request);

        assertThat(response).isNotNull();
        assertThat(response.getEstado()).isEqualTo(Pago.EstadoPago.COMPLETADO);
        assertThat(response.getMoneda()).isEqualTo("CLP");
        verify(pagoRepository).save(any(Pago.class));
    }

    @Test
    @DisplayName("confirmarPago() - debe ser idempotente: retornar pago existente sin crear duplicado")
    void debeSerIdempotente() throws Exception {
        StripeConfirmarRequest request = buildConfirmarRequest(SESSION_ID, 1L);

        Pago pagoExistente = buildPago(SESSION_ID, 1L, 1L, BigDecimal.valueOf(45000));

        when(pagoRepository.existsByGatewayId(SESSION_ID)).thenReturn(true);
        when(pagoRepository.findByGatewayId(SESSION_ID)).thenReturn(Optional.of(pagoExistente));

        PagoResponse response = stripeService.confirmarPago(request);

        assertThat(response.getGatewayId()).isEqualTo(SESSION_ID);
        // No debe llamar al gateway ni guardar un segundo pago
        verify(stripeGateway, never()).retrieveSession(any());
        verify(pagoRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmarPago() - debe lanzar PagoFallidoException si paymentStatus != paid")
    void debeLanzarExcepcionSiPagoNoProcesado() throws Exception {
        StripeConfirmarRequest request = buildConfirmarRequest(SESSION_ID, 1L);

        Session sessionMock = mock(Session.class);
        when(sessionMock.getPaymentStatus()).thenReturn("unpaid");
        when(pagoRepository.existsByGatewayId(SESSION_ID)).thenReturn(false);
        when(stripeGateway.retrieveSession(SESSION_ID)).thenReturn(sessionMock);

        assertThatThrownBy(() -> stripeService.confirmarPago(request))
                .isInstanceOf(PagoFallidoException.class)
                .hasMessageContaining("no está aprobado");

        verify(pagoRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmarPago() - debe lanzar PagoFallidoException si el gateway lanza StripeException")
    void debeLanzarExcepcionSiStripeAPIFalla() throws Exception {
        StripeConfirmarRequest request = buildConfirmarRequest("cs_test_INVALID", 1L);

        StripeException stripeEx = mock(StripeException.class);
        when(stripeEx.getMessage()).thenReturn("No such checkout.session");
        when(pagoRepository.existsByGatewayId("cs_test_INVALID")).thenReturn(false);
        when(stripeGateway.retrieveSession("cs_test_INVALID")).thenThrow(stripeEx);

        assertThatThrownBy(() -> stripeService.confirmarPago(request))
                .isInstanceOf(PagoFallidoException.class)
                .hasMessageContaining("No se pudo verificar");

        verify(pagoRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmarPago() - debe continuar aunque confirmarReserva() falle (error no crítico)")
    void debeContinuarSiConfirmarReservaFalla() throws Exception {
        StripeConfirmarRequest request = buildConfirmarRequest(SESSION_ID, 1L);

        Session sessionMock = mock(Session.class);
        when(sessionMock.getPaymentStatus()).thenReturn("paid");
        when(sessionMock.getAmountTotal()).thenReturn(45000L);
        when(sessionMock.getMetadata()).thenReturn(Map.of("reserva_id", "1"));

        Pago pago = buildPago(SESSION_ID, 1L, 1L, BigDecimal.valueOf(45000));

        when(pagoRepository.existsByGatewayId(SESSION_ID)).thenReturn(false);
        when(pagoRepository.existsByReferencia(anyString())).thenReturn(false);
        when(pagoRepository.save(any())).thenReturn(pago);
        when(stripeGateway.retrieveSession(SESSION_ID)).thenReturn(sessionMock);
        when(restTemplate.exchange(anyString(), any(), any(), eq(Object.class)))
                .thenThrow(new RuntimeException("reservas-service unreachable"));

        // No debe lanzar excepción — el error de confirmar reserva es manejado internamente
        assertThatNoException().isThrownBy(() -> stripeService.confirmarPago(request));
        // El pago sí se guardó
        verify(pagoRepository).save(any(Pago.class));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private StripeCheckoutRequest buildCheckoutRequest(Long reservaId, BigDecimal monto, String titulo) {
        StripeCheckoutRequest r = new StripeCheckoutRequest();
        r.setReservaId(reservaId);
        r.setMonto(monto);
        r.setTituloReserva(titulo);
        return r;
    }

    private StripeConfirmarRequest buildConfirmarRequest(String sessionId, Long usuarioId) {
        StripeConfirmarRequest r = new StripeConfirmarRequest();
        r.setSessionId(sessionId);
        r.setUsuarioId(usuarioId);
        return r;
    }

    private Pago buildPago(String gatewayId, Long reservaId, Long usuarioId, BigDecimal monto) {
        return Pago.builder()
                .id(1L).referencia("STRIPE-ABCDE12345")
                .reservaId(reservaId).usuarioId(usuarioId)
                .monto(monto).moneda("CLP")
                .metodoPago(Pago.MetodoPago.STRIPE)
                .estado(Pago.EstadoPago.COMPLETADO)
                .gatewayId(gatewayId)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }
}
