package com.staysync.pagos.messaging;

import com.staysync.eventos.EventoDominio;
import com.staysync.eventos.ProcesadorEventos;
import com.staysync.eventos.TiposEvento;
import com.staysync.pagos.service.PagoService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Consumer;

/** Cola pagos-q: si se cancela una reserva ya pagada, se reembolsa automáticamente. */
@Component
@RequiredArgsConstructor
public class EventosPagoListener {

    private final ProcesadorEventos procesador;
    private final PagoService pagoService;

    @SqsListener("${staysync.eventos.cola}")
    public void recibir(String mensaje) {
        procesador.procesar(mensaje, manejadores());
    }

    Map<String, Consumer<EventoDominio>> manejadores() {
        return Map.of(TiposEvento.RESERVA_CANCELADA,
                e -> pagoService.reembolsarPorCancelacion(e.payloadLong("reservaId")));
    }
}
