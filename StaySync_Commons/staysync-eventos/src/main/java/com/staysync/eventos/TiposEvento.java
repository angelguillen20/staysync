package com.staysync.eventos;

/** Tipos de evento de StaySync. El valor viaja como atributo "tipo" y SNS filtra por él. */
public final class TiposEvento {

    /** Reserva creada en estado PENDIENTE (falta el pago). Publica: Reservas. */
    public static final String RESERVA_CREADA = "ReservaCreada";
    /** Reserva confirmada (normalmente tras el pago). Publica: Reservas. */
    public static final String RESERVA_CONFIRMADA = "ReservaConfirmada";
    /** Reserva cancelada. Publica: Reservas. */
    public static final String RESERVA_CANCELADA = "ReservaCancelada";
    /** El huésped hizo check-in. Publica: Reservas. */
    public static final String RESERVA_CHECKIN = "ReservaCheckin";
    /** El huésped hizo check-out. Publica: Reservas. */
    public static final String RESERVA_CHECKOUT = "ReservaCheckout";
    /** Pago cobrado. Publica: Pagos. */
    public static final String PAGO_EFECTUADO = "PagoEfectuado";
    /** Pago devuelto. Publica: Pagos. */
    public static final String REEMBOLSO_EFECTUADO = "ReembolsoEfectuado";

    /** Atributos de mensaje SNS/SQS. */
    public static final String ATRIBUTO_TIPO = "tipo";
    public static final String ATRIBUTO_TENANT = "tenantId";

    private TiposEvento() {
    }
}
