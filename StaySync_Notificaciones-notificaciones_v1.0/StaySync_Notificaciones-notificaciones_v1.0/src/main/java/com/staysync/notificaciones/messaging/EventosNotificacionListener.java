package com.staysync.notificaciones.messaging;

import com.staysync.eventos.EventoDominio;
import com.staysync.eventos.ProcesadorEventos;
import com.staysync.eventos.TiposEvento;
import com.staysync.notificaciones.model.ContactoReserva;
import com.staysync.notificaciones.repository.ContactoReservaRepository;
import com.staysync.notificaciones.service.NotificacionService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Cola notificaciones-q: un correo por cada evento de reserva o pago. Reemplaza las
 * llamadas HTTP que hacía el BFF, que además usaban el email de quien hacía el cambio
 * (p. ej. la recepcionista) en vez del del huésped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventosNotificacionListener {

    static final String PLANTILLA_RESERVA_CREADA = "RESERVA_CREADA";
    static final String PLANTILLA_RESERVA_ESTADO = "RESERVA_ESTADO";
    static final String PLANTILLA_PAGO = "PAGO_EFECTUADO";
    static final String PLANTILLA_REEMBOLSO = "REEMBOLSO_EFECTUADO";

    private final ProcesadorEventos procesador;
    private final NotificacionService notificacionService;
    private final ContactoReservaRepository contactos;

    @SqsListener("${staysync.eventos.cola}")
    public void recibir(String mensaje) {
        procesador.procesar(mensaje, manejadores());
    }

    Map<String, Consumer<EventoDominio>> manejadores() {
        return Map.of(
                TiposEvento.RESERVA_CREADA, e -> reserva(e, PLANTILLA_RESERVA_CREADA),
                TiposEvento.RESERVA_CONFIRMADA, e -> reserva(e, PLANTILLA_RESERVA_ESTADO),
                TiposEvento.RESERVA_CANCELADA, e -> reserva(e, PLANTILLA_RESERVA_ESTADO),
                TiposEvento.RESERVA_CHECKIN, e -> reserva(e, PLANTILLA_RESERVA_ESTADO),
                TiposEvento.RESERVA_CHECKOUT, e -> reserva(e, PLANTILLA_RESERVA_ESTADO),
                TiposEvento.PAGO_EFECTUADO, e -> pago(e, PLANTILLA_PAGO),
                TiposEvento.REEMBOLSO_EFECTUADO, e -> pago(e, PLANTILLA_REEMBOLSO));
    }

    private void reserva(EventoDominio e, String plantilla) {
        ContactoReserva contacto = guardarContacto(e);
        if (contacto.getEmail() == null) {
            log.warn("{} de la reserva {} sin email del huésped: no se envía correo", e.tipo(), contacto.getReservaId());
            return;
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("nombreUsuario", nombre(contacto));
        vars.put("codigo", valor(e.payloadString("codigo")));
        vars.put("habitacion", "Hab. " + valor(e.payloadString("habitacionId")));
        vars.put("fechaEntrada", valor(e.payloadString("fechaEntrada")));
        vars.put("fechaSalida", valor(e.payloadString("fechaSalida")));
        vars.put("precioTotal", valor(e.payloadString("precioTotal")));
        vars.put("estadoNuevo", traducirEstado(e.payloadString("estado")));
        notificacionService.procesarEvento(plantilla, contacto.getUsuarioId(), contacto.getEmail(), vars);
    }

    private void pago(EventoDominio e, String plantilla) {
        Long reservaId = e.payloadLong("reservaId");
        Optional<ContactoReserva> contacto = reservaId == null ? Optional.empty() : contactos.findByReservaId(reservaId);
        if (contacto.isEmpty() || contacto.get().getEmail() == null) {
            log.warn("{} de la reserva {} sin contacto conocido: no se envía correo", e.tipo(), reservaId);
            return;
        }
        Map<String, Object> vars = new HashMap<>();
        vars.put("nombreUsuario", nombre(contacto.get()));
        vars.put("codigo", valor(contacto.get().getCodigo()));
        vars.put("monto", valor(e.payloadString("monto")));
        vars.put("moneda", valor(e.payloadString("moneda")));
        vars.put("referencia", valor(e.payloadString("referencia")));
        notificacionService.procesarEvento(plantilla, contacto.get().getUsuarioId(), contacto.get().getEmail(), vars);
    }

    /** Crea o completa el contacto de la reserva con lo que trae el evento. */
    private ContactoReserva guardarContacto(EventoDominio e) {
        Long reservaId = e.payloadLong("reservaId");
        ContactoReserva c = contactos.findByReservaId(reservaId)
                .orElseGet(() -> ContactoReserva.builder().reservaId(reservaId).build());
        c.setUsuarioId(e.payloadLong("usuarioId"));
        c.setCodigo(e.payloadString("codigo"));
        if (e.payloadString("huespedEmail") != null) c.setEmail(e.payloadString("huespedEmail"));
        if (e.payloadString("huespedNombre") != null) c.setNombre(e.payloadString("huespedNombre"));
        return contactos.save(c);
    }

    private static String nombre(ContactoReserva c) {
        if (c.getNombre() != null && !c.getNombre().isBlank()) return c.getNombre();
        String email = c.getEmail();
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private static String valor(String v) {
        return v == null ? "N/A" : v;
    }

    private static String traducirEstado(String estado) {
        if (estado == null) return "Desconocido";
        return switch (estado) {
            case "PENDIENTE"  -> "Pendiente de pago";
            case "CONFIRMADA" -> "Confirmada";
            case "CHECKIN"    -> "Check-in realizado";
            case "CHECKOUT"   -> "Finalizada";
            case "CANCELADA"  -> "Cancelada";
            default           -> estado;
        };
    }
}
