import { useState, useEffect } from 'react';
import { useAuth }       from '../../context/AuthContext';
import { useNavigate }   from 'react-router-dom';
import { getMisReservas, cancelarReserva } from '../../services/reservasService';
import LoadingSpinner    from '../../components/common/LoadingSpinner';
import AlertMessage      from '../../components/common/AlertMessage';
import ModalConfirmarCancelacion from '../../components/common/ModalConfirmarCancelacion';

// ── Política de cancelación ────────────────────────────────────────────────
// El hotel Chile opera en America/Santiago, que usa DST:
//   Verano (oct–mar): UTC-3  |  Invierno (abr–sep): UTC-4
// Por eso NO se puede usar un offset fijo: se usa la API Intl del navegador.
// Check-in oficial: 15:00 hora Chile. Límite: 24h antes.
// Regla espejada del backend (ReservaService.validarVentanaCancelacion).

const ZONA_HOTEL            = 'America/Santiago';
const HORA_CHECKIN_H        = 15;
const HORAS_MIN_CANCELACION = 24;

/**
 * Calcula el timestamp UTC (ms) para las 15:00 hora Chile en la fecha dada.
 * Usa Intl.DateTimeFormat para leer el offset real (DST-aware) en lugar de
 * un valor fijo, garantizando exactitud tanto en verano (UTC-3) como en
 * invierno (UTC-4).
 *
 * @param {string} fechaEntrada  "YYYY-MM-DD"
 * @returns {number}             ms desde epoch
 */
function checkinUtcMs(fechaEntrada) {
  const [y, m, d] = fechaEntrada.split('-').map(Number);

  // Estimación inicial: 15:00 Chile verano ≈ 18:00 UTC (UTC-3)
  let utcMs = Date.UTC(y, m - 1, d, 18, 0, 0);

  // Leemos la hora local en Santiago para ese instante UTC
  const horaLocal = parseInt(
    new Intl.DateTimeFormat('es-CL', {
      timeZone: ZONA_HOTEL,
      hour:     '2-digit',
      hour12:   false,
    }).formatToParts(new Date(utcMs))
      .find(p => p.type === 'hour').value,
    10
  );

  // Ajustamos si el offset real difiere del estimado (ej. invierno UTC-4 → horaLocal=14)
  utcMs += (HORA_CHECKIN_H - horaLocal) * 3600 * 1000;
  return utcMs;
}

/** Devuelve true si todavía está dentro del plazo de cancelación. */
function puedeCancel(fechaEntrada) {
  if (!fechaEntrada) return false;
  const deadlineMs = checkinUtcMs(fechaEntrada) - HORAS_MIN_CANCELACION * 3600 * 1000;
  return Date.now() < deadlineMs;
}

/** Formatea la fecha límite de cancelación en hora Chile (es-CL). */
function formatDeadline(fechaEntrada) {
  if (!fechaEntrada) return '—';
  const deadlineMs = checkinUtcMs(fechaEntrada) - HORAS_MIN_CANCELACION * 3600 * 1000;
  const dl    = new Date(deadlineMs);
  const opts  = { timeZone: ZONA_HOTEL };
  const fecha = dl.toLocaleDateString('es-CL',  { ...opts, day: '2-digit', month: 'long', year: 'numeric' });
  const hora  = dl.toLocaleTimeString('es-CL',  { ...opts, hour: '2-digit', minute: '2-digit', hour12: false });
  return `${fecha} a las ${hora}`;
}
// ──────────────────────────────────────────────────────────────────────────

const ESTADO_BADGE = {
  PENDIENTE:  'bg-warning text-dark',
  CONFIRMADA: 'bg-success',
  CHECKIN:    'bg-primary',
  CHECKOUT:   'bg-dark',
  CANCELADA:  'bg-secondary',
  NO_SHOW:    'bg-danger',
};

const ESTADO_LABEL = {
  PENDIENTE:  'Pendiente',
  CONFIRMADA: 'Confirmada',
  CHECKIN:    'Activa',
  CHECKOUT:   'Finalizada',
  CANCELADA:  'Cancelada',
  NO_SHOW:    'No Show',
};

export default function ReservasHuesped() {
  const { user }   = useAuth();
  const navigate   = useNavigate();
  const [reservas,       setReservas]       = useState([]);
  const [loading,        setLoading]        = useState(true);
  const [error,          setError]          = useState('');
  const [canceling,      setCanceling]      = useState(null);
  const [modalReserva,   setModalReserva]   = useState(null); // reserva completa para el modal

  const handlePagar = (r) => {
    navigate('/huesped/pago', {
      state: {
        reservaId:        r.id,
        codigo:           r.codigo,
        habitacionNumero: r.habitacionNumero ?? r.habitacionId,
        fechaEntrada:     r.fechaEntrada,
        fechaSalida:      r.fechaSalida,
        precioTotal:      r.precioTotal,
      },
    });
  };

  useEffect(() => {
    getMisReservas(user?.userId)
      .then(data => {
        const all = Array.isArray(data) ? data : data.content ?? [];
        setReservas(all.filter(r => r.estado !== 'CANCELADA'));
      })
      .catch(() => setError('Error al cargar reservas.'))
      .finally(() => setLoading(false));
  }, [user?.userId]);

  const handleCancelar = async () => {
    if (!modalReserva) return;
    setCanceling(modalReserva.id);
    try {
      await cancelarReserva(modalReserva.id);
      setReservas(prev => prev.filter(r => r.id !== modalReserva.id));
      setModalReserva(null);
    } catch (err) {
      // Propagar el mensaje exacto del backend (incluye el plazo límite en el error 422)
      setError(err?.response?.data?.message ?? 'No se pudo cancelar la reserva.');
      setModalReserva(null);
    } finally {
      setCanceling(null);
    }
  };

  return (
    <div className="fade-in-up p-3 p-md-4">
      <h2 className="fw-bold mb-1" style={{ color: 'var(--ss-dark)' }}>
        <i className="bi bi-calendar-check me-2" style={{ color: 'var(--ss-gold)' }} />
        Mis reservas
      </h2>
      <p className="text-muted mb-4">Historial completo de tus reservas</p>

      <AlertMessage message={error} onClose={() => setError('')} />

      <ModalConfirmarCancelacion
        show={modalReserva !== null}
        onClose={() => setModalReserva(null)}
        onConfirm={handleCancelar}
        loading={canceling !== null}
        deadline={modalReserva ? formatDeadline(modalReserva.fechaEntrada) : null}
      />

      {loading
        ? <LoadingSpinner message="Cargando reservas..." />
        : reservas.length === 0
          ? (
            <div className="text-center py-5 text-muted">
              <i className="bi bi-calendar-x fs-1 d-block mb-2" />
              No tienes reservas registradas.
            </div>
          )
          : (
            <div className="table-responsive">
              <table className="table table-hover align-middle">
                <thead className="table-light">
                  <tr>
                    <th>#</th>
                    <th>Habitación</th>
                    <th>Entrada</th>
                    <th>Salida</th>
                    <th>Huéspedes</th>
                    <th>Total</th>
                    <th>Estado</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {reservas.map(r => (
                    <tr key={r.id}>
                      <td className="text-muted small">#{r.id}</td>
                      <td className="fw-medium">Hab. {r.habitacionNumero ?? r.habitacionId}</td>
                      <td>{r.fechaEntrada ? new Date(r.fechaEntrada).toLocaleDateString('es-CO') : '—'}</td>
                      <td>{r.fechaSalida  ? new Date(r.fechaSalida).toLocaleDateString('es-CO')  : '—'}</td>
                      <td>{r.numHuespedes ?? '—'}</td>
                      <td>{r.precioTotal ? `$${Number(r.precioTotal).toLocaleString()}` : '—'}</td>
                      <td>
                        <span className={`badge ${ESTADO_BADGE[r.estado] ?? 'bg-secondary'}`}>
                          {ESTADO_LABEL[r.estado] ?? r.estado}
                        </span>
                      </td>
                      <td>
                        <div className="d-flex gap-2">
                          {r.estado === 'PENDIENTE' && (
                            <button
                              className="btn btn-sm fw-semibold"
                              style={{ background: 'var(--ss-gold)', color: 'var(--ss-dark)', border: 'none', whiteSpace: 'nowrap' }}
                              onClick={() => handlePagar(r)}
                            >
                              <i className="bi bi-credit-card-fill me-1" />
                              Pagar
                            </button>
                          )}
                          {(r.estado === 'PENDIENTE' || r.estado === 'CONFIRMADA') && (
                            puedeCancel(r.fechaEntrada) ? (
                              /* Ventana de cancelación abierta */
                              <button
                                className="btn btn-outline-danger btn-sm"
                                disabled={canceling === r.id}
                                onClick={() => setModalReserva(r)}
                                title="Cancelar reserva"
                              >
                                {canceling === r.id
                                  ? <span className="spinner-border spinner-border-sm" />
                                  : <i className="bi bi-x-circle" />
                                }
                              </button>
                            ) : (
                              /* Ventana cerrada: botón bloqueado con tooltip explicativo */
                              <span
                                title={`Cancelación no disponible. El plazo venció el ${formatDeadline(r.fechaEntrada)}.`}
                                style={{ cursor: 'help', display: 'inline-flex', alignItems: 'center', gap: '0.3rem' }}
                              >
                                <button
                                  className="btn btn-outline-secondary btn-sm"
                                  disabled
                                  style={{ pointerEvents: 'none', opacity: 0.55 }}
                                  aria-label="Cancelación no disponible"
                                >
                                  <i className="bi bi-lock-fill" />
                                </button>
                              </span>
                            )
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
      }
    </div>
  );
}
