import { useState, useEffect } from 'react';
import { getReservasHoy, cambiarEstadoReserva } from '../../services/reservasService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import AlertMessage   from '../../components/common/AlertMessage';

const fmt = (d) => d ? new Date(d).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' }) : '—';
const noches = (e, s) => {
  try { return Math.max(1, Math.round((new Date(s) - new Date(e)) / 86400000)); }
  catch { return '—'; }
};

const URGENCY = (fechaEntrada) => {
  const diff = Math.round((new Date(fechaEntrada) - new Date()) / 86400000);
  if (diff < 0)  return { label: 'Atrasado', cls: 'bg-danger text-white' };
  if (diff === 0) return { label: 'Hoy',      cls: 'bg-warning text-dark' };
  return { label: `En ${diff}d`,              cls: 'bg-light text-muted' };
};

function ReservaRow({ r, onAction, updating, accion, nuevoEstado, btnVariant }) {
  const u = URGENCY(r.fechaEntrada);
  const isUpdating = updating === r.id;
  return (
    <tr className={isUpdating ? 'table-active' : ''}>
      <td>
        <span className="fw-semibold text-muted small">#{r.id}</span>
        {accion === 'Check-in' && (
          <span className={`badge ms-1 small ${u.cls}`}>{u.label}</span>
        )}
      </td>
      <td>
        <span
          className="fw-bold px-2 py-1 rounded"
          style={{ background: 'rgba(239,193,67,0.15)', color: 'var(--ss-dark)', fontSize: '0.9rem' }}
        >
          Hab. {r.habitacionNumero ?? r.habitacionId ?? '—'}
        </span>
      </td>
      <td className="small">{fmt(r.fechaEntrada)}</td>
      <td className="small">{fmt(r.fechaSalida)}</td>
      <td className="text-center small">{r.numHuespedes ?? '—'}</td>
      <td className="small fw-semibold">
        {r.precioTotal ? `$${Number(r.precioTotal).toLocaleString('es-CO')}` : '—'}
        {r.fechaEntrada && r.fechaSalida && (
          <div className="text-muted fw-normal">{noches(r.fechaEntrada, r.fechaSalida)}n</div>
        )}
      </td>
      <td>
        <button
          className={`btn btn-sm ${btnVariant} fw-semibold`}
          style={{ whiteSpace: 'nowrap', minWidth: 110 }}
          disabled={isUpdating}
          onClick={() => onAction(r.id, nuevoEstado, accion)}
        >
          {isUpdating
            ? <><span className="spinner-border spinner-border-sm me-1" />Procesando…</>
            : accion === 'Check-in'
              ? <><i className="bi bi-box-arrow-in-right me-1" />Check-in</>
              : <><i className="bi bi-box-arrow-right me-1" />Check-out</>
          }
        </button>
      </td>
    </tr>
  );
}

function TablaVacia({ accion }) {
  return (
    <div className="text-center py-5">
      <div
        className="mx-auto mb-3 rounded-circle d-flex align-items-center justify-content-center"
        style={{ width: 72, height: 72, background: 'rgba(239,193,67,0.12)' }}
      >
        <i className="bi bi-calendar-check fs-2" style={{ color: 'var(--ss-gold)' }} />
      </div>
      <p className="text-muted mb-0">
        No hay reservas pendientes de <strong>{accion.toLowerCase()}</strong> en este momento.
      </p>
    </div>
  );
}

export default function CheckinRecepcion() {
  const [pendientesCheckin,  setPendientesCheckin]  = useState([]);
  const [pendientesCheckout, setPendientesCheckout] = useState([]);
  const [loading,   setLoading]   = useState(true);
  const [error,     setError]     = useState('');
  const [success,   setSuccess]   = useState('');
  const [updating,  setUpdating]  = useState(null);
  const [tab,       setTab]       = useState('checkin');
  const [busqueda,  setBusqueda]  = useState('');

  useEffect(() => {
    getReservasHoy()
      .then(data => {
        setPendientesCheckin(data.pendientesCheckin ?? []);
        setPendientesCheckout(data.pendientesCheckout ?? []);
      })
      .catch(() => setError('Error al cargar las reservas del día.'))
      .finally(() => setLoading(false));
  }, []);

  const filtrar = (lista) => {
    if (!busqueda.trim()) return lista;
    const q = busqueda.toLowerCase();
    return lista.filter(r =>
      String(r.id).includes(q) ||
      String(r.habitacionNumero ?? r.habitacionId ?? '').includes(q) ||
      (r.nombreContacto ?? r.nombre ?? '').toLowerCase().includes(q)
    );
  };

  const ejecutar = async (id, nuevoEstado, accion) => {
    setUpdating(id); setError(''); setSuccess('');
    try {
      await cambiarEstadoReserva(id, { estado: nuevoEstado });
      // Mover la reserva entre listas según el nuevo estado
      if (nuevoEstado === 'CHECKIN') {
        setPendientesCheckin(prev => {
          const reserva = prev.find(r => r.id === id);
          if (reserva) setPendientesCheckout(co => [...co, { ...reserva, estado: 'CHECKIN' }]);
          return prev.filter(r => r.id !== id);
        });
      } else if (nuevoEstado === 'CHECKOUT') {
        setPendientesCheckout(prev => prev.filter(r => r.id !== id));
      }
      setSuccess(`${accion} completado para la reserva #${id}.`);
    } catch (err) {
      setError(err?.response?.data?.message ?? `No se pudo realizar el ${accion.toLowerCase()}.`);
    } finally {
      setUpdating(null);
    }
  };

  const listaActual  = tab === 'checkin' ? filtrar(pendientesCheckin) : filtrar(pendientesCheckout);
  const accionActual = tab === 'checkin' ? 'Check-in' : 'Check-out';
  const estadoNuevo  = tab === 'checkin' ? 'CHECKIN' : 'CHECKOUT';
  const btnVariant   = tab === 'checkin' ? 'btn-ss-dark' : 'btn-outline-primary';

  const TABS = [
    { key: 'checkin',  icon: 'bi-box-arrow-in-right', label: 'Check-in',  count: pendientesCheckin.length,  color: '#198754' },
    { key: 'checkout', icon: 'bi-box-arrow-right',    label: 'Check-out', count: pendientesCheckout.length, color: '#0d6efd' },
  ];

  return (
    <div className="fade-in-up p-3 p-md-4">

      {/* Header */}
      <div className="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-3">
        <div>
          <h2 className="fw-bold mb-0" style={{ color: 'var(--ss-dark)' }}>
            <i className="bi bi-door-open me-2" style={{ color: 'var(--ss-gold)' }} />
            Check-in / Check-out
          </h2>
          <small className="text-muted">
            Gestión de llegadas y salidas de hoy
            <span className="ms-3 text-body-secondary">
              <i className="bi bi-clock me-1" />
              Check-in: 15:00 · Check-out: 12:00
            </span>
          </small>
        </div>
      </div>

      <AlertMessage message={error}   onClose={() => setError('')} />
      <AlertMessage type="success" message={success} onClose={() => setSuccess('')} />

      {/* Stat pills */}
      <div className="row g-3 mb-4">
        {TABS.map(t => (
          <div key={t.key} className="col-6 col-md-3">
            <div
              className="card border-0 shadow-sm h-100"
              style={{ borderLeft: `4px solid ${t.color}`, borderRadius: 12 }}
            >
              <div className="card-body d-flex align-items-center gap-3 py-3">
                <div
                  className="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0"
                  style={{ width: 44, height: 44, background: t.color + '18' }}
                >
                  <i className={`bi ${t.icon}`} style={{ color: t.color, fontSize: '1.2rem' }} />
                </div>
                <div>
                  <div className="fw-bold fs-4 lh-1">{t.count}</div>
                  <small className="text-muted">{t.label} pendientes</small>
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Tabs + Búsqueda */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
        <ul className="nav nav-pills gap-2 mb-0">
          {TABS.map(t => (
            <li key={t.key} className="nav-item">
              <button
                className={`nav-link fw-semibold px-4 ${tab === t.key ? 'active' : ''}`}
                style={tab === t.key
                  ? { backgroundColor: 'var(--ss-dark)', color: '#fff', borderRadius: 8 }
                  : { color: 'var(--ss-dark)', borderRadius: 8 }
                }
                onClick={() => { setTab(t.key); setBusqueda(''); }}
              >
                <i className={`bi ${t.icon} me-2`} />
                {t.label}
                <span
                  className="badge ms-2"
                  style={{ background: tab === t.key ? 'rgba(239,193,67,0.3)' : 'rgba(34,32,22,0.1)', color: 'inherit' }}
                >
                  {t.count}
                </span>
              </button>
            </li>
          ))}
        </ul>

        <div className="input-group" style={{ maxWidth: 280 }}>
          <span className="input-group-text bg-white border-end-0">
            <i className="bi bi-search text-muted" style={{ fontSize: '0.85rem' }} />
          </span>
          <input
            className="form-control border-start-0"
            style={{ fontSize: '0.9rem' }}
            placeholder="Buscar por hab., huésped, #"
            value={busqueda}
            onChange={e => setBusqueda(e.target.value)}
          />
        </div>
      </div>

      {/* Tabla */}
      {loading ? (
        <LoadingSpinner message="Cargando reservas..." />
      ) : listaActual.length === 0 ? (
        <TablaVacia accion={accionActual} />
      ) : (
        <div className="card border-0 shadow-sm" style={{ borderRadius: 12 }}>
          <div className="table-responsive">
            <table className="table table-hover align-middle mb-0">
              <thead style={{ background: 'var(--ss-cream)' }}>
                <tr>
                  <th className="ps-3 small text-muted fw-semibold">Reserva</th>
                  <th className="small text-muted fw-semibold">Hab.</th>
                  <th className="small text-muted fw-semibold">Entrada</th>
                  <th className="small text-muted fw-semibold">Salida</th>
                  <th className="text-center small text-muted fw-semibold">Huésp.</th>
                  <th className="small text-muted fw-semibold">Total</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {listaActual.map(r => (
                  <ReservaRow
                    key={r.id}
                    r={r}
                    onAction={ejecutar}
                    updating={updating}
                    accion={accionActual}
                    nuevoEstado={estadoNuevo}
                    btnVariant={btnVariant}
                  />
                ))}
              </tbody>
            </table>
          </div>
          <div className="px-3 py-2 border-top" style={{ background: 'var(--ss-cream)', borderRadius: '0 0 12px 12px' }}>
            <small className="text-muted">{listaActual.length} reserva{listaActual.length !== 1 ? 's' : ''}</small>
          </div>
        </div>
      )}
    </div>
  );
}
