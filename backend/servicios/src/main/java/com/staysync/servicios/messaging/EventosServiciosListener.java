package com.staysync.servicios.messaging;

import com.staysync.eventos.EventoDominio;
import com.staysync.eventos.ProcesadorEventos;
import com.staysync.eventos.TiposEvento;
import com.staysync.servicios.service.AseoService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Consumer;

/** Cola servicios-q: agenda, cancela o libera las tareas de aseo según la reserva. */
@Component
@RequiredArgsConstructor
public class EventosServiciosListener {

    private final ProcesadorEventos procesador;
    private final AseoService aseoService;

    @SqsListener("${staysync.eventos.cola}")
    public void recibir(String mensaje) {
        procesador.procesar(mensaje, manejadores());
    }

    Map<String, Consumer<EventoDominio>> manejadores() {
        return Map.of(
                TiposEvento.RESERVA_CONFIRMADA, e -> aseoService.programar(
                        e.payloadLong("reservaId"), e.payloadLong("habitacionId"), fechaSalida(e)),
                TiposEvento.RESERVA_CANCELADA, e -> aseoService.cancelar(e.payloadLong("reservaId")),
                TiposEvento.RESERVA_CHECKOUT, e -> aseoService.marcarPendiente(
                        e.payloadLong("reservaId"), e.payloadLong("habitacionId"), fechaSalida(e)));
    }

    private static LocalDate fechaSalida(EventoDominio e) {
        return LocalDate.parse(e.payloadString("fechaSalida"));
    }
}
