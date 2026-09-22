import { useState, useEffect, useCallback } from 'react';
import { getHabitaciones, cambiarEstado } from '../../services/habitacionesService';
import { getDashboard }                   from '../../services/dashboardService';
import LoadingSpinner                     from '../common/LoadingSpinner';
import AlertMessage                       from '../common/AlertMessage';
import ModalReserva                       from './ModalReserva';

const ESTADO_META = {
  DISPONIBLE:       { label: 'Disponible',        badge: 'bg-success',           icon: 'bi-check-circle-fill',  color: '#198754' },
  OCUPADA:          { label: 'Ocupada',           badge: 'bg-danger',            icon: 'bi-person-fill',        color: '#dc3545' },
  EN_LIMPIEZA:      { label: '🧹 En limpieza',   badge: 'bg-warning text-dark', icon: 'bi-brush',              color: '#e6a817' },
  MANTENIMIENTO:    { label: '🔧 Mantenimiento', badge: 'bg-secondary',         icon: 'bi-tools',              color: '#6c757d' },
  FUERA_DE_SERVICIO:{ label: 'Fuera de servicio', badge: 'bg-dark',              icon: 'bi-x-octagon-fill',     color: '#343a40' },
};

const TRANSICIONES = {
  DISPONIBLE:       ['EN_LIMPIEZA', 'MANTENIMIENTO'],
  OCUPADA:          ['DISPONIBLE',  'EN_LIMPIEZA'],
  EN_LIMPIEZA:      ['DISPONIBLE',  'MANTENIMIENTO'],
  MANTENIMIENTO:    ['DISPONIBLE',  'FUERA_DE_SERVICIO'],
  FUERA_DE_SERVICIO:['DISPONIBLE',  'MANTENIMIENTO'],
};

const ESTADO_RESERVA = {
  PENDIENTE:  { badge: 'bg-warning text-dark', label: 'Pendiente'  },
  CONFIRMADA: { badge: 'bg-success',           label: 'Confirmada' },
  CHECKIN:    { badge: 'bg-primary',           label: 'Check-in'   },
  CHECKOUT:   { badge: 'bg-dark',              label: 'Check-out'  },
  CANCELADA:  { badge: 'bg-secondary',         label: 'Cancelada'  },
  NO_SHOW:    { badge: 'bg-danger',            label: 'No show'    },
};

function StatCard({ icon, label, value, color, sublabel }) {
  return (
    <div className="col-6 col-xl-3">
      <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 12, borderLeft: `4px solid ${color}` }}>
        <div className="card-body d-flex align-items-center gap-3 py-3">
          <div
            className="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0"
            style={{ width: 48, height: 48, background: color + '20' }}
          >
            <i className={`bi ${icon}`} style={{ color, fontSize: '1.3rem' }} />
          </div>
          <div>
            <div className="fw-bold lh-1" style={{ fontSize: '1.6rem' }}>{value ?? '—'}</div>
            <small className="text-muted">{label}</small>
            {sublabel && <div style={{ fontSize: '0.7rem', color }}>{sublabel}</div>}
          </div>
        </div>
      </div>
    </div>
  );
}

function HabitacionCard({ hab, onCambiar, updating }) {
  const meta = ESTADO_META[hab.estado] ?? { label: hab.estado, badge: 'bg-secondary', icon: 'bi-question', color: '#6c757d' };
  const next  = TRANSICIONES[hab.estado] ?? [];
  const isUpdating = updating === hab.id;

  return (
    <div className="col-sm-6 col-lg-4 col-xl-3">
      <div
        className="card border-0 h-100"
        style={{
          borderRadius: 12,
          boxShadow: '0 2px 8px rgba(34,32,22,0.08)',
          borderTop: `3px solid ${meta.color}`,
          transition: 'box-shadow 0.2s',
        }}
      >
        <div className="card-body p-3">
          <div className="d-flex justify-content-between align-items-start mb-2">
            <div>
              <span className="fw-bold" style={{ fontSize: '1.1rem', color: 'var(--ss-dark)' }}>
                Hab. {hab.numero}
              </span>
              {hab.piso && <small className="text-muted ms-1">· Piso {hab.piso}</small>}
            </div>
            <span className={`badge ${meta.badge} d-flex align-items-center gap-1`} style={{ fontSize: '0.7rem' }}>
              <i className={`bi ${meta.icon}`} />{meta.label}
            </span>
          </div>

          <div className="small text-muted mb-1">
            <i className="bi bi-tag me-1" style={{ color: 'var(--ss-gold)' }} />
            {hab.tipoNombre ?? 'Estándar'}
          </div>
          <div className="small text-muted mb-1">
            <i className="bi bi-people me-1" style={{ color: 'var(--ss-gold)' }} />
            Cap. {hab.capacidad ?? '—'}
          </div>
          <div className="small fw-semibold mb-3" style={{ color: 'var(--ss-dark)' }}>
            <i className="bi bi-cash me-1" style={{ color: 'var(--ss-gold)' }} />
            ${hab.precioPorNoche?.toLocaleString('es-CO') ?? '—'}<span className="fw-normal text-muted">/noche</span>
          </div>

          {next.length > 0 && (
            <div className="d-flex flex-wrap gap-1 pt-2 border-top">
              {next.map(estado => (
                <button
                  key={estado}
                  className="btn btn-outline-secondary btn-sm py-0 px-2"
                  style={{ fontSize: '0.7rem', borderRadius: 6 }}
                  disabled={isUpdating}
                  onClick={() => onCambiar(hab.id, estado)}
                >
                  {isUpdating
                    ? <span className="spinner-border spinner-border-sm" style={{ width: '0.6rem', height: '0.6rem' }} />
                    : `→ ${ESTADO_META[estado]?.label ?? estado}`
                  }
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default function DashboardRecepcion() {
  const [habitaciones, setHabitaciones] = useState([]);
  const [stats,        setStats]        = useState(null);
  const [loading,      setLoading]      = useState(true);
  const [error,        setError]        = useState('');
  const [updating,     setUpdating]     = useState(null);
  const [modalOpen,    setModalOpen]    = useState(false);
  const [filtro,       setFiltro]       = useState('TODOS');

  const cargar = useCallback(async () => {
    setLoading(true);
    try {
      const [habs, dash] = await Promise.allSettled([
        getHabitaciones(),
        getDashboard(),
      ]);
      if (habs.status === 'fulfilled') setHabitaciones(habs.value);
      if (dash.status === 'fulfilled') setStats(dash.value);
    } catch {
      setError('Error al cargar datos.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { cargar(); }, [cargar]);

  const handleCambiarEstado = async (id, nuevoEstado) => {
    setUpdating(id);
    try {
      await cambiarEstado(id, { estado: nuevoEstado });
      setHabitaciones(prev => prev.map(h => h.id === id ? { ...h, estado: nuevoEstado } : h));
    } catch {
      setError('No se pudo actualizar la habitación.');
    } finally {
      setUpdating(null);
    }
  };

  const conteo = Object.fromEntries(
    Object.keys(ESTADO_META).map(e => [e, habitaciones.filter(h => h.estado === e).length])
  );
  const habsFiltradas = filtro === 'TODOS' ? habitaciones : habitaciones.filter(h => h.estado === filtro);

  // Stats del dashboard service — stats.reservas.* y stats.habitaciones.*
  const r = stats?.reservas ?? {};
  const recientes = stats?.reservasRecientes ?? [];

  return (
    <div className="fade-in-up p-3 p-md-4">

      {/* Header */}
      <div className="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-3">
        <div>
          <h2 className="fw-bold mb-0" style={{ color: 'var(--ss-dark)' }}>
            <i className="bi bi-grid-3x3-gap-fill me-2" style={{ color: 'var(--ss-gold)' }} />
            Dashboard Recepción
          </h2>
          <small className="text-muted">Vista general en tiempo real</small>
        </div>
        <div className="d-flex gap-2">
          <button className="btn btn-outline-secondary btn-sm" onClick={cargar} disabled={loading}>
            <i className="bi bi-arrow-clockwise me-1" />Actualizar
          </button>
          <button className="btn btn-ss-dark" onClick={() => setModalOpen(true)}>
            <i className="bi bi-calendar-plus me-2" />Nueva reserva
          </button>
        </div>
      </div>

      <AlertMessage message={error} onClose={() => setError('')} />

      {/* ── Stats habitaciones ── */}
      <p className="small fw-semibold text-muted text-uppercase mb-2" style={{ letterSpacing: '0.06em' }}>
        Estado de habitaciones
      </p>
      <div className="row g-3 mb-4">
        <StatCard icon="bi-check-circle-fill" label="Disponibles"    value={conteo.DISPONIBLE}    color="#198754" />
        <StatCard icon="bi-person-fill"       label="Ocupadas"       value={conteo.OCUPADA}        color="#dc3545" />
        <StatCard icon="bi-brush"             label="En limpieza"    value={conteo.EN_LIMPIEZA}    color="#e6a817" />
        <StatCard icon="bi-tools"             label="Mantenimiento"  value={conteo.MANTENIMIENTO}  color="#6c757d" />
      </div>

      {/* ── Stats reservas (del dashboard service) ── */}
      {stats && (
        <>
          <p className="small fw-semibold text-muted text-uppercase mb-2" style={{ letterSpacing: '0.06em' }}>
            Estado de reservas
          </p>
          <div className="row g-3 mb-4">
            <StatCard icon="bi-calendar2-check" label="Total reservas"  value={r.total}      color="var(--ss-dark)" />
            <StatCard icon="bi-hourglass-split" label="Pendientes"      value={r.pendientes} color="#e6a817" />
            <StatCard icon="bi-box-arrow-in-right" label="En check-in"  value={r.enCheckin}  color="#0d6efd" />
            <StatCard icon="bi-calendar-x"      label="Canceladas"      value={r.canceladas} color="#dc3545" />
          </div>
        </>
      )}

      {/* ── Reservas recientes ── */}
      {recientes.length > 0 && (
        <div className="card border-0 shadow-sm mb-4" style={{ borderRadius: 12 }}>
          <div className="card-header bg-white border-bottom d-flex justify-content-between align-items-center py-3" style={{ borderRadius: '12px 12px 0 0' }}>
            <span className="fw-semibold" style={{ color: 'var(--ss-dark)' }}>
              <i className="bi bi-clock-history me-2" style={{ color: 'var(--ss-gold)' }} />
              Reservas recientes
            </span>
            <small className="text-muted">Últimas {recientes.length}</small>
          </div>
          <div className="table-responsive">
            <table className="table table-hover align-middle mb-0 small">
              <thead style={{ background: 'var(--ss-cream)' }}>
                <tr>
                  <th className="ps-3">#</th>
                  <th>Habitación</th>
                  <th>Entrada</th>
                  <th>Salida</th>
                  <th>Total</th>
                  <th>Estado</th>
                </tr>
              </thead>
              <tbody>
                {recientes.map((res, i) => {
                  const em = ESTADO_RESERVA[res.estado] ?? { badge: 'bg-secondary', label: res.estado };
                  return (
                    <tr key={res.id ?? i}>
                      <td className="ps-3 text-muted">#{res.id}</td>
                      <td className="fw-semibold">Hab. {res.habitacionNumero ?? res.habitacionId ?? '—'}</td>
                      <td>{res.fechaEntrada ? new Date(res.fechaEntrada).toLocaleDateString('es-CO') : '—'}</td>
                      <td>{res.fechaSalida  ? new Date(res.fechaSalida).toLocaleDateString('es-CO')  : '—'}</td>
                      <td>{res.precioTotal ? `$${Number(res.precioTotal).toLocaleString('es-CO')}` : '—'}</td>
                      <td><span className={`badge ${em.badge}`}>{em.label}</span></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ── Filtros habitaciones ── */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
        <p className="small fw-semibold text-muted text-uppercase mb-0" style={{ letterSpacing: '0.06em' }}>
          Mapa de habitaciones
        </p>
        <div className="d-flex flex-wrap gap-2">
          {['TODOS', ...Object.keys(ESTADO_META)].map(e => {
            const meta = ESTADO_META[e];
            return (
              <button
                key={e}
                className={`btn btn-sm ${filtro === e ? 'btn-ss-dark' : 'btn-outline-secondary'}`}
                style={{ borderRadius: 8, fontSize: '0.8rem' }}
                onClick={() => setFiltro(e)}
              >
                {e === 'TODOS'
                  ? `Todas (${habitaciones.length})`
                  : `${meta.label} (${conteo[e] ?? 0})`
                }
              </button>
            );
          })}
        </div>
      </div>

      {/* ── Grid habitaciones ── */}
      {loading
        ? <LoadingSpinner message="Cargando habitaciones..." />
        : habsFiltradas.length === 0
          ? (
            <div className="text-center py-5 text-muted">
              <i className="bi bi-inbox fs-1 d-block mb-2" />
              No hay habitaciones en este estado.
            </div>
          )
          : (
            <div className="row g-3">
              {habsFiltradas.map(h => (
                <HabitacionCard
                  key={h.id}
                  hab={h}
                  onCambiar={handleCambiarEstado}
                  updating={updating}
                />
              ))}
            </div>
          )
      }

      <ModalReserva
        show={modalOpen}
        onClose={() => setModalOpen(false)}
        onCreated={cargar}
      />
    </div>
  );
}
