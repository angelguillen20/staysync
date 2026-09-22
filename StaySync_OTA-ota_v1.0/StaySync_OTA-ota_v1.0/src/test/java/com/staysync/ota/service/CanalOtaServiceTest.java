package com.staysync.ota.service;

import com.staysync.ota.exception.CanalOtaNotFoundException;
import com.staysync.ota.model.CanalOta;
import com.staysync.ota.model.ReservaOta;
import com.staysync.ota.repository.CanalOtaRepository;
import com.staysync.ota.repository.ReservaOtaRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CanalOtaService - Tests Unitarios")
class CanalOtaServiceTest {

    @Mock private CanalOtaRepository canalRepository;
    @Mock private ReservaOtaRepository reservaOtaRepository;

    @InjectMocks private CanalOtaService canalOtaService;

    private CanalOta canalBooking;

    @BeforeEach
    void setUp() {
        canalBooking = CanalOta.builder()
                .id(1L).nombre("Booking.com").codigo("BOOKING")
                .comision(BigDecimal.valueOf(15)).activo(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("listarActivos() - debe retornar canales activos")
    void debeListarActivos() {
        when(canalRepository.findByActivoTrue()).thenReturn(List.of(canalBooking));

        List<CanalOta> result = canalOtaService.listarActivos();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCodigo()).isEqualTo("BOOKING");
    }

    @Test
    @DisplayName("obtenerPorId() - debe retornar canal existente")
    void debeRetornarCanalPorId() {
        when(canalRepository.findById(1L)).thenReturn(Optional.of(canalBooking));

        CanalOta result = canalOtaService.obtenerPorId(1L);

        assertThat(result).isNotNull();
        assertThat(result.getNombre()).isEqualTo("Booking.com");
    }

    @Test
    @DisplayName("obtenerPorId() - debe lanzar excepción si no existe")
    void debeLanzarExcepcionNoExiste() {
        when(canalRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> canalOtaService.obtenerPorId(99L))
                .isInstanceOf(CanalOtaNotFoundException.class);
    }

    @Test
    @DisplayName("registrarReservaExterna() - debe crear nueva reserva OTA si no existe")
    void debeRegistrarNuevaReservaOta() {
        Map<String, Object> payload = Map.of("guestName", "John Doe", "roomType", "Deluxe");

        ReservaOta nuevaReserva = ReservaOta.builder()
                .id(1L).canal(canalBooking).reservaIdExt("BK-12345")
                .sincronizada(false).payloadEntrada(payload)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        when(canalRepository.findById(1L)).thenReturn(Optional.of(canalBooking));
        when(reservaOtaRepository.findByCanalIdAndReservaIdExt(1L, "BK-12345"))
                .thenReturn(Optional.empty());
        when(reservaOtaRepository.save(any())).thenReturn(nuevaReserva);

        ReservaOta result = canalOtaService.registrarReservaExterna(1L, "BK-12345", payload);

        assertThat(result).isNotNull();
        assertThat(result.getReservaIdExt()).isEqualTo("BK-12345");
        verify(reservaOtaRepository).save(any(ReservaOta.class));
    }

    @Test
    @DisplayName("registrarReservaExterna() - debe retornar existente si ya está registrada")
    void debeRetornarExistenteEnDuplicado() {
        ReservaOta existente = ReservaOta.builder()
                .id(1L).canal(canalBooking).reservaIdExt("BK-12345").sincronizada(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();

        when(canalRepository.findById(1L)).thenReturn(Optional.of(canalBooking));
        when(reservaOtaRepository.findByCanalIdAndReservaIdExt(1L, "BK-12345"))
                .thenReturn(Optional.of(existente));

        ReservaOta result = canalOtaService.registrarReservaExterna(1L, "BK-12345", Map.of());

        assertThat(result.getSincronizada()).isTrue();
        verify(reservaOtaRepository, never()).save(any());
    }

    @Test
    @DisplayName("sincronizarEstadoHabitacion() - debe consultar los canales activos por cada cambio de estado")
    void debeSincronizarEstadoHabitacionConCanalesActivos() {
        when(canalRepository.findByActivoTrue()).thenReturn(List.of(canalBooking));

        canalOtaService.sincronizarEstadoHabitacion(10L, "DISPONIBLE", "OCUPADA");

        verify(canalRepository).findByActivoTrue();
    }

    @Test
    @DisplayName("sincronizarEstadoHabitacion() - no debe fallar si no hay canales activos")
    void debeSincronizarSinCanalesActivosSinError() {
        when(canalRepository.findByActivoTrue()).thenReturn(List.of());

        canalOtaService.sincronizarEstadoHabitacion(10L, "OCUPADA", "DISPONIBLE");

        verify(canalRepository).findByActivoTrue();
    }
}
