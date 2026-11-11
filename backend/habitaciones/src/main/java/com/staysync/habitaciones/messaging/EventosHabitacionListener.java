package com.staysync.habitaciones.messaging;

import com.staysync.eventos.EventoDominio;
import com.staysync.eventos.ProcesadorEventos;
import com.staysync.eventos.TiposEvento;
import com.staysync.habitaciones.model.Habitacion.EstadoHabitacion;
import com.staysync.habitaciones.service.HabitacionService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Cola habitaciones-q. La habitación pasa a OCUPADA con el check-in y vuelve a DISPONIBLE
 * con el check-out. Reemplaza la llamada HTTP directa que hacía reservas-service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventosHabitacionListener {

    private final ProcesadorEventos procesador;
    private final HabitacionService habitacionService;

    @SqsListener("${staysync.eventos.cola}")
    public void recibir(String mensaje) {
        procesador.procesar(mensaje, manejadores());
    }

    Map<String, Consumer<EventoDominio>> manejadores() {
        return Map.of(
                TiposEvento.RESERVA_CHECKIN, e -> cambiarEstado(e, EstadoHabitacion.OCUPADA),
                TiposEvento.RESERVA_CHECKOUT, e -> cambiarEstado(e, EstadoHabitacion.DISPONIBLE));
    }

    private void cambiarEstado(EventoDominio evento, EstadoHabitacion estado) {
        Long habitacionId = evento.payloadLong("habitacionId");
        habitacionService.cambiarEstado(habitacionId, estado);
        log.info("Habitación {} -> {} por {}", habitacionId, estado, evento.tipo());
    }
}
