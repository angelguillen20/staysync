import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { procesarPago, crearCheckoutStripe } from '../../services/pagosService';

const METODOS = [
  { value: 'TARJETA_CREDITO',  label: 'Tarjeta de crédito',   icon: 'bi-credit-card-2-front' },
  { value: 'TARJETA_DEBITO',   label: 'Tarjeta de débito',    icon: 'bi-credit-card'         },
  { value: 'TRANSFERENCIA',    label: 'Transferencia',         icon: 'bi-bank'                },
  { value: 'EFECTIVO',         label: 'Efectivo',              icon: 'bi-cash-coin'           },
  { value: 'STRIPE',           label: 'Pago con tarjeta',     icon: 'bi-credit-card-fill'    },
];

export default function PasarelaPago() {
  const { state }  = useLocation();
  const navigate   = useNavigate();
  const { user }   = useAuth();

  const [metodo,     setMetodo]     = useState('');
  const [procesando, setProcesando] = useState(false);
  const [error,      setError]      = useState('');
  const [exitoso,    setExitoso]    = useState(null);

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

  const noches = fechaEntrada && fechaSalida
    ? Math.max(1, Math.round((new Date(fechaSalida) - new Date(fechaEntrada)) / 86400000))
    : '—';

  const fmt = (val) => `$${Number(val).toLocaleString('es-CL')}`;

  const handlePagar = async () => {
    if (!metodo) { setError('Selecciona un método de pago.'); return; }

    setProcesando(true);
    setError('');

    /* ── Stripe: redirige a Stripe Checkout ─────────────── */
    if (metodo === 'STRIPE') {
      try {
        const data = await crearCheckoutStripe({
          monto:         precioTotal,
          tituloReserva: `Reserva ${codigo ?? `#${reservaId}`} — StaySync`,
          reservaId,
        });
        window.location.href = data.checkoutUrl;
      } catch (err) {
        setError(err.response?.data?.message ?? 'No se pudo conectar con el servicio de pago.');
        setProcesando(false);
      }
      return;
    }

    /* ── Otros métodos: flujo local ──────────────────────── */
    try {
      const pago = await procesarPago({
        reservaId,
        usuarioId:   user?.userId,
        monto:       precioTotal,
        moneda:      'CLP',
        metodoPago:  metodo,
        descripcion: `Pago reserva ${codigo ?? reservaId}`,
      });
      setExitoso(pago);
    } catch (err) {
      setError(err.response?.data?.message ?? 'No se pudo procesar el pago. Intente nuevamente.');
    } finally {
      setProcesando(false);
    }
  };

  /* ── Pantalla de éxito (métodos locales) ─────────────── */
  if (exitoso) {
    return (
      <div className="fade-in-up p-3 p-md-4 d-flex justify-content-center">
        <div style={{ maxWidth: 480, width: '100%' }}>
          <div className="card border-0 text-center p-4" style={{ borderRadius: 16, boxShadow: '0 8px 40px rgba(34,32,22,0.15)' }}>
            <div
              className="mx-auto mb-3 d-flex align-items-center justify-content-center rounded-circle"
              style={{ width: 72, height: 72, background: 'rgba(40,167,69,0.12)' }}
            >
              <i className="bi bi-check-circle-fill fs-1" style={{ color: 'var(--ss-success)' }} />
            </div>
            <h4 className="fw-bold mb-1" style={{ color: 'var(--ss-dark)' }}>¡Pago exitoso!</h4>
            <p className="text-muted small mb-3">Tu reserva ha sido confirmada y el pago procesado correctamente.</p>
            <div className="p-3 rounded mb-3" style={{ background: 'var(--ss-cream)', border: '1px solid rgba(239,193,67,0.4)' }}>
              <div className="d-flex justify-content-between small mb-1">
                <span className="text-muted">Referencia de pago</span>
                <span className="fw-semibold">{exitoso.referencia ?? exitoso.id}</span>
              </div>
              <div className="d-flex justify-content-between small mb-1">
                <span className="text-muted">Reserva</span>
                <span className="fw-semibold">{codigo ?? `#${reservaId}`}</span>
              </div>
              <div className="d-flex justify-content-between small">
                <span className="text-muted">Total pagado</span>
                <span className="fw-bold" style={{ color: 'var(--ss-dark)' }}>{fmt(precioTotal)}</span>
              </div>
            </div>
            <button className="btn btn-ss-dark w-100" onClick={() => navigate('/huesped/reservas')}>
              <i className="bi bi-calendar-check me-2" />
              Ver mis reservas
            </button>
          </div>
        </div>
      </div>
    );
  }

  /* ── Formulario de pago ───────────────────────────────── */
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
            <i className="bi bi-shield-lock me-2" style={{ color: 'var(--ss-gold)' }} />
            Pasarela de pago
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
            <div className="d-flex gap-3 small mb-3" style={{ color: 'rgba(255,255,255,0.65)' }}>
              <span><i className="bi bi-calendar-event me-1" />{fechaEntrada ? new Date(fechaEntrada).toLocaleDateString('es-CL') : '—'}</span>
              <span><i className="bi bi-arrow-right mx-1" />{fechaSalida ? new Date(fechaSalida).toLocaleDateString('es-CL') : '—'}</span>
              <span><i className="bi bi-moon me-1" />{noches} {noches === 1 ? 'noche' : 'noches'}</span>
            </div>
            <hr style={{ borderColor: 'rgba(255,255,255,0.12)', margin: '0 0 0.75rem' }} />
            <div className="d-flex justify-content-between align-items-center">
              <span className="small" style={{ color: 'rgba(255,255,255,0.65)' }}>Total a pagar</span>
              <span className="fw-bold fs-4" style={{ color: 'var(--ss-gold)' }}>{fmt(precioTotal)}</span>
            </div>
          </div>
        </div>

        {/* Métodos de pago */}
        <div className="card border-0 mb-3" style={{ borderRadius: 12, boxShadow: '0 2px 12px rgba(34,32,22,0.08)' }}>
          <div className="card-body p-4">
            <p className="fw-semibold small mb-3" style={{ color: 'var(--ss-dark)' }}>Selecciona un método de pago</p>
            <div className="d-flex flex-column gap-2">
              {METODOS.map(m => {
                const seleccionado = metodo === m.value;
                return (
                  <button
                    key={m.value}
                    className="d-flex align-items-center gap-3 p-3 rounded border text-start w-100"
                    style={{
                      background:  seleccionado ? 'rgba(239,193,67,0.1)' : '#fff',
                      borderColor: seleccionado ? 'var(--ss-gold)' : 'rgba(34,32,22,0.12)',
                      borderWidth: seleccionado ? 2 : 1,
                      cursor:      'pointer',
                      transition:  'all 0.15s',
                    }}
                    onClick={() => { setMetodo(m.value); setError(''); }}
                    disabled={procesando}
                  >
                    <i className={`bi ${m.icon} fs-5`} style={{ color: seleccionado ? 'var(--ss-gold)' : 'var(--ss-dark)', width: 24, flexShrink: 0 }} />
                    <span className="fw-medium small" style={{ color: 'var(--ss-dark)' }}>{m.label}</span>
                    {m.value === 'STRIPE' && (
                      <span className="ms-auto badge" style={{ background: 'rgba(99,91,255,0.12)', color: '#635bff', fontSize: '0.65rem' }}>
                        Stripe
                      </span>
                    )}
                    {seleccionado && m.value !== 'STRIPE' && (
                      <i className="bi bi-check-circle-fill ms-auto" style={{ color: 'var(--ss-gold)' }} />
                    )}
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        {/* Nota informativa Stripe */}
        {metodo === 'STRIPE' && (
          <div className="alert d-flex align-items-start gap-2 py-2 small mb-3"
            style={{ borderRadius: 8, background: 'rgba(99,91,255,0.08)', border: '1px solid rgba(99,91,255,0.25)', color: '#4f46a8' }}
          >
            <i className="bi bi-info-circle-fill mt-1 flex-shrink-0" />
            <span>
              Serás redirigido a <strong>Stripe Checkout</strong> para completar el pago de forma segura.
              En modo test usa la tarjeta <strong>4242 4242 4242 4242</strong>, fecha futura, CVC cualquiera.
            </span>
          </div>
        )}

        {/* Error */}
        {error && (
          <div className="alert alert-danger d-flex align-items-center gap-2 py-2 small mb-3" style={{ borderRadius: 8 }}>
            <i className="bi bi-exclamation-circle-fill" />
            {error}
          </div>
        )}

        <button
          className="btn w-100 fw-bold py-3"
          style={{
            background:   metodo && !procesando ? 'var(--ss-dark)' : '#b0a898',
            color:        '#fff',
            borderRadius: 10,
            fontSize:     '1rem',
            border:       'none',
            cursor:       metodo && !procesando ? 'pointer' : 'not-allowed',
            transition:   'background 0.2s',
            boxShadow:    metodo ? '0 4px 16px rgba(34,32,22,0.25)' : 'none',
          }}
          disabled={!metodo || procesando}
          onClick={handlePagar}
        >
          {procesando ? (
            <><span className="spinner-border spinner-border-sm me-2" />
              {metodo === 'STRIPE' ? 'Redirigiendo a Stripe...' : 'Procesando pago...'}</>
          ) : metodo === 'STRIPE' ? (
            <><i className="bi bi-credit-card-fill me-2" />Pagar con Stripe</>
          ) : (
            <><i className="bi bi-lock-fill me-2" />Pagar {fmt(precioTotal)}</>
          )}
        </button>

        <p className="text-center text-muted mt-3" style={{ fontSize: '0.75rem' }}>
          <i className="bi bi-shield-check me-1" style={{ color: 'var(--ss-gold)' }} />
          Pago seguro — modo de prueba activo
        </p>

      </div>
    </div>
  );
}
