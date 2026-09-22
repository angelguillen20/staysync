import { useState, useEffect, useMemo } from 'react';
import { getReservas, cambiarEstadoReserva } from '../../services/reservasService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import AlertMessage   from '../../components/common/AlertMessage';
import ModalReserva   from '../../components/recepcion/ModalReserva';

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

const TRANSICION_LABEL = {
  CONFIRMADA: 'Confirmar',
  CHECKIN:    'Activar',
  CHECKOUT:   'Finalizar',
  CANCELADA:  'Cancelar',
};

const TRANSICIONES = {
  PENDIENTE:  ['CONFIRMADA', 'CANCELADA'],
  CONFIRMADA: ['CHECKIN',    'CANCELADA'],
  CHECKIN:    ['CHECKOUT'],
};

function SortIcon({ campo, sortConfig }) {
  if (sortConfig.campo !== campo) {
    return <i className="bi bi-arrow-down-up ms-1 text-muted" style={{ fontSize: '0.7rem', opacity: 0.5 }} />;
  }
  return sortConfig.dir === 'asc'
    ? <i className="bi bi-sort-up ms-1" style={{ fontSize: '0.75rem', color: 'var(--ss-gold)' }} />
    : <i className="bi bi-sort-down ms-1" style={{ fontSize: '0.75rem', color: 'var(--ss-gold)' }} />;
}

export default function ReservasRecepcion() {
  const [reservas,  setReservas]  = useState([]);
  const [loading,   setLoading]   = useState(true);
  const [error,     setError]     = useState('');
  const [updating,  setUpdating]  = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [busqueda,  setBusqueda]  = useState('');
  const [sortConfig, setSortConfig] = useState({ campo: 'fechaEntrada', dir: 'asc' });

  const cargar = async () => {
    setLoading(true);
    try {
      const data = await getReservas();
      setReservas(Array.isArray(data) ? data : data.content ?? []);
    } catch {
      setError('Error al cargar reservas.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { cargar(); }, []);

  const handleSort = (campo) => {
    setSortConfig(prev =>
      prev.campo === campo
        ? { campo, dir: prev.dir === 'asc' ? 'desc' : 'asc' }
        : { campo, dir: 'asc' }
    );
  };

  const handleCambiarEstado = async (id, estado) => {
    setUpdating(id + estado);
    try {
      await cambiarEstadoReserva(id, { estado });
      setReservas(prev => prev.map(r => r.id === id ? { ...r, estado } : r));
    } catch {
      setError('No se pudo actualizar el estado.');
    } finally {
      setUpdating(null);
    }
  };

  const filtradas = useMemo(() => {
    let result = reservas.filter(r => {
      if (r.estado === 'CANCELADA' || r.estado === 'CHECKOUT') return false;
      if (!busqueda) return true;
      const q = busqueda.toLowerCase();
      return (
        String(r.id).includes(q) ||
        r.codigo?.toLowerCase().includes(q) ||
        String(r.usuarioId).includes(q) ||
        String(r.habitacionNumero ?? r.habitacionId).includes(q)
      );
    });

    result = [...result].sort((a, b) => {
      let vA, vB;
      switch (sortConfig.campo) {
        case 'fechaEntrada':
          vA = a.fechaEntrada ?? '';
          vB = b.fechaEntrada ?? '';
          break;
        case 'fechaSalida':
          vA = a.fechaSalida ?? '';
          vB = b.fechaSalida ?? '';
          break;
        case 'habitacion':
          vA = Number(a.habitacionNumero ?? a.habitacionId ?? 0);
          vB = Number(b.habitacionNumero ?? b.habitacionId ?? 0);
          break;
        case 'total':
          vA = Number(a.precioTotal ?? 0);
          vB = Number(b.precioTotal ?? 0);
          break;
        default:
          return 0;
      }
      if (vA < vB) return sortConfig.dir === 'asc' ? -1 : 1;
      if (vA > vB) return sortConfig.dir === 'asc' ?  1 : -1;
      return 0;
    });

    return result;
  }, [reservas, busqueda, sortConfig]);

  const thSort = (campo, label) => (
    <th
      style={{ cursor: 'pointer', userSelect: 'none', whiteSpace: 'nowrap' }}
      onClick={() => handleSort(campo)}
    >
      {label}
      <SortIcon campo={campo} sortConfig={sortConfig} />
    </th>
  );

  return (
    <div className="fade-in-up p-3 p-md-4">
      <div className="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-3">
        <div>
          <h2 className="fw-bold mb-0" style={{ color: 'var(--ss-dark)' }}>
            <i className="bi bi-calendar-check me-2" style={{ color: 'var(--ss-gold)' }} />
            Reservas
          </h2>
          <small className="text-muted">Gestión de reservas del hotel</small>
        </div>
        <div className="d-flex gap-2">
          <button className="btn btn-outline-secondary btn-sm" onClick={cargar}>
            <i className="bi bi-arrow-clockwise" />
          </button>
          <button className="btn btn-ss-dark" onClick={() => setModalOpen(true)}>
            <i className="bi bi-calendar-plus me-2" />Nueva reserva
          </button>
        </div>
      </div>

      <AlertMessage message={error} onClose={() => setError('')} />

      <div className="mb-3" style={{ maxWidth: 340 }}>
        <div className="input-group">
          <span className="input-group-text"><i className="bi bi-search" /></span>
          <input
            className="form-control"
            placeholder="Buscar por ID, código o habitación..."
            value={busqueda}
            onChange={e => setBusqueda(e.target.value)}
          />
        </div>
      </div>

      {loading
        ? <LoadingSpinner message="Cargando reservas..." />
        : filtradas.length === 0
          ? (
            <div className="text-center py-5 text-muted">
              <i className="bi bi-calendar-x fs-1 d-block mb-2" />
              No se encontraron reservas.
            </div>
          )
          : (
            <div className="table-responsive">
              <table className="table table-hover align-middle">
                <thead className="table-light">
                  <tr>
                    <th>#</th>
                    <th>Código / Usuario</th>
                    {thSort('habitacion',   'Habitación')}
                    {thSort('fechaEntrada', 'Entrada')}
                    {thSort('fechaSalida',  'Salida')}
                    <th>Huésp.</th>
                    {thSort('total', 'Total')}
                    <th>Estado</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {filtradas.map(r => {
                    const next = TRANSICIONES[r.estado] ?? [];
                    return (
                      <tr key={r.id}>
                        <td className="text-muted small">#{r.id}</td>
                        <td>
                          <div className="fw-medium small">{r.codigo ?? '—'}</div>
                          <div className="text-muted" style={{ fontSize: '0.72rem' }}>Usr. #{r.usuarioId}</div>
                        </td>
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
                          <div className="d-flex gap-1 flex-wrap">
                            {next.map(estado => (
                              <button
                                key={estado}
                                className={`btn btn-sm ${estado === 'CANCELADA' ? 'btn-outline-danger' : 'btn-outline-primary'}`}
                                style={{ fontSize: '0.72rem' }}
                                disabled={updating === r.id + estado}
                                onClick={() => handleCambiarEstado(r.id, estado)}
                              >
                                {updating === r.id + estado
                                  ? <span className="spinner-border spinner-border-sm" />
                                  : TRANSICION_LABEL[estado] ?? estado
                                }
                              </button>
                            ))}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
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
