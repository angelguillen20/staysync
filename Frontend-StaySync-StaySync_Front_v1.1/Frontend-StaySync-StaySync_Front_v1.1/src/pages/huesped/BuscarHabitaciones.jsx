import { useState, useEffect } from 'react';
import { useNavigate }    from 'react-router-dom';
import { getDisponibles } from '../../services/habitacionesService';
import { crearReserva }   from '../../services/reservasService';
import { useAuth }        from '../../context/AuthContext';
import LoadingSpinner     from '../../components/common/LoadingSpinner';
import AlertMessage       from '../../components/common/AlertMessage';

export default function BuscarHabitaciones() {
  const { user } = useAuth();
  const navigate  = useNavigate();
  const [habitaciones, setHabitaciones] = useState([]);
  const [loading,      setLoading]      = useState(true);
  const [error,        setError]        = useState('');
  const [success,      setSuccess]      = useState('');
  const [reservando,   setReservando]   = useState(null);

  const [filtros, setFiltros] = useState({ entrada: '', salida: '', huespedes: 1 });

  useEffect(() => {
    getDisponibles()
      .then(data => setHabitaciones(Array.isArray(data) ? data : []))
      .catch(() => setError('Error al cargar habitaciones disponibles.'))
      .finally(() => setLoading(false));
  }, []);

  const handleReservar = async (hab) => {
    if (!filtros.entrada || !filtros.salida) {
      setError('Por favor selecciona fechas de entrada y salida antes de reservar.');
      return;
    }
    setReservando(hab.id); setError('');
    try {
      const reserva = await crearReserva({
        habitacionId:   hab.id,
        usuarioId:      user?.userId,
        fechaEntrada:   filtros.entrada,
        fechaSalida:    filtros.salida,
        numHuespedes:   Number(filtros.huespedes),
        fuente:         'WEB',
        nombreContacto: `${user?.nombre} ${user?.apellido}`,
        emailContacto:  user?.email,
      });
      navigate('/huesped/pago', {
        state: {
          reservaId:       reserva.id,
          codigo:          reserva.codigo,
          habitacionNumero: hab.numero,
          fechaEntrada:    filtros.entrada,
          fechaSalida:     filtros.salida,
          precioTotal:     reserva.precioTotal,
        },
      });
    } catch (err) {
      setError(err.response?.data?.message ?? 'No se pudo crear la reserva. Intente nuevamente.');
    } finally {
      setReservando(null);
    }
  };

  return (
    <div className="fade-in-up p-3 p-md-4">
      <h2 className="fw-bold mb-1" style={{ color: 'var(--ss-dark)' }}>
        <i className="bi bi-search me-2" style={{ color: 'var(--ss-gold)' }} />
        Buscar habitaciones
      </h2>
      <p className="text-muted mb-4">Habitaciones disponibles para reservar</p>

      {/* Filtros */}
      <div className="card border-0 shadow-sm mb-4">
        <div className="card-body">
          <div className="row g-3 align-items-end">
            <div className="col-md-4">
              <label className="form-label fw-medium small">Fecha de entrada</label>
              <input
                type="date"
                className="form-control"
                min={new Date().toISOString().split('T')[0]}
                value={filtros.entrada}
                onChange={e => setFiltros(p => ({ ...p, entrada: e.target.value }))}
              />
            </div>
            <div className="col-md-4">
              <label className="form-label fw-medium small">Fecha de salida</label>
              <input
                type="date"
                className="form-control"
                min={filtros.entrada || new Date().toISOString().split('T')[0]}
                value={filtros.salida}
                onChange={e => setFiltros(p => ({ ...p, salida: e.target.value }))}
              />
            </div>
            <div className="col-md-4">
              <label className="form-label fw-medium small">N° huéspedes</label>
              <input
                type="number"
                className="form-control"
                min="1" max="10"
                value={filtros.huespedes}
                onChange={e => setFiltros(p => ({ ...p, huespedes: e.target.value }))}
              />
            </div>
          </div>
        </div>
      </div>

      <AlertMessage message={error}   onClose={() => setError('')} />
      <AlertMessage type="success" message={success} onClose={() => setSuccess('')} />

      {loading
        ? <LoadingSpinner message="Buscando habitaciones disponibles..." />
        : habitaciones.length === 0
          ? (
            <div className="text-center py-5 text-muted">
              <i className="bi bi-calendar-x fs-1 d-block mb-2" />
              No hay habitaciones disponibles en este momento.
            </div>
          )
          : (
            <div className="row g-3">
              {habitaciones.map(h => (
                <div key={h.id} className="col-md-6 col-xl-4">
                  <div className="card border-0 shadow-sm h-100">
                    <div className="card-body">
                      <div className="d-flex justify-content-between align-items-start mb-2">
                        <h5 className="fw-bold mb-0">Habitación {h.numero}</h5>
                        <span className="badge bg-success">Disponible</span>
                      </div>
                      <p className="text-muted small mb-1">
                        <i className="bi bi-tag me-1" />{h.tipo}
                      </p>
                      <p className="text-muted small mb-1">
                        <i className="bi bi-people me-1" />Capacidad: {h.capacidad} personas
                      </p>
                      {h.descripcion && (
                        <p className="text-muted small mb-2">{h.descripcion}</p>
                      )}
                      <div className="d-flex justify-content-between align-items-center mt-3">
                        <span className="fw-bold fs-5" style={{ color: 'var(--ss-dark)' }}>
                          ${h.precioPorNoche?.toLocaleString()}
                          <small className="fw-normal text-muted fs-6">/noche</small>
                        </span>
                        <button
                          className="btn btn-ss-dark btn-sm px-3"
                          disabled={reservando === h.id}
                          onClick={() => handleReservar(h)}
                        >
                          {reservando === h.id
                            ? <span className="spinner-border spinner-border-sm" />
                            : <><i className="bi bi-calendar-plus me-1" />Reservar</>
                          }
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )
      }
    </div>
  );
}
