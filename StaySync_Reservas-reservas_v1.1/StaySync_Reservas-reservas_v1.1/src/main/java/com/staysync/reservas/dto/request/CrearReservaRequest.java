package com.staysync.reservas.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "Datos para crear una nueva reserva")
public class CrearReservaRequest {

    @NotNull(message = "El ID de usuario es obligatorio")
    private Long usuarioId;

    @NotNull(message = "El ID de habitación es obligatorio")
    private Long habitacionId;

    // Que no sea pasada lo valida ReservaService con la fecha del hotel: @FutureOrPresent usa
    // la del servidor (UTC) y rechazaba reservar para hoy desde las 21:00 de Chile.
    @NotNull(message = "La fecha de entrada es obligatoria")
    @Schema(example = "2025-03-01")
    private LocalDate fechaEntrada;

    @NotNull(message = "La fecha de salida es obligatoria")
    @Schema(example = "2025-03-05")
    private LocalDate fechaSalida;

    @Min(value = 1, message = "Debe haber al menos 1 huésped")
    @Max(value = 10, message = "No se permiten más de 10 huéspedes por reserva")
    private Integer numHuespedes = 1;

    @Size(max = 500, message = "Las notas no pueden superar 500 caracteres")
    private String notas;

    /** Contacto del huésped para las notificaciones; lo completa el BFF. */
    @Email
    @Size(max = 255)
    private String huespedEmail;

    @Size(max = 200)
    private String huespedNombre;

    private com.staysync.reservas.model.Reserva.FuenteReserva fuente =
            com.staysync.reservas.model.Reserva.FuenteReserva.DIRECTO;

    @Valid
    private List<HuespedAdicionalRequest> huespedesAdicionales = new ArrayList<>();

    @Data
    @Schema(description = "Datos de un huésped adicional")
    public static class HuespedAdicionalRequest {

        @NotBlank(message = "El nombre del huésped adicional es obligatorio")
        @Size(max = 100)
        private String nombre;

        @NotBlank(message = "El apellido del huésped adicional es obligatorio")
        @Size(max = 100)
        private String apellido;

        @Size(max = 50)
        private String documento;
    }
}
