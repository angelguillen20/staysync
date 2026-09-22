package com.staysync.bff.dto.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class DashboardResponse {

    private ReservasStats reservas;
    private HabitacionesStats habitaciones;
    private List<Map<String, Object>> reservasRecientes;

    @Getter
    @Builder
    public static class ReservasStats {
        private long total;
        private long pendientes;
        private long confirmadas;
        private long enCheckin;
        private long canceladas;
    }

    @Getter
    @Builder
    public static class HabitacionesStats {
        private long totalHabitaciones;
        private long disponibles;
        private long ocupadas;
        private long enMantenimiento;
    }
}
