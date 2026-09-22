package com.staysync.servicios.service;

import com.staysync.servicios.dto.request.SolicitudServicioRequest;
import com.staysync.servicios.dto.response.ServicioResponse;
import com.staysync.servicios.dto.response.SolicitudServicioResponse;
import com.staysync.servicios.exception.ServicioNotFoundException;
import com.staysync.servicios.model.CategoriaServicio;
import com.staysync.servicios.model.Servicio;
import com.staysync.servicios.model.SolicitudServicio;
import com.staysync.servicios.repository.ServicioRepository;
import com.staysync.servicios.repository.SolicitudServicioRepository;
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
@DisplayName("ServicioService - Tests Unitarios")
class ServicioServiceTest {

    @Mock private ServicioRepository servicioRepository;
    @Mock private SolicitudServicioRepository solicitudRepository;

    @InjectMocks private ServicioService servicioService;

    private CategoriaServicio categoria;
    private Servicio servicio;

    @BeforeEach
    void setUp() {
        categoria = CategoriaServicio.builder().id(1L).nombre("Spa").activa(true).build();
        servicio = Servicio.builder()
                .id(1L).categoria(categoria).nombre("Masaje 60min")
                .precio(BigDecimal.valueOf(60)).disponible(true).requiereReserva(true)
                .createdAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("listarDisponibles() - debe retornar servicios disponibles")
    void debeListarDisponibles() {
        when(servicioRepository.findByDisponibleTrue()).thenReturn(List.of(servicio));

        List<ServicioResponse> result = servicioService.listarDisponibles();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNombre()).isEqualTo("Masaje 60min");
    }

    @Test
    @DisplayName("obtenerPorId() - debe retornar servicio existente")
    void debeRetornarServicio() {
        when(servicioRepository.findById(1L)).thenReturn(Optional.of(servicio));

        ServicioResponse response = servicioService.obtenerPorId(1L);

        assertThat(response).isNotNull();
        assertThat(response.getPrecio()).isEqualTo(BigDecimal.valueOf(60));
    }

    @Test
    @DisplayName("obtenerPorId() - debe lanzar excepción si no existe")
    void debeLanzarExcepcionNoExiste() {
        when(servicioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicioService.obtenerPorId(99L))
                .isInstanceOf(ServicioNotFoundException.class);
    }

    @Test
    @DisplayName("crearSolicitud() - debe crear solicitud y calcular precio total")
    void debeCrearSolicitud() {
        SolicitudServicioRequest request = new SolicitudServicioRequest();
        request.setReservaId(1L);
        request.setUsuarioId(1L);
        request.setServicioId(1L);
        request.setCantidad(2);
        request.setFechaServicio(LocalDateTime.now().plusDays(1));

        SolicitudServicio solicitud = SolicitudServicio.builder()
                .id(1L).reservaId(1L).usuarioId(1L).servicio(servicio)
                .cantidad(2).precioTotal(BigDecimal.valueOf(120))
                .estado(SolicitudServicio.EstadoSolicitud.PENDIENTE)
                .fechaServicio(LocalDateTime.now().plusDays(1))
                .createdAt(LocalDateTime.now()).build();

        when(servicioRepository.findById(1L)).thenReturn(Optional.of(servicio));
        when(solicitudRepository.save(any())).thenReturn(solicitud);

        SolicitudServicioResponse response = servicioService.crearSolicitud(request);

        assertThat(response).isNotNull();
        assertThat(response.getPrecioTotal()).isEqualTo(BigDecimal.valueOf(120));
        verify(solicitudRepository).save(any(SolicitudServicio.class));
    }

    @Test
    @DisplayName("listarSolicitudesPorReserva() - debe retornar solicitudes de la reserva")
    void debeListarSolicitudesPorReserva() {
        SolicitudServicio solicitud = SolicitudServicio.builder()
                .id(1L).reservaId(5L).servicio(servicio)
                .cantidad(1).precioTotal(BigDecimal.valueOf(60))
                .estado(SolicitudServicio.EstadoSolicitud.PENDIENTE)
                .fechaServicio(LocalDateTime.now().plusDays(1))
                .createdAt(LocalDateTime.now()).build();

        when(solicitudRepository.findByReservaId(5L)).thenReturn(List.of(solicitud));

        List<SolicitudServicioResponse> result = servicioService.listarSolicitudesPorReserva(5L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReservaId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("listarSolicitudesPorUsuario() - debe retornar solicitudes del usuario")
    void debeListarSolicitudesPorUsuario() {
        SolicitudServicio solicitud = SolicitudServicio.builder()
                .id(1L).reservaId(1L).usuarioId(7L).servicio(servicio)
                .cantidad(1).precioTotal(BigDecimal.valueOf(60))
                .estado(SolicitudServicio.EstadoSolicitud.PENDIENTE)
                .fechaServicio(LocalDateTime.now().plusDays(1))
                .createdAt(LocalDateTime.now()).build();

        when(solicitudRepository.findByUsuarioId(7L)).thenReturn(List.of(solicitud));

        List<SolicitudServicioResponse> result = servicioService.listarSolicitudesPorUsuario(7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUsuarioId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("listarTodasSolicitudes() - debe retornar todas en orden de creación")
    void debeListarTodasSolicitudes() {
        SolicitudServicio s1 = SolicitudServicio.builder()
                .id(1L).reservaId(1L).usuarioId(1L).servicio(servicio)
                .cantidad(1).precioTotal(BigDecimal.valueOf(60))
                .estado(SolicitudServicio.EstadoSolicitud.PENDIENTE)
                .fechaServicio(LocalDateTime.now().plusDays(1))
                .createdAt(LocalDateTime.now()).build();
        SolicitudServicio s2 = SolicitudServicio.builder()
                .id(2L).reservaId(2L).usuarioId(2L).servicio(servicio)
                .cantidad(3).precioTotal(BigDecimal.valueOf(180))
                .estado(SolicitudServicio.EstadoSolicitud.COMPLETADO)
                .fechaServicio(LocalDateTime.now().plusDays(2))
                .createdAt(LocalDateTime.now()).build();

        when(solicitudRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(s1, s2));

        List<SolicitudServicioResponse> result = servicioService.listarTodasSolicitudes();

        assertThat(result).hasSize(2);
        assertThat(result.get(1).getPrecioTotal()).isEqualTo(BigDecimal.valueOf(180));
    }

    @Test
    @DisplayName("actualizarEstadoSolicitud() - debe cambiar el estado de la solicitud")
    void debeCambiarEstadoDeSolicitud() {
        SolicitudServicio solicitud = SolicitudServicio.builder()
                .id(1L).reservaId(1L).usuarioId(1L).servicio(servicio)
                .cantidad(1).precioTotal(BigDecimal.valueOf(60))
                .estado(SolicitudServicio.EstadoSolicitud.PENDIENTE)
                .fechaServicio(LocalDateTime.now().plusDays(1))
                .createdAt(LocalDateTime.now()).build();
        SolicitudServicio actualizado = SolicitudServicio.builder()
                .id(1L).reservaId(1L).usuarioId(1L).servicio(servicio)
                .cantidad(1).precioTotal(BigDecimal.valueOf(60))
                .estado(SolicitudServicio.EstadoSolicitud.EN_PROCESO)
                .fechaServicio(LocalDateTime.now().plusDays(1))
                .createdAt(LocalDateTime.now()).build();

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(solicitudRepository.save(any())).thenReturn(actualizado);

        SolicitudServicioResponse response =
                servicioService.actualizarEstadoSolicitud(1L, SolicitudServicio.EstadoSolicitud.EN_PROCESO);

        assertThat(response.getEstado()).isEqualTo(SolicitudServicio.EstadoSolicitud.EN_PROCESO);
        verify(solicitudRepository).save(solicitud);
    }

    @Test
    @DisplayName("actualizarEstadoSolicitud() - debe lanzar excepción si la solicitud no existe")
    void debeLanzarExcepcionSolicitudInexistente() {
        when(solicitudRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                servicioService.actualizarEstadoSolicitud(99L, SolicitudServicio.EstadoSolicitud.EN_PROCESO))
                .isInstanceOf(ServicioNotFoundException.class);
    }

    @Test
    @DisplayName("crearSolicitud() - debe lanzar excepción si el servicio no existe")
    void debeLanzarExcepcionServicioInexistente() {
        SolicitudServicioRequest request = new SolicitudServicioRequest();
        request.setServicioId(99L);
        request.setReservaId(1L);
        request.setUsuarioId(1L);
        request.setCantidad(1);
        request.setFechaServicio(LocalDateTime.now().plusDays(1));

        when(servicioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicioService.crearSolicitud(request))
                .isInstanceOf(ServicioNotFoundException.class);

        verify(solicitudRepository, never()).save(any());
    }
}
