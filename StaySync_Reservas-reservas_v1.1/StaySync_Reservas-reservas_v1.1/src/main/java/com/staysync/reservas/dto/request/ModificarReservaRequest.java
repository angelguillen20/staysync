package com.staysync.reservas.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Nuevas fechas y, opcionalmente, otra habitación para una reserva")
public class ModificarReservaRequest {

    @NotNull(message = "La fecha de entrada es obligatoria")
    private LocalDate fechaEntrada;

    @NotNull(message = "La fecha de salida es obligatoria")
    private LocalDate fechaSalida;

    @Schema(description = "Vacío = la misma habitación")
    private Long habitacionId;
}
