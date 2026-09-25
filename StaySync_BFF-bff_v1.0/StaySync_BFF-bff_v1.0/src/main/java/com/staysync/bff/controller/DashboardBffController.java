package com.staysync.bff.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.staysync.bff.client.HabitacionesClient;
import com.staysync.bff.client.ReservasClient;
import com.staysync.bff.client.WeatherClient;
import com.staysync.bff.dto.dashboard.DashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/bff/dashboard")
@RequiredArgsConstructor
@Tag(name = "BFF Dashboard", description = "Vista agregada para el panel de control")
public class DashboardBffController {

    private final ReservasClient reservasClient;
    private final HabitacionesClient habitacionesClient;
    private final WeatherClient weatherClient;
    private final ObjectMapper mapper;
    private final ExecutorService bffTaskExecutor;

    @Operation(summary = "Obtener datos agregados del dashboard",
               description = "Agrega información de reservas y habitaciones en una sola llamada")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestHeader("Authorization") String authHeader) {

        // Las 3 llamadas a downstream son independientes entre sí: se disparan en paralelo
        // sobre un pool acotado y la latencia total pasa a ser max(latencias) en vez de la suma.
        var reservasFuture = CompletableFuture.supplyAsync(
                () -> reservasClient.listar(authHeader), bffTaskExecutor);
        var habitacionesFuture = CompletableFuture.supplyAsync(
                () -> habitacionesClient.listarTodas(authHeader), bffTaskExecutor);
        var disponiblesFuture = CompletableFuture.supplyAsync(
                () -> habitacionesClient.listarDisponibles(authHeader), bffTaskExecutor);
        var climaFuture = CompletableFuture.supplyAsync(
                weatherClient::obtenerClima, bffTaskExecutor);

        try {
            CompletableFuture.allOf(reservasFuture, habitacionesFuture, disponiblesFuture, climaFuture).join();
        } catch (CompletionException ex) {
            // Desenvuelve la causa real para que GlobalExceptionHandler siga mapeando
            // DownstreamServiceException / HttpClientErrorException al status HTTP correcto.
            if (ex.getCause() instanceof RuntimeException re) throw re;
            throw ex;
        }

        List<Map<String, Object>> reservas     = parseList(reservasFuture.join().getBody());
        List<Map<String, Object>> habitaciones = parseList(habitacionesFuture.join().getBody());
        List<Map<String, Object>> disponibles  = parseList(disponiblesFuture.join().getBody());

        // Calcular stats de reservas agrupando por estado
        long pendientes  = contarPorEstado(reservas, "PENDIENTE");
        long confirmadas = contarPorEstado(reservas, "CONFIRMADA");
        long enCheckin   = contarPorEstado(reservas, "CHECKIN");
        long canceladas  = contarPorEstado(reservas, "CANCELADA");

        // Stats de habitaciones
        long totalHabs  = habitaciones.size();
        long disponiblesN = disponibles.size();
        long ocupadas   = contarPorEstado(habitaciones, "OCUPADA");
        long mantenimiento = contarPorEstado(habitaciones, "MANTENIMIENTO");

        // Últimas 5 reservas recientes
        List<Map<String, Object>> recientes = reservas.stream()
                .limit(5)
                .toList();

        DashboardResponse response = DashboardResponse.builder()
                .reservas(DashboardResponse.ReservasStats.builder()
                        .total(reservas.size())
                        .pendientes(pendientes)
                        .confirmadas(confirmadas)
                        .enCheckin(enCheckin)
                        .canceladas(canceladas)
                        .build())
                .habitaciones(DashboardResponse.HabitacionesStats.builder()
                        .totalHabitaciones(totalHabs)
                        .disponibles(disponiblesN)
                        .ocupadas(ocupadas)
                        .enMantenimiento(mantenimiento)
                        .build())
                .reservasRecientes(recientes)
                .clima(climaFuture.join())
                .build();

        return ResponseEntity.ok(response);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseList(Object body) {
        if (body instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private long contarPorEstado(List<Map<String, Object>> items, String estado) {
        return items.stream()
                .filter(m -> estado.equals(m.get("estado")))
                .count();
    }
}
