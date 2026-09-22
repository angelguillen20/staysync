import { useState, useEffect, useCallback } from 'react';
import { getTareas, actualizarSolicitud } from '../../services/serviciosService';
import LoadingSpinner from '../common/LoadingSpinner';
import AlertMessage   from '../common/AlertMessage';

// Enum values match the backend EstadoSolicitud exactly
const ESTADO_META = {
  PENDIENTE:  { label: 'Pendiente',    badge: 'bg-warning text-dark', icon: 'bi-clock',         color: '#e6a817', next: ['EN_PROCESO'] },
  EN_PROCESO: { label: 'En proceso',   badge: 'bg-primary',           icon: 'bi-arrow-repeat',  color: '#0d6efd', next: ['COMPLETADO', 'CANCELADO'] },
  COMPLETADO: { label: 'Completado',   badge: 'bg-success',           icon: 'bi-check-circle',  color: '#198754', next: [] },
  CANCELADO:  { label: 'Cancelado',    badge: 'bg-secondary',         icon: 'bi-x-circle',      color: '#6c757d', next: [] },
};

function TareaCard({ tarea, onActualizar, updating }) {
  const meta = ESTADO_META[tarea.estado] ?? {
    label: tarea.estado, badge: 'bg-secondary', icon: 'bi-question', color: '#6c757d', next: [],
  };
  const isUpdating = updating === tarea.id;

  return (
    <div
      className="card border-0 mb-3"
      style={{
        borderRadius: 12,
        boxShadow: '0 2px 10px rgba(34,32,22,0.08)',
        borderLeft: `4px solid ${meta.color}`,
      }}
    >
      <div className="card-body p-3">
        <div className="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-2">
          <div>
            <div className="fw-semibold" style={{ color: 'var(--ss-dark)', fontSize: '0.95rem' }}>
              {tarea.servicioNombre ?? '—'}
            </div>
            <small className="text-muted">
              Reserva #{tarea.reservaId}
              {tarea.cantidad > 1 && <> · x{tarea.cantidad}</>}
              {tarea.fechaServicio && (
                <> · {new Date(tarea.fechaServicio).toLocaleDateString('es-CO', {
                  day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit',
                })}</>
              )}
            </small>
          </div>
          <span className={`badge ${meta.badge} d-flex align-items-center gap-1`}>
            <i className={`bi ${meta.icon}`} />{meta.label}
          </span>
        </div>

        {tarea.notas && (
          <p className="text-muted small mb-2 ps-1" style={{ borderLeft: '2px solid rgba(239,193,67,0.4)' }}>
            {tarea.notas}
          </p>
        )}

        {tarea.precioTotal != null && (
          <div className="small mb-2" style={{ color: 'var(--ss-dark)' }}>
            <i className="bi bi-cash me-1" style={{ color: 'var(--ss-gold)' }} />
            ${Number(tarea.precioTotal).toLocaleString('es-CO')}
          </div>
        )}

        {meta.next.length > 0 && (
          <div className="d-flex gap-2 flex-wrap pt-2 border-top">
            {meta.next.map(estado => {
              const m = ESTADO_META[estado];
              return (
                <button
                  key={estado}
                  className={`btn btn-sm ${estado === 'COMPLETADO' ? 'btn-ss-dark' : 'btn-outline-secondary'}`}
                  style={{ fontSize: '0.78rem', borderRadius: 8 }}
                  disabled={isUpdating}
                  onClick={() => onActualizar(tarea.id, estado)}
                >
                  {isUpdating
                    ? <span className="spinner-border spinner-border-sm" style={{ width: '0.65rem', height: '0.65rem' }} />
                    : <><i className={`bi ${m.icon} me-1`} />{m.label}</>
                  }
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

export default function DashboardOperaciones() {
  const [tareas,   setTareas]   = useState([]);
  const [loading,  setLoading]  = useState(true);
  const [error,    setError]    = useState('');
  const [success,  setSuccess]  = useState('');
  const [updating, setUpdating] = useState(null);
  const [filtro,   setFiltro]   = useState('TODOS');

  const cargar = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await getTareas();
      setTareas(Array.isArray(data) ? data : []);
    } catch {
      setError('Error al cargar las tareas de operaciones.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { cargar(); }, [cargar]);

  const handleActualizar = async (id, nuevoEstado) => {
    setUpdating(id);
    setError(''); setSuccess('');
    try {
      await actualizarSolicitud(id, { estado: nuevoEstado });
      setTareas(prev => prev.map(t => t.id === id ? { ...t, estado: nuevoEstado } : t));
      setSuccess(`Tarea #${id} actualizada a "${ESTADO_META[nuevoEstado]?.label ?? nuevoEstado}".`);
      setTimeout(() => setSuccess(''), 3000);
    } catch {
      setError('No se pudo actualizar la tarea.');
    } finally {
      setUpdating(null);
    }
  };

  const conteo = Object.fromEntries(
    Object.keys(ESTADO_META).map(e => [e, tareas.filter(t => t.estado === e).length])
  );

  const tareasFiltradas = filtro === 'TODOS'
    ? tareas
    : tareas.filter(t => t.estado === filtro);

  return (
    <div className="fade-in-up p-3 p-md-4">

      {/* Header */}
      <div className="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-3">
        <div>
          <h2 className="fw-bold mb-0" style={{ color: 'var(--ss-dark)' }}>
            <i className="bi bi-clipboard-check me-2" style={{ color: 'var(--ss-gold)' }} />
            Operaciones
          </h2>
          <small className="text-muted">
            Tareas de servicios adicionales · {tareas.length} en total
          </small>
        </div>
        <button className="btn btn-outline-secondary btn-sm" onClick={cargar} disabled={loading}>
          <i className="bi bi-arrow-clockwise me-1" />Actualizar
        </button>
      </div>

      <AlertMessage message={error}   onClose={() => setError('')} />
      <AlertMessage type="success" message={success} onClose={() => setSuccess('')} />

      {/* Stat cards */}
      <div className="row g-3 mb-4">
        {[
          { estado: 'PENDIENTE',  icon: 'bi-clock-fill' },
          { estado: 'EN_PROCESO', icon: 'bi-arrow-repeat' },
          { estado: 'COMPLETADO', icon: 'bi-check-circle-fill' },
        ].map(({ estado, icon }) => {
          const m = ESTADO_META[estado];
          return (
            <div key={estado} className="col-sm-4">
              <div
                className="card border-0 shadow-sm h-100"
                style={{ borderRadius: 12, borderLeft: `4px solid ${m.color}` }}
              >
                <div className="card-body d-flex align-items-center gap-3 py-3">
                  <div
                    className="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0"
                    style={{ width: 44, height: 44, background: m.color + '20' }}
                  >
                    <i className={`bi ${icon}`} style={{ color: m.color, fontSize: '1.2rem' }} />
                  </div>
                  <div>
                    <div className="fw-bold fs-4 lh-1">{conteo[estado] ?? 0}</div>
                    <small className="text-muted">{m.label}</small>
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Filter tabs */}
      <div className="d-flex flex-wrap gap-2 mb-4">
        {['TODOS', ...Object.keys(ESTADO_META)].map(e => {
          const meta = ESTADO_META[e];
          const active = filtro === e;
          return (
            <button
              key={e}
              className="btn btn-sm"
              style={{
                borderRadius: 8,
                fontSize: '0.8rem',
                background: active ? 'var(--ss-dark)' : '#fff',
                color: active ? '#fff' : 'var(--ss-dark)',
                border: active ? '1px solid var(--ss-dark)' : '1px solid rgba(34,32,22,0.2)',
                fontWeight: active ? 600 : 400,
              }}
              onClick={() => setFiltro(e)}
            >
              {e === 'TODOS'
                ? `Todas (${tareas.length})`
                : `${meta.label} (${conteo[e] ?? 0})`
              }
            </button>
          );
        })}
      </div>

      {/* List */}
      {loading
        ? <LoadingSpinner message="Cargando tareas..." />
        : tareasFiltradas.length === 0
          ? (
            <div className="text-center py-5 text-muted">
              <i className="bi bi-clipboard-x fs-1 d-block mb-2" />
              {filtro === 'TODOS'
                ? 'No hay tareas de operaciones registradas.'
                : `No hay tareas en estado "${ESTADO_META[filtro]?.label ?? filtro}".`
              }
            </div>
          )
          : tareasFiltradas.map(t => (
              <TareaCard
                key={t.id}
                tarea={t}
                onActualizar={handleActualizar}
                updating={updating}
              />
            ))
      }
    </div>
  );
}
