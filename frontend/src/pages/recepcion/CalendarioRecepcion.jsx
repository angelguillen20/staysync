import { useEffect, useMemo, useState } from 'react';
import { getHabitaciones } from '../../services/habitacionesService';
import { getReservasRango } from '../../services/reservasService';
import AlertMessage from '../../components/common/AlertMessage';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import Placa from '../../components/common/Placa';
import ModalCuenta from '../../components/recepcion/ModalCuenta';
import { fmtFecha, hoyHotel, listaDias, parseFecha, sumarDias } from '../../utils/fechas';
import { filasCalendario } from '../../utils/ocupacion';

const ESTADOS = [
  { estado: 'PENDIENTE',  label: 'Sin pagar' },
  { estado: 'CONFIRMADA', label: 'Confirmada' },
  { estado: 'CHECKIN',    label: 'Alojado' },
  { estado: 'CHECKOUT',   label: 'Se fue' },
];

const diaSemana = (iso) => parseFecha(iso).toLocaleDateString('es-CL', { weekday: 'short' }).replace('.', '');
const esFinDeSemana = (iso) => [0, 6].includes(parseFecha(iso).getDay());

export default function CalendarioRecepcion() {
  const [inicio, setInicio] = useState(hoyHotel());
  const [largo,  setLargo]  = useState(14);
  const [habitaciones, setHabitaciones] = useState([]);
  const [reservas, setReservas] = useState([]);
  const [loading,  setLoading]  = useState(true);
  const [error,    setError]    = useState('');
  const [elegida,  setElegida]  = useState(null);
  const [cuenta,   setCuenta]   = useState(null);

  const dias = useMemo(() => listaDias(inicio, largo), [inicio, largo]);
  const hoy = hoyHotel();

  useEffect(() => {
    setLoading(true); setError('');
    Promise.all([getHabitaciones(), getReservasRango(inicio, sumarDias(inicio, largo))])
      .then(([habs, res]) => {
        setHabitaciones([...habs].sort((a, b) => String(a.numero).localeCompare(String(b.numero), 'es', { numeric: true })));
        setReservas(res);
      })
      .catch(err => setError(err.response?.data?.message ?? 'No se pudo cargar el calendario.'))
      .finally(() => setLoading(false));
  }, [inicio, largo]);

  const filas = useMemo(() => filasCalendario(habitaciones, reservas, dias), [habitaciones, reservas, dias]);
  const numero = (id) => habitaciones.find(h => h.id === id)?.numero ?? id;

  return (
    <div className="fade-in-up p-3 p-md-4">
      <div className="d-flex flex-wrap justify-content-between align-items-end gap-3 mb-3">
        <div>
          <h2 className="mb-1">Calendario de ocupación</h2>
          <p className="text-muted mb-0">
            Del {fmtFecha(dias[0], { day: 'numeric', month: 'long' })} al {fmtFecha(dias[dias.length - 1], { day: 'numeric', month: 'long' })}
          </p>
        </div>
        <div className="d-flex flex-wrap gap-2 align-items-center">
          <div className="btn-group" role="group" aria-label="Moverse en el calendario">
            <button className="btn btn-outline-secondary" onClick={() => setInicio(sumarDias(inicio, -largo))} aria-label="Período anterior">
              <i className="bi bi-chevron-left" aria-hidden="true" />
            </button>
            <button className="btn btn-outline-secondary" onClick={() => setInicio(hoyHotel())} disabled={inicio === hoy}>Hoy</button>
            <button className="btn btn-outline-secondary" onClick={() => setInicio(sumarDias(inicio, largo))} aria-label="Período siguiente">
              <i className="bi bi-chevron-right" aria-hidden="true" />
            </button>
          </div>
          <select className="form-select w-auto" value={largo} onChange={e => setLargo(Number(e.target.value))} aria-label="Días a mostrar">
            <option value={14}>2 semanas</option>
            <option value={30}>30 días</option>
          </select>
        </div>
      </div>

      <div className="d-flex flex-wrap gap-3 mb-3 small" aria-label="Colores del calendario">
        {ESTADOS.map(e => (
          <span key={e.estado} className="d-inline-flex align-items-center gap-1">
            <span className={`estadia-${e.estado}`} style={{ width: 14, height: 14, borderRadius: 3, display: 'inline-block' }} />
            {e.label}
          </span>
        ))}
      </div>

      <AlertMessage message={error} onClose={() => setError('')} />
      {loading && <LoadingSpinner message="Cargando calendario…" />}

      {!loading && habitaciones.length === 0 && !error && (
        <p className="text-muted">Agrega habitaciones para ver su ocupación aquí.</p>
      )}

      {!loading && habitaciones.length > 0 && (
        <div className="calendario">
          <table>
            <thead>
              <tr>
                <th className="col-hab" scope="col">Hab.</th>
                {dias.map(d => (
                  <th key={d} scope="col" className={`${d === hoy ? 'hoy' : ''} ${esFinDeSemana(d) ? 'fin-de-semana' : ''}`}>
                    <div>{diaSemana(d)}</div>
                    <div style={{ fontSize: '0.85rem' }}>{parseFecha(d).getDate()}</div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filas.map(({ habitacion, celdas }) => (
                <tr key={habitacion.id}>
                  <th scope="row" className="col-hab"><Placa numero={habitacion.numero} tamano="sm" /></th>
                  {celdas.map(c => c.tipo === 'libre'
                    ? <td key={c.dia} className={esFinDeSemana(c.dia) ? 'fin-de-semana' : ''} />
                    : (
                      <td key={c.dia} colSpan={c.span} style={{ padding: '0 2px' }}>
                        <button
                          type="button"
                          className={`estadia estadia-${c.reserva.estado}`}
                          onClick={() => setElegida(c.reserva)}
                          title={`${c.reserva.huespedNombre ?? c.reserva.codigo}: ${fmtFecha(c.reserva.fechaEntrada)} al ${fmtFecha(c.reserva.fechaSalida)}`}
                        >
                          {c.continua && '‹ '}{c.reserva.huespedNombre || c.reserva.codigo}
                        </button>
                      </td>
                    ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {elegida && (
        <div className="card shadow-sm mt-3" style={{ maxWidth: 520 }}>
          <div className="card-body d-flex gap-3 align-items-start">
            <Placa numero={numero(elegida.habitacionId)} tamano="lg" />
            <div className="flex-grow-1">
              <div className="fw-semibold">{elegida.huespedNombre || 'Huésped sin nombre'}</div>
              <div className="text-muted small">
                {elegida.codigo}, {fmtFecha(elegida.fechaEntrada)} al {fmtFecha(elegida.fechaSalida)}, {elegida.numHuespedes} pers.
              </div>
              <div className="small mt-1">{ESTADOS.find(e => e.estado === elegida.estado)?.label ?? elegida.estado}</div>
            </div>
            <div className="d-flex flex-column gap-2">
              <button className="btn btn-sm btn-ss-dark" onClick={() => setCuenta(elegida)}>Ver cuenta</button>
              <button className="btn btn-sm btn-outline-secondary" onClick={() => setElegida(null)}>Cerrar</button>
            </div>
          </div>
        </div>
      )}

      {cuenta && <ModalCuenta reserva={cuenta} onClose={() => setCuenta(null)} />}
    </div>
  );
}
