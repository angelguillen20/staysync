import { useCallback, useEffect, useState } from 'react';
import { getAseo, completarAseo } from '../../services/serviciosService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import AlertMessage from '../../components/common/AlertMessage';

// Las tareas las crea el sistema con los eventos de reserva (confirmación, cancelación, check-out)
const ESTADO_META = {
  PROGRAMADA: { label: 'Programada',       badge: 'bg-secondary' },
  PENDIENTE:  { label: 'Lista para limpiar', badge: 'bg-warning text-dark' },
  COMPLETADA: { label: 'Completada',       badge: 'bg-success' },
  CANCELADA:  { label: 'Cancelada',        badge: 'bg-light text-muted border' },
};

export default function AseoRecepcion() {
  const [tareas, setTareas] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [completando, setCompletando] = useState(null);

  const cargar = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await getAseo();
      setTareas(Array.isArray(data) ? data : []);
    } catch {
      setError('No se pudieron cargar las tareas de aseo.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { cargar(); }, [cargar]);

  const completar = async (tarea) => {
    setCompletando(tarea.id);
    try {
      const actualizada = await completarAseo(tarea.id);
      setTareas((prev) => prev.map((t) => (t.id === tarea.id ? actualizada : t)));
    } catch {
      setError('No se pudo marcar la tarea como completada.');
    } finally {
      setCompletando(null);
    }
  };

  const pendientes = tareas.filter((t) => t.estado === 'PENDIENTE').length;

  return (
    <div className="fade-in-up p-3 p-md-4">
      <div className="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-3">
        <div>
          <h2 className="fw-bold mb-0" style={{ color: 'var(--ss-dark)' }}>
            <i className="bi bi-stars me-2" style={{ color: 'var(--ss-gold)' }} />Aseo de habitaciones
          </h2>
          <small className="text-muted">
            Se programan solas al confirmarse una reserva y quedan listas para limpiar con el check-out
          </small>
        </div>
        <span className="badge bg-warning text-dark fs-6">{pendientes} lista(s) para limpiar</span>
      </div>

      <AlertMessage message={error} onClose={() => setError('')} />

      {loading && <LoadingSpinner message="Cargando tareas de aseo..." />}
      {!loading && tareas.length === 0 && (
        <div className="text-center py-5 text-muted">
          <i className="bi bi-check2-all fs-1 d-block mb-2" />No hay tareas de aseo por ahora.
        </div>
      )}
      {!loading && tareas.length > 0 && (
        <div className="card border-0 shadow-sm" style={{ borderRadius: 12 }}>
          <div className="table-responsive">
            <table className="table table-hover align-middle mb-0">
              <thead style={{ background: 'var(--ss-cream)' }}>
                <tr>
                  <th className="ps-3 small text-muted fw-semibold">Habitación</th>
                  <th className="small text-muted fw-semibold">Fecha</th>
                  <th className="small text-muted fw-semibold">Reserva</th>
                  <th className="small text-muted fw-semibold">Estado</th>
                  <th className="small text-muted fw-semibold"></th>
                </tr>
              </thead>
              <tbody>
                {tareas.map((t) => {
                  const meta = ESTADO_META[t.estado] ?? { label: t.estado, badge: 'bg-secondary' };
                  return (
                    <tr key={t.id}>
                      <td className="ps-3 fw-semibold">#{t.habitacionId}</td>
                      <td className="small">{t.fechaProgramada}</td>
                      <td className="small text-muted">#{t.reservaId}</td>
                      <td><span className={`badge ${meta.badge}`}>{meta.label}</span></td>
                      <td>
                        {(t.estado === 'PENDIENTE' || t.estado === 'PROGRAMADA') && (
                          <button
                            className="btn btn-outline-success btn-sm"
                            disabled={completando === t.id}
                            onClick={() => completar(t)}
                          >
                            {completando === t.id
                              ? <span className="spinner-border spinner-border-sm" />
                              : <><i className="bi bi-check-lg me-1" />Completada</>}
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
