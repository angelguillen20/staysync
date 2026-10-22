package com.staysync.reservas.messaging;

import com.staysync.eventos.EventoDominio;
import com.staysync.eventos.ProcesadorEventos;
import com.staysync.eventos.TiposEvento;
import com.staysync.reservas.service.ReservaService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Consumer;

/** Cola reservas-q: cuando Pagos cobra, la reserva pendiente pasa a CONFIRMADA. */
@Component
@RequiredArgsConstructor
public class EventosReservaListener {

    private final ProcesadorEventos procesador;
    private final ReservaService reservaService;

    @SqsListener("${staysync.eventos.cola}")
    public void recibir(String mensaje) {
        procesador.procesar(mensaje, manejadores());
    }

    Map<String, Consumer<EventoDominio>> manejadores() {
        return Map.of(TiposEvento.PAGO_EFECTUADO,
                e -> reservaService.confirmarPorPago(e.payloadLong("reservaId")));
    }
}
