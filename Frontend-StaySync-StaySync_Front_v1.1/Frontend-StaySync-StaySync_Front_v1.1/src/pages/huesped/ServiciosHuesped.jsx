import { useState, useEffect, useCallback, useMemo } from 'react';
import { useAuth } from '../../context/AuthContext';
import { getServicios, getSolicitudes, solicitarServicio } from '../../services/serviciosService';
import { getMisReservas } from '../../services/reservasService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import AlertMessage   from '../../components/common/AlertMessage';

// Reservas desde las que tiene sentido pedir un servicio (estancia confirmada o en curso)
const ESTADOS_RESERVA_ELEGIBLES = ['CONFIRMADA', 'CHECKIN'];

// Enum values match el backend EstadoSolicitud exactamente (ver DashboardOperaciones)
const ESTADO_META = {
  PENDIENTE:  { label: 'Pendiente',  badge: 'bg-warning text-dark', icon: 'bi-clock' },
  EN_PROCESO: { label: 'En proceso', badge: 'bg-primary',           icon: 'bi-arrow-repeat' },
  COMPLETADO: { label: 'Completado', badge: 'bg-success',           icon: 'bi-check-circle' },
  CANCELADO:  { label: 'Cancelado',  badge: 'bg-secondary',         icon: 'bi-x-circle' },
};

function toDatetimeLocalValue(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function ModalSolicitarServicio({ servicio, reservas, onClose, onConfirm, loading, error }) {
  const defaultFecha = useMemo(() => toDatetimeLocalValue(new Date(Date.now() + 2 * 60 * 60 * 1000)), []);
  const minFecha      = useMemo(() => toDatetimeLocalValue(new Date(Date.now() + 5 * 60 * 1000)), []);

  const [reservaId, setReservaId] = useState(reservas[0]?.id ?? '');
  const [fecha,     setFecha]     = useState(defaultFecha);
  const [cantidad,  setCantidad]  = useState(1);
  const [notas,     setNotas]     = useState('');

  if (!servicio) return null;

  const total = (Number(servicio.precio) || 0) * cantidad;
  const puedeEnviar = !!reservaId && !!fecha;

  const handleConfirm = () => {
    if (!puedeEnviar) return;
    onConfirm({
      reservaId:  Number(reservaId),
      servicioId: servicio.id,
      cantidad,
      fechaServicio: fecha.length === 16 ? `${fecha}:00` : fecha,
      notas: notas.trim() || undefined,
    });
  };

  return (
    <div
      style={{ position: 'fixed', inset: 0, zIndex: 1055, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1rem', background: 'rgba(34,32,22,0.45)' }}
      onClick={(e) => { if (e.target === e.currentTarget && !loading) onClose(); }}
    >
      <div style={{ width: '100%', maxWidth: 480 }}>
        <div className="modal-content border-0" style={{ borderRadius: 14, overflow: 'hidden', boxShadow: '0 8px 40px rgba(34,32,22,0.35)' }}>

          <div className="modal-header" style={{ background: 'var(--ss-dark)', borderBottom: '3px solid var(--ss-gold)', padding: '1.1rem 1.5rem' }}>
            <div>
              <h5 className="modal-title mb-0 fw-bold" style={{ color: '#fff' }}>
                <i className="bi bi-bell-fill me-2" style={{ color: 'var(--ss-gold)' }} />
                Solicitar servicio
              </h5>
              <small style={{ color: 'rgba(255,255,255,0.65)' }}>{servicio.nombre}</small>
            </div>
            <button className="btn-close btn-close-white" onClick={onClose} disabled={loading} aria-label="Cerrar" />
          </div>

          <div className="modal-body px-4 py-4" style={{ background: '#fff' }}>
            {reservas.length === 0 ? (
              <div className="d-flex align-items-start gap-2 p-3 rounded" style={{ background: 'rgba(220,53,69,0.07)', border: '1px solid rgba(220,53,69,0.2)' }}>
                <i className="bi bi-exclamation-circle-fill mt-1" style={{ color: 'var(--ss-danger)' }} />
                <p className="mb-0 small" style={{ color: 'var(--ss-dark)' }}>
                  Necesitas una reserva <strong>confirmada</strong> o <strong>activa</strong> para solicitar servicios adicionales.
                </p>
              </div>
            ) : (
              <>
                <div className="mb-3">
                  <label className="form-label fw-semibold small mb-1" style={{ color: 'var(--ss-dark)' }}>Reserva</label>
                  <select className="form-select" value={reservaId} onChange={(e) => setReservaId(e.target.value)} disabled={loading}>
                    {reservas.map((r) => (
                      <option key={r.id} value={r.id}>
                        Hab. {r.habitacionNumero ?? r.habitacionId} · {r.codigo ?? `#${r.id}`}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="row g-3 mb-3">
                  <div className="col-7">
                    <label className="form-label fw-semibold small mb-1" style={{ color: 'var(--ss-dark)' }}>Fecha y hora</label>
                    <input
                      type="datetime-local"
                      className="form-control"
                      min={minFecha}
                      value={fecha}
                      onChange={(e) => setFecha(e.target.value)}
                      disabled={loading}
                    />
                  </div>
                  <div className="col-5">
                    <label className="form-label fw-semibold small mb-1" style={{ color: 'var(--ss-dark)' }}>Cantidad</label>
                    <input
                      type="number"
                      className="form-control"
                      min={1}
                      max={10}
                      value={cantidad}
                      onChange={(e) => setCantidad(Math.min(10, Math.max(1, Number(e.target.value) || 1)))}
                      disabled={loading}
                    />
                  </div>
                </div>

                <div className="mb-3">
                  <label className="form-label fw-semibold small mb-1" style={{ color: 'var(--ss-dark)' }}>
                    Notas <span className="text-muted fw-normal">(opcional)</span>
                  </label>
                  <textarea
                    className="form-control"
                    rows={2}
                    maxLength={500}
                    placeholder="Indicaciones adicionales para el servicio…"
                    value={notas}
                    onChange={(e) => setNotas(e.target.value)}
                    disabled={loading}
                  />
                </div>

                <div className="rounded px-3 py-2 d-flex justify-content-between align-items-center" style={{ background: 'var(--ss-cream)', border: '1px solid rgba(239,193,67,0.4)' }}>
                  <span className="small text-muted">Total estimado</span>
                  <span className="fw-bold" style={{ color: 'var(--ss-dark)' }}>${total.toLocaleString('es-CO')}</span>
                </div>
              </>
            )}

            {error && (
              <div className="alert alert-danger py-2 small mt-3 mb-0" style={{ borderRadius: 8 }}>
                <i className="bi bi-exclamation-circle-fill me-1" />{error}
              </div>
            )}
          </div>

          <div className="modal-footer px-4 py-3 gap-2" style={{ background: 'var(--ss-cream)', borderTop: '1px solid rgba(34,32,22,0.1)' }}>
            <button className="btn btn-outline-secondary" onClick={onClose} disabled={loading}>
              Cancelar
            </button>
            <button
              className="btn btn-ss-dark fw-semibold"
              disabled={!puedeEnviar || loading || reservas.length === 0}
              onClick={handleConfirm}
            >
              {loading
                ? <><span className="spinner-border spinner-border-sm me-2" />Enviando...</>
                : <><i className="bi bi-send-fill me-1" />Confirmar solicitud</>
              }
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function ServiciosHuesped() {
  const { user } = useAuth();

  const [tab,          setTab]          = useState('DISPONIBLES'); // DISPONIBLES | MIS_SOLICITUDES
  const [servicios,    setServicios]    = useState([]);
  const [reservas,     setReservas]     = useState([]);
  const [solicitudes,  setSolicitudes]  = useState([]);
  const [loading,      setLoading]      = useState(true);
  const [error,        setError]        = useState('');
  const [success,      setSuccess]      = useState('');

  const [servicioModal, setServicioModal] = useState(null);
  const [enviando,      setEnviando]      = useState(false);
  const [errorModal,    setErrorModal]    = useState('');

  const cargar = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [svcs, mis, sols] = await Promise.all([
        getServicios(),
        getMisReservas(user?.userId),
        getSolicitudes(user?.userId),
      ]);
      setServicios(Array.isArray(svcs) ? svcs : []);
      const todasReservas = Array.isArray(mis) ? mis : mis?.content ?? [];
      setReservas(todasReservas.filter((r) => ESTADOS_RESERVA_ELEGIBLES.includes(r.estado)));
      setSolicitudes(Array.isArray(sols) ? sols : []);
    } catch {
      setError('Error al cargar los servicios disponibles.');
    } finally {
      setLoading(false);
    }
  }, [user?.userId]);

  useEffect(() => { cargar(); }, [cargar]);

  const categorias = useMemo(() => {
    const grupos = new Map();
    for (const s of servicios) {
      const key = s.categoriaNombre ?? 'Otros servicios';
      if (!grupos.has(key)) grupos.set(key, []);
      grupos.get(key).push(s);
    }
    return Array.from(grupos.entries());
  }, [servicios]);

  const handleConfirmarSolicitud = async (body) => {
    setEnviando(true);
    setErrorModal('');
    try {
      const nueva = await solicitarServicio({ ...body, usuarioId: user?.userId });
      setSolicitudes((prev) => [nueva, ...prev]);
      setServicioModal(null);
      setSuccess(`Solicitud de "${servicioModal.nombre}" enviada correctamente.`);
      setTab('MIS_SOLICITUDES');
      setTimeout(() => setSuccess(''), 4000);
    } catch (err) {
      setErrorModal(err.response?.data?.message ?? 'No se pudo enviar la solicitud. Intenta nuevamente.');
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="fade-in-up p-3 p-md-4">

      <div className="mb-4">
        <h2 className="fw-bold mb-0" style={{ color: 'var(--ss-dark)' }}>
          <i className="bi bi-bell-fill me-2" style={{ color: 'var(--ss-gold)' }} />
          Servicios adicionales
        </h2>
        <p className="text-muted mb-0">Solicita servicios extra para tu estancia y revisa el estado de tus pedidos</p>
      </div>

      <AlertMessage message={error} onClose={() => setError('')} />
      <AlertMessage type="success" message={success} onClose={() => setSuccess('')} />

      {servicioModal && (
        <ModalSolicitarServicio
          servicio={servicioModal}
          reservas={reservas}
          loading={enviando}
          error={errorModal}
          onClose={() => { setServicioModal(null); setErrorModal(''); }}
          onConfirm={handleConfirmarSolicitud}
        />
      )}

      {/* Tabs */}
      <div className="d-flex gap-2 mb-4">
        {[
          { key: 'DISPONIBLES',     label: 'Disponibles',     icon: 'bi-grid' },
          { key: 'MIS_SOLICITUDES', label: 'Mis solicitudes', icon: 'bi-list-check', count: solicitudes.length },
        ].map((t) => {
          const active = tab === t.key;
          return (
            <button
              key={t.key}
              className="btn btn-sm d-flex align-items-center gap-2"
              style={{
                borderRadius: 8,
                fontSize: '0.85rem',
                background: active ? 'var(--ss-dark)' : '#fff',
                color: active ? '#fff' : 'var(--ss-dark)',
                border: active ? '1px solid var(--ss-dark)' : '1px solid rgba(34,32,22,0.2)',
                fontWeight: active ? 600 : 400,
                padding: '0.45rem 0.9rem',
              }}
              onClick={() => setTab(t.key)}
            >
              <i className={`bi ${t.icon}`} />
              {t.label}
              {t.count > 0 && (
                <span className="badge rounded-pill" style={{ background: active ? 'var(--ss-gold)' : 'var(--ss-dark)', color: active ? 'var(--ss-dark)' : '#fff', fontSize: '0.7rem' }}>
                  {t.count}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {loading ? (
        <LoadingSpinner message="Cargando servicios..." />
      ) : tab === 'DISPONIBLES' ? (
        categorias.length === 0 ? (
          <div className="text-center py-5 text-muted">
            <i className="bi bi-bell-slash fs-1 d-block mb-2" />
            No hay servicios adicionales disponibles por el momento.
          </div>
        ) : (
          categorias.map(([categoria, items]) => (
            <div key={categoria} className="mb-4">
              <h6 className="fw-bold mb-3 d-flex align-items-center gap-2" style={{ color: 'var(--ss-dark)' }}>
                <i className="bi bi-tag-fill" style={{ color: 'var(--ss-gold)' }} />
                {categoria}
              </h6>
              <div className="row g-3">
                {items.map((s) => (
                  <div key={s.id} className="col-sm-6 col-lg-4">
                    <div className="card border-0 h-100" style={{ borderRadius: 12, boxShadow: '0 2px 10px rgba(34,32,22,0.08)' }}>
                      <div className="card-body p-3 d-flex flex-column">
                        <div className="d-flex justify-content-between align-items-start mb-2 gap-2">
                          <span className="fw-semibold" style={{ color: 'var(--ss-dark)' }}>{s.nombre}</span>
                          {s.disponible === false && <span className="badge bg-secondary">No disponible</span>}
                        </div>
                        {s.descripcion && (
                          <p className="text-muted small mb-3 flex-grow-1">{s.descripcion}</p>
                        )}
                        {s.requiereReserva && (
                          <span className="badge bg-light text-dark border small mb-3 align-self-start">
                            <i className="bi bi-door-open me-1" style={{ color: 'var(--ss-gold)' }} />
                            Requiere reserva activa
                          </span>
                        )}
                        <div className="d-flex justify-content-between align-items-center mt-auto">
                          <span className="fw-bold" style={{ color: 'var(--ss-dark)' }}>
                            ${Number(s.precio ?? 0).toLocaleString('es-CO')}
                          </span>
                          <button
                            className="btn btn-ss-dark btn-sm"
                            disabled={s.disponible === false}
                            onClick={() => setServicioModal(s)}
                          >
                            <i className="bi bi-bell me-1" />Solicitar
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))
        )
      ) : (
        solicitudes.length === 0 ? (
          <div className="text-center py-5 text-muted">
            <i className="bi bi-clipboard-x fs-1 d-block mb-2" />
            Aún no has solicitado ningún servicio.
          </div>
        ) : (
          solicitudes.map((sol) => {
            const meta = ESTADO_META[sol.estado] ?? { label: sol.estado, badge: 'bg-secondary', icon: 'bi-question' };
            return (
              <div key={sol.id} className="card border-0 mb-3" style={{ borderRadius: 12, boxShadow: '0 2px 10px rgba(34,32,22,0.08)' }}>
                <div className="card-body p-3">
                  <div className="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-1">
                    <div>
                      <div className="fw-semibold" style={{ color: 'var(--ss-dark)' }}>{sol.servicioNombre ?? '—'}</div>
                      <small className="text-muted">
                        Reserva #{sol.reservaId}
                        {sol.cantidad > 1 && <> · x{sol.cantidad}</>}
                        {sol.fechaServicio && (
                          <> · {new Date(sol.fechaServicio).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}</>
                        )}
                      </small>
                    </div>
                    <span className={`badge ${meta.badge} d-flex align-items-center gap-1`}>
                      <i className={`bi ${meta.icon}`} />{meta.label}
                    </span>
                  </div>
                  {sol.notas && (
                    <p className="text-muted small mb-1 ps-1 mt-2" style={{ borderLeft: '2px solid rgba(239,193,67,0.4)' }}>
                      {sol.notas}
                    </p>
                  )}
                  {sol.precioTotal != null && (
                    <div className="small mt-2" style={{ color: 'var(--ss-dark)' }}>
                      <i className="bi bi-cash me-1" style={{ color: 'var(--ss-gold)' }} />
                      ${Number(sol.precioTotal).toLocaleString('es-CO')}
                    </div>
                  )}
                </div>
              </div>
            );
          })
        )
      )}
    </div>
  );
}
