import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { getMisReservas } from '../../services/reservasService';
import LoadingSpinner from '../../components/common/LoadingSpinner';

// Enum values match el backend exactamente: PENDIENTE, CONFIRMADA, CHECKIN, CHECKOUT, CANCELADA, NO_SHOW
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

export default function DashboardHuesped() {
  const { user } = useAuth();
  const [reservas, setReservas] = useState([]);
  const [loading,  setLoading]  = useState(true);

  useEffect(() => {
    getMisReservas(user?.userId)
      .then(data => setReservas(Array.isArray(data) ? data : data.content ?? []))
      .catch(() => setReservas([]))
      .finally(() => setLoading(false));
  }, [user?.userId]);

  const activa = reservas.find(r => r.estado === 'CHECKIN');
  const proxima = reservas.find(r => r.estado === 'CONFIRMADA');

  return (
    <div className="fade-in-up p-3 p-md-4">
      {/* Welcome */}
      <div className="mb-4">
        <h2 className="fw-bold" style={{ color: 'var(--ss-dark)' }}>
          Bienvenido, {user?.nombre} 👋
        </h2>
        <p className="text-muted">Portal de huéspedes StaySync</p>
      </div>

      {/* Quick cards */}
      <div className="row g-3 mb-4">
        {/* Estancia activa */}
        <div className="col-md-6">
          <div className="card border-0 shadow-sm h-100" style={{ borderLeft: '4px solid var(--ss-gold) !important' }}>
            <div className="card-body">
              <div className="d-flex align-items-center gap-2 mb-3">
                <i className="bi bi-house-check-fill fs-4" style={{ color: 'var(--ss-gold)' }} />
                <span className="fw-semibold">Estancia actual</span>
              </div>
              {activa
                ? (
                  <>
                    <p className="mb-1">
                      <strong>Habitación {activa.habitacionNumero ?? activa.habitacionId}</strong>
                    </p>
                    <p className="text-muted small mb-0">
                      Check-out: {activa.fechaSalida ? new Date(activa.fechaSalida).toLocaleDateString('es-CO') : '—'}
                    </p>
                  </>
                )
                : <p className="text-muted mb-0">No tienes una estancia activa en este momento.</p>
              }
            </div>
          </div>
        </div>

        {/* Próxima reserva */}
        <div className="col-md-6">
          <div className="card border-0 shadow-sm h-100">
            <div className="card-body">
              <div className="d-flex align-items-center gap-2 mb-3">
                <i className="bi bi-calendar-event fs-4" style={{ color: 'var(--ss-gold)' }} />
                <span className="fw-semibold">Próxima reserva</span>
              </div>
              {proxima
                ? (
                  <>
                    <p className="mb-1">
                      Check-in: <strong>{new Date(proxima.fechaEntrada).toLocaleDateString('es-CO')}</strong>
                    </p>
                    <p className="text-muted small mb-0">
                      Habitación {proxima.habitacionNumero ?? proxima.habitacionId}
                    </p>
                  </>
                )
                : <p className="text-muted mb-0">No tienes reservas próximas.</p>
              }
            </div>
          </div>
        </div>
      </div>

      {/* Recent reservations */}
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h5 className="fw-bold mb-0">Mis reservas recientes</h5>
        <Link to="/huesped/reservas" className="btn btn-ss-dark btn-sm">
          <i className="bi bi-list-ul me-1" />Ver todas
        </Link>
      </div>

      {loading
        ? <LoadingSpinner message="Cargando tus reservas..." />
        : reservas.length === 0
          ? (
            <div className="text-center py-5 text-muted">
              <i className="bi bi-calendar-x fs-1 d-block mb-2" />
              <p>Aún no tienes reservas.</p>
              <p className="small">Contacta a recepción para hacer una reserva.</p>
            </div>
          )
          : (
            <div className="table-responsive">
              <table className="table table-hover align-middle">
                <thead className="table-light">
                  <tr>
                    <th>Habitación</th>
                    <th>Entrada</th>
                    <th>Salida</th>
                    <th>Estado</th>
                    <th>Total</th>
                  </tr>
                </thead>
                <tbody>
                  {reservas.slice(0, 5).map(r => (
                    <tr key={r.id}>
                      <td className="fw-medium">Hab. {r.habitacionNumero ?? r.habitacionId}</td>
                      <td>{r.fechaEntrada ? new Date(r.fechaEntrada).toLocaleDateString('es-CO') : '—'}</td>
                      <td>{r.fechaSalida  ? new Date(r.fechaSalida).toLocaleDateString('es-CO')  : '—'}</td>
                      <td>
                        <span className={`badge ${ESTADO_BADGE[r.estado] ?? 'bg-secondary'}`}>
                          {ESTADO_LABEL[r.estado] ?? r.estado}
                        </span>
                      </td>
                      <td>{r.precioTotal ? `$${Number(r.precioTotal).toLocaleString()}` : '—'}</td>
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
