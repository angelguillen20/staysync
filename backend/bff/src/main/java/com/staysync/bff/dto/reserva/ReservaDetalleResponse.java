package com.staysync.bff.dto.reserva;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReservaDetalleResponse {

    // Datos de reservas-service
    private Long id;
    private String codigoReserva;
    private String estado;
    private String fuente;
    private String fechaEntrada;
    private String fechaSalida;
    private Long habitacionId;
    private Long usuarioId;
    private BigDecimal precioTotal;
    private String notas;

    // Datos enriquecidos de habitaciones-service (null si el servicio no responde)
    private HabitacionInfo habitacion;

    // Calculado en BFF
    private long noches;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HabitacionInfo {
        private Long id;
        private String numero;
        private String tipo;
        private BigDecimal precioPorNoche;
        private String estado;
        private List<String> amenidades;
    }
}
