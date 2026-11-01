import { useState, useEffect, useCallback } from 'react';
import {
  getHabitaciones, cambiarEstado, crearHabitacion,
  getTiposHabitacion, getAmenidades, desactivarHabitacion,
} from '../../services/habitacionesService';
import { useAuth } from '../../context/AuthContext';
import { useSearchParams } from 'react-router-dom';
import EtiquetaHotel, { useHotelesPlataforma } from '../../components/common/EtiquetaHotel';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import AlertMessage   from '../../components/common/AlertMessage';
import ModalBackdrop  from '../../components/common/ModalBackdrop';
import Foto           from '../../components/common/Foto';
import ModalFotosHabitacion from '../../components/recepcion/ModalFotosHabitacion';

const ESTADO_META = {
  DISPONIBLE:      { label: 'Disponible',        badge: 'bg-success',           icon: 'bi-check-circle-fill',  color: '#198754', bg: '#f0fdf4' },
  OCUPADA:         { label: 'Ocupada',           badge: 'bg-danger',            icon: 'bi-person-fill',        color: '#dc3545', bg: '#fff5f5' },
  EN_LIMPIEZA:     { label: 'En limpieza',    badge: 'bg-warning text-dark', icon: 'bi-brush',              color: '#e6a817', bg: '#fffbeb' },
  MANTENIMIENTO:   { label: 'Mantenimiento',  badge: 'bg-secondary',         icon: 'bi-tools',              color: '#6c757d', bg: '#f8f9fa' },
  FUERA_DE_SERVICIO:{ label: 'Fuera de servicio',badge: 'bg-dark',              icon: 'bi-x-octagon-fill',     color: '#343a40', bg: '#f8f9fa' },
};

const TRANSICIONES = {
  DISPONIBLE:       ['EN_LIMPIEZA', 'MANTENIMIENTO'],
  OCUPADA:          ['DISPONIBLE',  'EN_LIMPIEZA'],
  EN_LIMPIEZA:      ['DISPONIBLE',  'MANTENIMIENTO'],
  MANTENIMIENTO:    ['DISPONIBLE',  'FUERA_DE_SERVICIO'],
  FUERA_DE_SERVICIO:['DISPONIBLE',  'MANTENIMIENTO'],
};

function OcupacionBar({ conteo, total }) {
  if (!total) return null;
  const pct = (n) => ((n / total) * 100).toFixed(1);
  const segments = [
    { key: 'OCUPADA',          color: '#dc3545' },
    { key: 'EN_LIMPIEZA',      color: '#e6a817' },
    { key: 'MANTENIMIENTO',    color: '#6c757d' },
    { key: 'FUERA_DE_SERVICIO',color: '#343a40' },
    { key: 'DISPONIBLE',       color: '#198754' },
  ];
  return (
    <div className="mb-4">
      <div className="d-flex rounded overflow-hidden" style={{ height: 10, background: '#e9ecef' }}>
        {segments.map(s => conteo[s.key] > 0 && (
          <div
            key={s.key}
            title={`${ESTADO_META[s.key].label}: ${conteo[s.key]}`}
            style={{ width: `${pct(conteo[s.key])}%`, background: s.color, transition: 'width 0.5s' }}
          />
        ))}
      </div>
      <div className="d-flex flex-wrap gap-3 mt-2">
        {segments.map(s => conteo[s.key] > 0 && (
          <span key={s.key} className="small d-flex align-items-center gap-1">
            <span style={{ width: 10, height: 10, borderRadius: 2, background: s.color, display: 'inline-block' }} />
            <span className="text-muted">{ESTADO_META[s.key].label}</span>
            <strong>{conteo[s.key]}</strong>
            <span className="text-muted">({pct(conteo[s.key])}%)</span>
          </span>
        ))}
      </div>
    </div>
  );
}

/* soloLectura: habitación de otro hotel (vista de plataforma); sus cambios se hacen desde ese hotel */
function HabitacionCard({ hab, onCambiar, updating, success, isAdmin, onDesactivar, deactivating, onFotos, soloLectura }) {
  const meta      = ESTADO_META[hab.estado] ?? { label: hab.estado, badge: 'bg-secondary', icon: 'bi-question', color: '#6c757d', bg: '#f8f9fa' };
  const next      = soloLectura ? [] : TRANSICIONES[hab.estado] ?? [];
  const isUpdating = updating === hab.id;
  const flashOk   = success === hab.id;
  const isDeactivating = deactivating === hab.id;

  return (
    <div className="col-sm-6 col-lg-4 col-xl-3">
      <div
        className="card border-0 h-100"
        style={{
          borderRadius: 14,
          boxShadow: flashOk
            ? '0 0 0 2px #198754, 0 4px 16px rgba(25,135,84,0.2)'
            : `0 2px 10px rgba(34,32,22,0.08)`,
          transition: 'box-shadow 0.3s',
          overflow: 'hidden',
        }}
      >
        <Foto src={hab.imagenPrincipal} alt={`Habitación ${hab.numero}`} numero={hab.numero} />
        {/* El estado de hoy, en la franja de color */}
        <div
          className="d-flex align-items-center justify-content-between px-3 py-2"
          style={{ background: meta.bg, borderBottom: `3px solid ${meta.color}` }}
        >
          {hab.hotelId
            ? <EtiquetaHotel tenantId={hab.hotelId} nombre={hab.hotelNombre} />
            : <span className="small text-muted">{(hab.imagenes?.length ?? 0) === 1 ? '1 foto' : `${hab.imagenes?.length ?? 0} fotos`}</span>}
          <span className={`badge ${meta.badge} d-flex align-items-center gap-1`} style={{ fontSize: '0.7rem' }}>
            <i className={`bi ${meta.icon}`} />{meta.label}
          </span>
        </div>

        <div className="card-body p-3">
          <div className="d-flex flex-wrap gap-2 mb-3">
            <span className="badge bg-light text-dark border small">
              <i className="bi bi-tag me-1" style={{ color: 'var(--ss-gold)' }} />
              {hab.tipoNombre ?? 'Estándar'}
            </span>
            <span className="badge bg-light text-dark border small">
              <i className="bi bi-people me-1" style={{ color: 'var(--ss-gold)' }} />Cap. {hab.capacidad ?? '—'}
            </span>
            {hab.piso && (
              <span className="badge bg-light text-dark border small">
                <i className="bi bi-building me-1" style={{ color: 'var(--ss-gold)' }} />Piso {hab.piso}
              </span>
            )}
          </div>

          <div
            className="rounded px-3 py-2 mb-3 d-flex justify-content-between align-items-center"
            style={{ background: 'var(--ss-cream)', border: '1px solid rgba(239,193,67,0.3)' }}
          >
            <span className="small text-muted">Precio/noche</span>
            <span className="fw-bold" style={{ color: 'var(--ss-dark)' }}>
              ${hab.precioPorNoche?.toLocaleString('es-CL') ?? '—'}
            </span>
          </div>

          {soloLectura ? null : next.length > 0 ? (
            <div className="d-flex flex-column gap-1">
              <small className="text-muted mb-1">Cambiar a:</small>
              {next.map(estado => {
                const m = ESTADO_META[estado];
                return (
                  <button
                    key={estado}
                    className="btn btn-sm text-start d-flex align-items-center gap-2"
                    style={{
                      background: m.bg,
                      border: `1px solid ${m.color}40`,
                      borderRadius: 8,
                      fontSize: '0.78rem',
                      color: 'var(--ss-dark)',
                    }}
                    disabled={isUpdating}
                    onClick={() => onCambiar(hab.id, estado)}
                  >
                    {isUpdating
                      ? <span className="spinner-border spinner-border-sm" style={{ width: '0.65rem', height: '0.65rem' }} />
                      : <i className={`bi ${m.icon}`} style={{ color: m.color }} />
                    }
                    {m.label}
                  </button>
                );
              })}
            </div>
          ) : (
            <div className="text-center text-muted small py-2">
              <i className="bi bi-lock me-1" />Sin transiciones disponibles
            </div>
          )}

          {isAdmin && !soloLectura && (
            <div className="d-flex justify-content-between align-items-center mt-2">
              <button className="btn btn-sm btn-outline-secondary" style={{ fontSize: '0.75rem' }} onClick={() => onFotos(hab)}>
                <i className="bi bi-images me-1" aria-hidden="true" />Fotos
              </button>
              <button
                className="btn btn-sm btn-link text-danger p-0 d-flex align-items-center gap-1"
                style={{ fontSize: '0.75rem', textDecoration: 'none' }}
                disabled={isDeactivating}
                onClick={() => onDesactivar(hab)}
              >
                {isDeactivating
                  ? <span className="spinner-border spinner-border-sm" style={{ width: '0.65rem', height: '0.65rem' }} />
                  : <i className="bi bi-trash3" />
                }
                Desactivar
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function ModalNuevaHabitacion({ tipos, amenidades, onClose, onCreate, saving, error }) {
  const [form, setForm] = useState({
    numero: '', piso: '', tipoId: tipos[0]?.id ?? '', descripcion: '', precioPorNoche: '',
  });
  const [amenidadIds, setAmenidadIds] = useState([]);

  // Los tipos se cargan después de abrir el modal: si llegan cuando tipoId sigue
  // vacío, el <select> muestra el primero pero el estado no, y el botón queda deshabilitado.
  useEffect(() => {
    if (tipos.length && !form.tipoId) {
      setForm((p) => ({ ...p, tipoId: tipos[0].id }));
    }
  }, [tipos, form.tipoId]);

  const toggleAmenidad = (id) => {
    setAmenidadIds((prev) => prev.includes(id) ? prev.filter((a) => a !== id) : [...prev, id]);
  };

  const puedeEnviar = form.numero.trim() && form.piso && form.tipoId && form.precioPorNoche;

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!puedeEnviar) return;
    onCreate({
      numero: form.numero.trim(),
      piso: Number(form.piso),
      tipoId: Number(form.tipoId),
      descripcion: form.descripcion.trim() || undefined,
      precioPorNoche: Number(form.precioPorNoche),
      amenidadIds: amenidadIds.length ? amenidadIds : undefined,
    });
  };

  return (
    <div
      style={{ position: 'fixed', inset: 0, zIndex: 1055, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1rem', background: 'rgba(34,32,22,0.45)' }}
    >
      <ModalBackdrop onClose={onClose} disabled={saving} />
      <div style={{ position: 'relative', width: '100%', maxWidth: 520 }}>
        <div className="modal-content border-0" style={{ borderRadius: 14, overflow: 'hidden', boxShadow: '0 8px 40px rgba(34,32,22,0.35)' }}>

          <div className="modal-header" style={{ background: 'var(--ss-dark)', borderBottom: '3px solid var(--ss-gold)', padding: '1.1rem 1.5rem' }}>
            <h5 className="modal-title mb-0 fw-bold" style={{ color: '#fff' }}>
              <i className="bi bi-building-add me-2" style={{ color: 'var(--ss-gold)' }} />Nueva habitación
            </h5>
            <button className="btn-close btn-close-white" onClick={onClose} disabled={saving} aria-label="Cerrar" />
          </div>

          <form onSubmit={handleSubmit}>
            <div className="modal-body px-4 py-4" style={{ background: '#fff', maxHeight: '70vh', overflowY: 'auto' }}>
              {tipos.length === 0 ? (
                <div className="d-flex align-items-start gap-2 p-3 rounded" style={{ background: 'rgba(220,53,69,0.07)', border: '1px solid rgba(220,53,69,0.2)' }}>
                  <i className="bi bi-exclamation-circle-fill mt-1" style={{ color: 'var(--ss-danger)' }} />
                  <p className="mb-0 small" style={{ color: 'var(--ss-dark)' }}>
                    No hay tipos de habitación configurados. Configura al menos un tipo antes de crear habitaciones.
                  </p>
                </div>
              ) : (
                <>
                  <div className="row g-3 mb-3">
                    <div className="col-sm-6">
                      <label htmlFor="habitacion-numero" className="form-label fw-medium small">N.° de habitación</label>
                      <input
                        id="habitacion-numero"
                        className="form-control"
                        placeholder="Ej: 305"
                        value={form.numero}
                        onChange={(e) => setForm((p) => ({ ...p, numero: e.target.value }))}
                        disabled={saving}
                        autoFocus
                      />
                    </div>
                    <div className="col-sm-6">
                      <label htmlFor="habitacion-piso" className="form-label fw-medium small">Piso</label>
                      <input
                        id="habitacion-piso"
                        type="number"
                        className="form-control"
                        min={1}
                        value={form.piso}
                        onChange={(e) => setForm((p) => ({ ...p, piso: e.target.value }))}
                        disabled={saving}
                      />
                    </div>
                    <div className="col-sm-6">
                      <label htmlFor="habitacion-tipo" className="form-label fw-medium small">Tipo</label>
                      <select
                        id="habitacion-tipo"
                        className="form-select"
                        value={form.tipoId}
                        onChange={(e) => setForm((p) => ({ ...p, tipoId: e.target.value }))}
                        disabled={saving}
                      >
                        {tipos.map((t) => (
                          <option key={t.id} value={t.id}>{t.nombre} (cap. {t.capacidad})</option>
                        ))}
                      </select>
                    </div>
                    <div className="col-sm-6">
                      <label htmlFor="habitacion-precio" className="form-label fw-medium small">Precio por noche ($)</label>
                      <input
                        id="habitacion-precio"
                        type="number"
                        className="form-control"
                        min="0.01"
                        step="0.01"
                        value={form.precioPorNoche}
                        onChange={(e) => setForm((p) => ({ ...p, precioPorNoche: e.target.value }))}
                        disabled={saving}
                      />
                    </div>
                    <div className="col-12">
                      <label htmlFor="habitacion-descripcion" className="form-label fw-medium small">Descripción <span className="text-muted fw-normal">(opcional)</span></label>
                      <textarea
                        id="habitacion-descripcion"
                        className="form-control"
                        rows={2}
                        value={form.descripcion}
                        onChange={(e) => setForm((p) => ({ ...p, descripcion: e.target.value }))}
                        disabled={saving}
                      />
                    </div>
                  </div>

                  {amenidades.length > 0 && (
                    <fieldset>
                      <legend className="form-label d-block fw-medium small mb-2">Amenidades <span className="text-muted fw-normal">(opcional)</span></legend>
                      <div className="d-flex flex-wrap gap-2">
                        {amenidades.map((a) => {
                          const checked = amenidadIds.includes(a.id);
                          return (
                            <button
                              type="button"
                              key={a.id}
                              className="btn btn-sm"
                              style={{
                                borderRadius: 8,
                                fontSize: '0.78rem',
                                background: checked ? 'var(--ss-gold)' : '#fff',
                                color: 'var(--ss-dark)',
                                border: checked ? '1px solid var(--ss-gold)' : '1px solid rgba(34,32,22,0.2)',
                              }}
                              disabled={saving}
                              onClick={() => toggleAmenidad(a.id)}
                            >
                              {checked && <i className="bi bi-check-lg me-1" />}
                              {a.nombre}
                            </button>
                          );
                        })}
                      </div>
                    </fieldset>
                  )}
                </>
              )}

              {error && (
                <div className="alert alert-danger py-2 small mt-3 mb-0" style={{ borderRadius: 8 }}>
                  <i className="bi bi-exclamation-circle-fill me-1" />{error}
                </div>
              )}
            </div>

            <div className="modal-footer px-4 py-3 gap-2" style={{ background: 'var(--ss-cream)', borderTop: '1px solid rgba(34,32,22,0.1)' }}>
              <button type="button" className="btn btn-outline-secondary" onClick={onClose} disabled={saving}>
                Cancelar
              </button>
              <button type="submit" className="btn btn-ss-dark fw-semibold" disabled={!puedeEnviar || saving || tipos.length === 0}>
                {saving
                  ? <><span className="spinner-border spinner-border-sm me-2" />Creando...</>
                  : <><i className="bi bi-check-lg me-1" />Crear habitación</>
                }
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

export default function HabitacionesRecepcion() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const esPlataforma = hasRole('SUPER_ADMIN');
  // '' = este hotel, 'todos' o un tenantId (Plataforma enlaza con ?hotel=)
  const [params] = useSearchParams();
  const [hotelVista, setHotelVista] = useState(esPlataforma ? params.get('hotel') ?? '' : '');
  const hoteles = useHotelesPlataforma(esPlataforma);
  const [hotelesFiltro, setHotelesFiltro] = useState([]);
  const alternarHotel = (id) => setHotelesFiltro(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]);

  const [habitaciones, setHabitaciones] = useState([]);
  const [loading,      setLoading]      = useState(true);
  const [error,        setError]        = useState('');
  const [success,      setSuccess]      = useState('');
  const [updating,     setUpdating]     = useState(null);
  const [successId,    setSuccessId]    = useState(null);
  const [filtro,       setFiltro]       = useState('TODOS');
  const [busqueda,     setBusqueda]     = useState('');

  const [showNueva,   setShowNueva]   = useState(false);
  const [tipos,       setTipos]       = useState([]);
  const [amenidades,  setAmenidades]  = useState([]);
  const [creating,    setCreating]    = useState(false);
  const [createError, setCreateError] = useState('');
  const [deactivating, setDeactivating] = useState(null);
  const [fotosDe,      setFotosDe]      = useState(null);

  const cargar = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getHabitaciones(hotelVista ? { hotel: hotelVista } : undefined);
      setHabitaciones(Array.isArray(data) ? data : data.content ?? []);
    } catch (err) {
      setError(err.response?.data?.message ?? 'Error al cargar habitaciones.');
    } finally {
      setLoading(false);
    }
  }, [hotelVista]);

  useEffect(() => { cargar(); }, [cargar]);


  const handleCambiarEstado = async (id, nuevoEstado) => {
    setUpdating(id);
    try {
      await cambiarEstado(id, { estado: nuevoEstado });
      setHabitaciones(prev => prev.map(h => h.id === id ? { ...h, estado: nuevoEstado } : h));
      setSuccessId(id);
      setTimeout(() => setSuccessId(null), 2000);
    } catch {
      setError('No se pudo actualizar el estado.');
    } finally {
      setUpdating(null);
    }
  };

  const handleAbrirNueva = async () => {
    setShowNueva(true);
    setCreateError('');
    try {
      const [t, a] = await Promise.all([getTiposHabitacion(), getAmenidades()]);
      setTipos(Array.isArray(t) ? t : []);
      setAmenidades(Array.isArray(a) ? a : []);
    } catch {
      setCreateError('No se pudieron cargar los tipos de habitación y amenidades.');
    }
  };

  const handleCrear = async (body) => {
    setCreating(true);
    setCreateError('');
    try {
      const nueva = await crearHabitacion(body);
      setHabitaciones((prev) => [...prev, nueva]);
      setShowNueva(false);
      setSuccess(`Habitación ${nueva.numero} creada correctamente.`);
      setTimeout(() => setSuccess(''), 3500);
    } catch (err) {
      setCreateError(err.response?.data?.message ?? 'No se pudo crear la habitación.');
    } finally {
      setCreating(false);
    }
  };

  const handleDesactivar = async (hab) => {
    if (!window.confirm(`¿Desactivar la habitación ${hab.numero}? Dejará de aparecer en el inventario activo.`)) return;
    setDeactivating(hab.id);
    setError('');
    try {
      await desactivarHabitacion(hab.id);
      setHabitaciones((prev) => prev.filter((h) => h.id !== hab.id));
      setSuccess(`Habitación ${hab.numero} desactivada.`);
      setTimeout(() => setSuccess(''), 3500);
    } catch {
      setError('No se pudo desactivar la habitación.');
    } finally {
      setDeactivating(null);
    }
  };

  const conteo = Object.fromEntries(
    Object.keys(ESTADO_META).map(e => [e, habitaciones.filter(h => h.estado === e).length])
  );

  const habsFiltradas = habitaciones
    .filter(h => !hotelesFiltro.length || hotelesFiltro.includes(h.hotelId))
    .filter(h => filtro === 'TODOS' || h.estado === filtro)
    .filter(h => {
      if (!busqueda.trim()) return true;
      const q = busqueda.toLowerCase();
      return String(h.numero).includes(q) || (h.tipoNombre ?? '').toLowerCase().includes(q)
        || (h.hotelNombre ?? '').toLowerCase().includes(q);
    });

  return (
    <div className="fade-in-up p-3 p-md-4">

      {/* Header */}
      <div className="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-3">
        <div>
          <h2 className="fw-bold mb-0" style={{ color: 'var(--ss-dark)' }}>
            <i className="bi bi-building me-2" style={{ color: 'var(--ss-gold)' }} />Habitaciones
          </h2>
          <small className="text-muted">
            {habitaciones.length} habitaciones en total, {conteo.DISPONIBLE ?? 0} disponibles
            {hotelVista && ' (solo lectura: los cambios se hacen desde cada hotel)'}
          </small>
        </div>
        <div className="d-flex flex-wrap gap-2">
          {esPlataforma && (
            <select className="form-select form-select-sm w-auto" aria-label="Hotel" value={hotelVista}
                    onChange={e => { setHotelVista(e.target.value); setHotelesFiltro([]); }}>
              <option value="">Este hotel</option>
              <option value="todos">Todos los hoteles</option>
              {hoteles.map(h => <option key={h.tenantId} value={h.tenantId}>{h.nombre}</option>)}
            </select>
          )}
          <button className="btn btn-outline-secondary btn-sm" onClick={cargar} disabled={loading}>
            <i className="bi bi-arrow-clockwise me-1" />Actualizar
          </button>
          {isAdmin && !hotelVista && (
            <button className="btn btn-ss-dark btn-sm" onClick={handleAbrirNueva}>
              <i className="bi bi-plus-lg me-1" />Nueva habitación
            </button>
          )}
        </div>
      </div>

      <AlertMessage message={error} onClose={() => setError('')} />
      <AlertMessage type="success" message={success} onClose={() => setSuccess('')} />

      {showNueva && (
        <ModalNuevaHabitacion
          tipos={tipos}
          amenidades={amenidades}
          saving={creating}
          error={createError}
          onClose={() => setShowNueva(false)}
          onCreate={handleCrear}
        />
      )}

      {/* Barra de ocupación */}
      <OcupacionBar conteo={conteo} total={habitaciones.length} />

      {hotelVista === 'todos' && hoteles.length > 1 && (
        <div className="filtros-hotel mb-3" role="group" aria-label="Filtrar por hotel">
          <small className="text-muted me-1">Hoteles:</small>
          {hoteles.map(h => (
            <EtiquetaHotel key={h.tenantId} tenantId={h.tenantId} nombre={h.nombre}
                           activa={hotelesFiltro.includes(h.tenantId)} onClick={() => alternarHotel(h.tenantId)} />
          ))}
          {hotelesFiltro.length > 0 && (
            <button type="button" className="btn btn-link btn-sm p-0 ms-1" onClick={() => setHotelesFiltro([])}>Ver todos</button>
          )}
        </div>
      )}

      {/* Filtros + Búsqueda */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
        <div className="d-flex flex-wrap gap-2">
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
                {e !== 'TODOS' && <i className={`bi ${meta.icon} me-1`} style={{ color: active ? '#fff' : meta.color }} />}
                {e === 'TODOS' ? `Todas (${habitaciones.length})` : `${meta.label} (${conteo[e] ?? 0})`}
              </button>
            );
          })}
        </div>

        <div className="input-group" style={{ maxWidth: 240 }}>
          <span className="input-group-text bg-white border-end-0">
            <i className="bi bi-search text-muted" style={{ fontSize: '0.85rem' }} />
          </span>
          <input
            className="form-control border-start-0"
            style={{ fontSize: '0.85rem' }}
            placeholder="N.° hab., tipo…"
            value={busqueda}
            onChange={e => setBusqueda(e.target.value)}
          />
        </div>
      </div>

      {/* Grid */}
      {loading && <LoadingSpinner message="Cargando habitaciones..." />}
      {!loading && habsFiltradas.length === 0 && (
        <div className="text-center py-5 text-muted">
          <i className="bi bi-building-slash fs-1 d-block mb-2" />No hay habitaciones{filtro !== 'TODOS' ? ` en estado "${ESTADO_META[filtro]?.label ?? filtro}"` : ''}.
        </div>
      )}
      {!loading && habsFiltradas.length > 0 && (
        <div className="row g-3">
          {habsFiltradas.map(h => (
            <HabitacionCard
              key={`${h.hotelId ?? ''}-${h.id}`}
              hab={h}
              soloLectura={!!hotelVista}
              onCambiar={handleCambiarEstado}
              updating={updating}
              success={successId}
              isAdmin={isAdmin}
              onDesactivar={handleDesactivar}
              deactivating={deactivating}
              onFotos={setFotosDe}
            />
          ))}
        </div>
      )}

      {fotosDe && (
        <ModalFotosHabitacion
          habitacion={fotosDe}
          onClose={() => setFotosDe(null)}
          onChange={(h) => setHabitaciones(prev => prev.map(x => x.id === h.id ? h : x))}
        />
      )}
    </div>
  );
}
