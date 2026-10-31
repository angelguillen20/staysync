import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { crearCheckoutStripe } from '../../services/pagosService';
import { fmtFecha, nochesEntre } from '../../utils/fechas';

/*
 * El huésped paga en línea solo con tarjeta (Stripe). Efectivo, transferencia o POS se
 * pagan en recepción, que los registra en Pagos. El monto que se cobra lo fija el BFF con
 * el total de la reserva: el que se muestra aquí es solo informativo.
 */
export default function PasarelaPago() {
  const { state }  = useLocation();
  const navigate   = useNavigate();

  const [procesando, setProcesando] = useState(false);
  const [error,      setError]      = useState('');

  if (!state?.reservaId) {
    return (
      <div className="fade-in-up p-4 text-center text-muted">
        <i className="bi bi-exclamation-circle fs-1 d-block mb-3" style={{ color: 'var(--ss-gold)' }} />
        <p className="fw-semibold">No hay información de reserva disponible.</p>
        <button className="btn btn-ss-dark mt-2" onClick={() => navigate('/huesped/buscar')}>
          Volver a buscar habitaciones
        </button>
      </div>
    );
  }

  const { reservaId, codigo, habitacionNumero, fechaEntrada, fechaSalida, precioTotal } = state;

  const noches = fechaEntrada && fechaSalida ? nochesEntre(fechaEntrada, fechaSalida) : 0;

  const fmt = (val) => `$${Number(val).toLocaleString('es-CL')}`;

  /* Crea la sesión de Stripe Checkout y redirige. Solo vuelve si hubo error. */
  const handlePagar = async () => {
    setProcesando(true);
    setError('');
    try {
      const data = await crearCheckoutStripe({
        tituloReserva: `Reserva ${codigo ?? '#' + reservaId} — StaySync`,
        reservaId,
      });
      window.location.href = data.checkoutUrl;
    } catch (err) {
      setError(err.response?.data?.message ?? 'No se pudo conectar con el servicio de pago.');
      setProcesando(false);
    }
  };

  return (
    <div className="fade-in-up p-3 p-md-4 d-flex justify-content-center">
      <div style={{ maxWidth: 520, width: '100%' }}>

        <div className="mb-4">
          <button
            className="btn btn-link p-0 text-decoration-none mb-3 small"
            style={{ color: 'var(--ss-dark)' }}
            onClick={() => navigate('/huesped/buscar')}
          >
            <i className="bi bi-arrow-left me-1" /> Volver
          </button>
          <h2 className="fw-bold mb-0" style={{ color: 'var(--ss-dark)' }}>
            <i className="bi bi-shield-lock me-2" style={{ color: 'var(--ss-gold)' }} />Pasarela de pago
          </h2>
          <p className="text-muted small mt-1">Completa el pago para confirmar tu reserva</p>
        </div>

        {/* Resumen de reserva */}
        <div className="card border-0 mb-4" style={{ borderRadius: 12, background: 'var(--ss-dark)', boxShadow: '0 4px 16px rgba(34,32,22,0.2)' }}>
          <div className="card-body p-4">
            <p className="small mb-2 fw-semibold" style={{ color: 'var(--ss-gold)', letterSpacing: '0.05em', textTransform: 'uppercase' }}>
              Resumen de reserva
            </p>
            <div className="d-flex justify-content-between align-items-start mb-2">
              <span className="fw-bold fs-5 text-white">Habitación {habitacionNumero}</span>
              {codigo && <span className="badge" style={{ background: 'rgba(239,193,67,0.2)', color: 'var(--ss-gold)', fontSize: '0.75rem' }}>{codigo}</span>}
            </div>
            <div className="d-flex flex-wrap gap-3 small mb-3" style={{ color: 'rgba(255,255,255,0.65)' }}>
              <span><i className="bi bi-calendar-event me-1" />{fmtFecha(fechaEntrada)}</span>
              <span><i className="bi bi-arrow-right mx-1" />{fmtFecha(fechaSalida)}</span>
              <span><i className="bi bi-moon me-1" />{noches > 0 ? `${noches} ${noches === 1 ? 'noche' : 'noches'}` : '—'}</span>
            </div>
            <hr style={{ borderColor: 'rgba(255,255,255,0.12)', margin: '0 0 0.75rem' }} />
            <div className="d-flex justify-content-between align-items-center">
              <span className="small" style={{ color: 'rgba(255,255,255,0.65)' }}>Total a pagar</span>
              <span className="fw-bold fs-4" style={{ color: 'var(--ss-gold)' }}>{fmt(precioTotal)}</span>
            </div>
          </div>
        </div>

        {/* Método de pago */}
        <div className="card border-0 mb-3" style={{ borderRadius: 12, boxShadow: '0 2px 12px rgba(34,32,22,0.08)' }}>
          <div className="card-body p-4">
            <div className="d-flex align-items-center gap-3 mb-3">
              <i className="bi bi-credit-card-fill fs-4" style={{ color: 'var(--ss-gold)' }} />
              <div>
                <div className="fw-semibold" style={{ color: 'var(--ss-dark)' }}>Tarjeta de crédito o débito</div>
                <small className="text-muted">Serás redirigido a Stripe Checkout para pagar de forma segura.</small>
              </div>
              <span className="ms-auto badge" style={{ background: 'rgba(99,91,255,0.12)', color: '#635bff', fontSize: '0.65rem' }}>
                Stripe
              </span>
            </div>
            <div className="small rounded p-2" style={{ background: 'rgba(99,91,255,0.08)', color: '#4f46a8' }}>
              <i className="bi bi-info-circle-fill me-1" />
              Modo de prueba: tarjeta <strong>4242 4242 4242 4242</strong>, fecha futura y cualquier CVC.
            </div>
            <p className="small text-muted mb-0 mt-3">
              <i className="bi bi-shop me-1" />¿Prefieres efectivo o transferencia? Paga en recepción al llegar.
              La reserva sin pagar se libera a las 24 horas.
            </p>
          </div>
        </div>

        {/* Error */}
        {error && (
          <div className="alert alert-danger d-flex align-items-center gap-2 py-2 small mb-3" style={{ borderRadius: 8 }} role="alert">
            <i className="bi bi-exclamation-circle-fill" />
            {error}
          </div>
        )}

        <button
          className="btn w-100 fw-bold py-3"
          style={{
            background:   procesando ? '#b0a898' : 'var(--ss-dark)',
            color:        '#fff',
            borderRadius: 10,
            fontSize:     '1rem',
            border:       'none',
            transition:   'background 0.2s',
            boxShadow:    '0 4px 16px rgba(34,32,22,0.25)',
          }}
          disabled={procesando}
          onClick={handlePagar}
        >
          {procesando
            ? <><span className="spinner-border spinner-border-sm me-2" />Redirigiendo a Stripe...</>
            : <><i className="bi bi-lock-fill me-2" />Pagar {fmt(precioTotal)} con tarjeta</>}
        </button>

        <p className="text-center text-muted mt-3" style={{ fontSize: '0.75rem' }}>
          <i className="bi bi-shield-check me-1" style={{ color: 'var(--ss-gold)' }} />Pago seguro — modo de prueba activo
        </p>

      </div>
    </div>
  );
}
