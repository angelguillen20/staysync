import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { getUsuarios } from '../../services/usuariosService';
import EtiquetaHotel, { useHotelesPlataforma } from '../../components/common/EtiquetaHotel';
import { getHabitaciones } from '../../services/habitacionesService';
import { getReservasRango } from '../../services/reservasService';
import AlertMessage from '../../components/common/AlertMessage';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import { hoyHotel, listaDias, nochesEntre, parseFecha } from '../../utils/fechas';
import { resumenPorHotel } from '../../utils/ocupacion';

const clp = (v) => `$${Math.round(v).toLocaleString('es-CL')}`;
const pct = (v) => `${Math.round(v * 100)}%`;

/* El mes en curso: [primer día, primer día del mes siguiente) */
function mesActual() {
  const hoy = hoyHotel();
  const desde = `${hoy.slice(0, 7)}-01`;
  const d = parseFecha(desde);
  d.setMonth(d.getMonth() + 1);
  return [desde, `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`];
}

/* Panel del SUPER_ADMIN: todos los hoteles de la plataforma de un vistazo. */
export default function PlataformaAdmin() {
  const hoteles = useHotelesPlataforma(true);
  const [habitaciones, setHabitaciones] = useState([]);
  const [reservas, setReservas] = useState([]);
  const [usuarios, setUsuarios] = useState({}); // tenantId → cantidad
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [desde, hasta] = mesActual();
  const dias = useMemo(() => listaDias(desde, nochesEntre(desde, hasta)), [desde, hasta]);
  const hoy = hoyHotel();

  useEffect(() => {
    Promise.all([getHabitaciones({ hotel: 'todos' }), getReservasRango(desde, hasta, 'todos')])
      .then(([habs, res]) => { setHabitaciones(habs); setReservas(res); })
      .catch(err => setError(err.response?.data?.message ?? 'No se pudo cargar la plataforma.'))
      .finally(() => setLoading(false));
  }, [desde, hasta]);

  // Total de cuentas por hotel: la página de usuarios trae totalElements
  useEffect(() => {
    Promise.all(hoteles.map(h =>
      getUsuarios({ tenantId: h.tenantId, size: 1 }).then(p => [h.tenantId, p.totalElements ?? 0]).catch(() => [h.tenantId, null])))
      .then(t => setUsuarios(Object.fromEntries(t)));
  }, [hoteles]);

  const filas = useMemo(() => resumenPorHotel(hoteles, habitaciones, reservas, desde, hasta, dias, hoy),
    [hoteles, habitaciones, reservas, desde, hasta, dias, hoy]);
  const total = (campo) => filas.reduce((n, f) => n + (f[campo] ?? 0), 0);
  const ocupacionTotal = (() => {
    const capacidad = total('habitaciones') * dias.length;
    return capacidad ? filas.reduce((n, f) => n + f.ocupacion * f.habitaciones * dias.length, 0) / capacidad : 0;
  })();
  const nombreMes = parseFecha(desde).toLocaleDateString('es-CL', { month: 'long', year: 'numeric' });

  return (
    <div className="fade-in-up p-3 p-md-4">
      <div className="d-flex flex-wrap justify-content-between align-items-end gap-3 mb-4">
        <div>
          <h2 className="mb-1">Plataforma</h2>
          <p className="text-muted mb-0">Todos los hoteles: lo de hoy y el mes de {nombreMes}.</p>
        </div>
        <div className="d-flex flex-wrap gap-2">
          <Link to="/recepcion/hoteles" className="btn btn-ss-dark btn-sm">Crear hotel</Link>
          <Link to="/recepcion/usuarios" className="btn btn-outline-secondary btn-sm">Usuarios de todos los hoteles</Link>
          <Link to="/recepcion/habitaciones?hotel=todos" className="btn btn-outline-secondary btn-sm">Habitaciones de todos</Link>
          <Link to="/recepcion/reservas?hotel=todos" className="btn btn-outline-secondary btn-sm">Reservas de todos</Link>
        </div>
      </div>

      <AlertMessage message={error} onClose={() => setError('')} />
      {loading && <LoadingSpinner message="Juntando los datos de cada hotel…" />}

      {!loading && !error && (
        <>
          <div className="row g-3 mb-4">
            {[
              [hoteles.length, 'Hoteles activos'],
              [total('habitaciones'), 'Habitaciones'],
              [total('alojados'), 'Huéspedes alojados hoy'],
              [clp(total('ingresos')), `Ingresos de ${parseFecha(desde).toLocaleDateString('es-CL', { month: 'long' })}`],
            ].map(([valor, nombre]) => (
              <div key={nombre} className="col-6 col-lg-3">
                <div className="card shadow-sm h-100"><div className="card-body">
                  <div className="cifra">{valor}</div>
                  <div className="fw-medium mt-2">{nombre}</div>
                </div></div>
              </div>
            ))}
          </div>

          <div className="card shadow-sm">
            <div className="table-responsive">
              <table className="table align-middle mb-0">
                <thead>
                  <tr>
                    <th className="ps-3">Hotel</th>
                    <th className="text-end">Habitaciones</th>
                    <th className="text-end">Ocupadas hoy</th>
                    <th className="text-end">Llegadas hoy</th>
                    <th className="text-end">Sin pagar</th>
                    <th className="text-end">Ocupación del mes</th>
                    <th className="text-end">Ingresos del mes</th>
                    <th className="text-end pe-3">Usuarios</th>
                  </tr>
                </thead>
                <tbody>
                  {filas.map(f => (
                    <tr key={f.hotel.tenantId}>
                      <td className="ps-3">
                        <EtiquetaHotel tenantId={f.hotel.tenantId} nombre={f.hotel.nombre} />
                        <div className="enlaces-hotel mt-1">
                          <Link to={`/recepcion/reservas?hotel=${f.hotel.tenantId}`}>Reservas</Link>
                          <Link to={`/recepcion/habitaciones?hotel=${f.hotel.tenantId}`}>Habitaciones</Link>
                          <Link to={`/recepcion/usuarios?hotel=${f.hotel.tenantId}`}>Usuarios</Link>
                        </div>
                      </td>
                      <td className="text-end">{f.habitaciones}</td>
                      <td className="text-end">{f.ocupadasHoy}</td>
                      <td className="text-end">{f.llegadasHoy}</td>
                      <td className="text-end">{f.sinPagar}</td>
                      <td className="text-end">{f.habitaciones ? pct(f.ocupacion) : '—'}</td>
                      <td className="text-end">{clp(f.ingresos)}</td>
                      <td className="text-end pe-3">{usuarios[f.hotel.tenantId] ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
                {filas.length > 1 && (
                  <tfoot>
                    <tr className="fw-semibold">
                      <td className="ps-3">Total</td>
                      <td className="text-end">{total('habitaciones')}</td>
                      <td className="text-end">{total('ocupadasHoy')}</td>
                      <td className="text-end">{total('llegadasHoy')}</td>
                      <td className="text-end">{total('sinPagar')}</td>
                      <td className="text-end">{pct(ocupacionTotal)}</td>
                      <td className="text-end">{clp(total('ingresos'))}</td>
                      <td className="text-end pe-3">{Object.values(usuarios).reduce((n, v) => n + (v ?? 0), 0)}</td>
                    </tr>
                  </tfoot>
                )}
              </table>
            </div>
          </div>
          <p className="small text-muted mt-2">
            Ingresos y ocupación cuentan las noches vendidas del mes (confirmadas, en curso o terminadas).
          </p>
        </>
      )}
    </div>
  );
}
