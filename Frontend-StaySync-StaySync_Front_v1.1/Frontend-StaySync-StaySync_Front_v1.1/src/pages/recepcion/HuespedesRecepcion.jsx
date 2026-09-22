import { useState, useEffect, useCallback } from 'react';
import { getHuespedes } from '../../services/usuariosService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import AlertMessage   from '../../components/common/AlertMessage';

export default function HuespedesRecepcion() {
  const [huespedes, setHuespedes] = useState([]);
  const [loading,   setLoading]   = useState(true);
  const [error,     setError]     = useState('');
  const [busqueda,  setBusqueda]  = useState('');

  const cargar = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await getHuespedes();
      setHuespedes(Array.isArray(data) ? data : []);
    } catch {
      setError('Error al cargar la lista de huéspedes.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { cargar(); }, [cargar]);

  const filtrados = huespedes.filter(h => {
    if (!busqueda.trim()) return true;
    const q = busqueda.toLowerCase();
    return (
      h.nombre?.toLowerCase().includes(q)   ||
      h.apellido?.toLowerCase().includes(q) ||
      h.email?.toLowerCase().includes(q)    ||
      h.telefono?.includes(q)
    );
  });

  const activos   = huespedes.filter(h => h.activo !== false).length;
  const inactivos = huespedes.length - activos;

  return (
    <div className="fade-in-up p-3 p-md-4">

      {/* Header */}
      <div className="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-3">
        <div>
          <h2 className="fw-bold mb-0" style={{ color: 'var(--ss-dark)' }}>
            <i className="bi bi-people me-2" style={{ color: 'var(--ss-gold)' }} />
            Huéspedes
          </h2>
          <small className="text-muted">
            {huespedes.length} registrados · {activos} activos
          </small>
        </div>
        <button
          className="btn btn-outline-secondary btn-sm"
          onClick={cargar}
          disabled={loading}
        >
          <i className="bi bi-arrow-clockwise me-1" />Actualizar
        </button>
      </div>

      <AlertMessage message={error} onClose={() => setError('')} />

      {/* Stats */}
      {huespedes.length > 0 && (
        <div className="row g-3 mb-4">
          <div className="col-6 col-md-4">
            <div
              className="card border-0 shadow-sm h-100"
              style={{ borderRadius: 12, borderLeft: '4px solid #198754' }}
            >
              <div className="card-body d-flex align-items-center gap-3 py-3">
                <div
                  className="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0"
                  style={{ width: 44, height: 44, background: '#19875420' }}
                >
                  <i className="bi bi-person-check-fill" style={{ color: '#198754', fontSize: '1.2rem' }} />
                </div>
                <div>
                  <div className="fw-bold fs-4 lh-1">{activos}</div>
                  <small className="text-muted">Activos</small>
                </div>
              </div>
            </div>
          </div>
          <div className="col-6 col-md-4">
            <div
              className="card border-0 shadow-sm h-100"
              style={{ borderRadius: 12, borderLeft: '4px solid #6c757d' }}
            >
              <div className="card-body d-flex align-items-center gap-3 py-3">
                <div
                  className="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0"
                  style={{ width: 44, height: 44, background: '#6c757d20' }}
                >
                  <i className="bi bi-person-dash-fill" style={{ color: '#6c757d', fontSize: '1.2rem' }} />
                </div>
                <div>
                  <div className="fw-bold fs-4 lh-1">{inactivos}</div>
                  <small className="text-muted">Inactivos</small>
                </div>
              </div>
            </div>
          </div>
          <div className="col-12 col-md-4">
            <div
              className="card border-0 shadow-sm h-100"
              style={{ borderRadius: 12, borderLeft: '4px solid var(--ss-gold)' }}
            >
              <div className="card-body d-flex align-items-center gap-3 py-3">
                <div
                  className="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0"
                  style={{ width: 44, height: 44, background: 'rgba(239,193,67,0.15)' }}
                >
                  <i className="bi bi-people-fill" style={{ color: 'var(--ss-gold)', fontSize: '1.2rem' }} />
                </div>
                <div>
                  <div className="fw-bold fs-4 lh-1">{huespedes.length}</div>
                  <small className="text-muted">Total</small>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Búsqueda */}
      <div className="mb-3" style={{ maxWidth: 360 }}>
        <div className="input-group">
          <span className="input-group-text bg-white border-end-0">
            <i className="bi bi-search text-muted" style={{ fontSize: '0.85rem' }} />
          </span>
          <input
            className="form-control border-start-0"
            style={{ fontSize: '0.85rem' }}
            placeholder="Buscar por nombre, email o teléfono…"
            value={busqueda}
            onChange={e => setBusqueda(e.target.value)}
          />
          {busqueda && (
            <button
              className="btn btn-outline-secondary"
              onClick={() => setBusqueda('')}
            >
              <i className="bi bi-x" />
            </button>
          )}
        </div>
      </div>

      {/* Tabla */}
      {loading ? (
        <LoadingSpinner message="Cargando huéspedes..." />
      ) : filtrados.length === 0 ? (
        <div className="text-center py-5 text-muted">
          <i className="bi bi-person-x fs-1 d-block mb-2" />
          {busqueda
            ? `No hay huéspedes que coincidan con "${busqueda}".`
            : 'No se encontraron huéspedes registrados.'}
        </div>
      ) : (
        <div className="card border-0 shadow-sm" style={{ borderRadius: 12 }}>
          <div className="table-responsive">
            <table className="table table-hover align-middle mb-0">
              <thead style={{ background: 'var(--ss-cream)' }}>
                <tr>
                  <th className="ps-3 small text-muted fw-semibold">Huésped</th>
                  <th className="small text-muted fw-semibold">Email</th>
                  <th className="small text-muted fw-semibold">Teléfono</th>
                  <th className="small text-muted fw-semibold">Estado</th>
                </tr>
              </thead>
              <tbody>
                {filtrados.map(h => (
                  <tr key={h.id}>
                    <td className="ps-3">
                      <div className="d-flex align-items-center gap-2">
                        <div
                          className="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0 fw-bold"
                          style={{
                            width: 36, height: 36,
                            background: 'rgba(239,193,67,0.18)',
                            color: 'var(--ss-dark)',
                            fontSize: '0.85rem',
                          }}
                        >
                          {(h.nombre?.[0] ?? '?').toUpperCase()}
                        </div>
                        <div>
                          <div className="fw-semibold" style={{ fontSize: '0.9rem', color: 'var(--ss-dark)' }}>
                            {h.nombre} {h.apellido}
                          </div>
                          <div className="text-muted" style={{ fontSize: '0.75rem' }}>ID #{h.id}</div>
                        </div>
                      </div>
                    </td>
                    <td className="small text-muted">{h.email}</td>
                    <td className="small text-muted">{h.telefono ?? '—'}</td>
                    <td>
                      <span className={`badge ${h.activo !== false ? 'bg-success' : 'bg-secondary'}`}>
                        {h.activo !== false ? 'Activo' : 'Inactivo'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div
            className="px-3 py-2 border-top"
            style={{ background: 'var(--ss-cream)', borderRadius: '0 0 12px 12px' }}
          >
            <small className="text-muted">
              {filtrados.length} huésped{filtrados.length !== 1 ? 'es' : ''}
              {busqueda && ` · filtrado de ${huespedes.length}`}
            </small>
          </div>
        </div>
      )}
    </div>
  );
}
