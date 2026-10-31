import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useMarca } from '../../context/ThemeContext';
import { getMisReservas } from '../../services/reservasService';
import { getDisponibles } from '../../services/habitacionesService';
import { getClima } from '../../services/dashboardService';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import WidgetClima from '../../components/common/WidgetClima';
import Foto from '../../components/common/Foto';
import Placa from '../../components/common/Placa';
import { fmtFecha, diasHasta, nochesEntre } from '../../utils/fechas';

const ESTADO_LABEL = {
  PENDIENTE:  'Falta pagar',
  CONFIRMADA: 'Confirmada',
  CHECKIN:    'En curso',
  CHECKOUT:   'Terminada',
  CANCELADA:  'Cancelada',
  NO_SHOW:    'No llegaste',
};

const clp = (v) => `$${Number(v ?? 0).toLocaleString('es-CL')}`;
const FECHA_CORTA = { day: 'numeric', month: 'short' };

function cuentaRegresiva(fechaEntrada) {
  const dias = diasHasta(fechaEntrada);
  if (dias <= 0) return 'Llegas hoy. El check-in es desde las 15:00.';
  if (dias === 1) return 'Llegas mañana. El check-in es desde las 15:00.';
  return `Faltan ${dias} días. El check-in es desde las 15:00.`;
}

export default function DashboardHuesped() {
  const { user } = useAuth();
  const { marca } = useMarca();
  const navigate = useNavigate();
  const [reservas, setReservas] = useState([]);
  const [loading,  setLoading]  = useState(true);
  const [clima,    setClima]    = useState(null);
  const [fotos,    setFotos]    = useState({}); // habitacionId → habitación con foto (las que están libres)

  useEffect(() => {
    getClima().then(setClima).catch(() => setClima(null));
    getDisponibles().then(l => setFotos(Object.fromEntries(l.map(h => [h.id, h])))).catch(() => setFotos({}));
  }, []);

  useEffect(() => {
    getMisReservas(user?.userId)
      .then(data => setReservas(Array.isArray(data) ? data : data.content ?? []))
      .catch(() => setReservas([]))
      .finally(() => setLoading(false));
  }, [user?.userId]);

  const activa   = reservas.find(r => r.estado === 'CHECKIN');
  const proxima  = reservas.filter(r => r.estado === 'CONFIRMADA')
    .sort((a, b) => a.fechaEntrada.localeCompare(b.fechaEntrada))[0];
  const sinPagar = reservas.filter(r => r.estado === 'PENDIENTE');
  const estadia  = activa ?? proxima;
  const numero   = (r) => r.habitacionNumero ?? fotos[r.habitacionId]?.numero;
  const nombre   = user?.nombreCompleto?.split(' ')[0];

  const pagar = (r) => navigate('/huesped/pago', {
    state: {
      reservaId: r.id, codigo: r.codigo, habitacionNumero: numero(r) ?? '',
      fechaEntrada: r.fechaEntrada, fechaSalida: r.fechaSalida, precioTotal: r.precioTotal,
    },
  });

  return (
    <div className="fade-in-up p-3 p-md-4">
      {/* Lo primero: su estadía (o la invitación a buscar una) */}
      <section className={`hero-estadia mb-4 ${estadia ? '' : 'hero-sin-estadia'}`} aria-label="Tu estadía">
        {estadia && (
          <Foto src={fotos[estadia.habitacionId]?.imagenPrincipal} alt="Tu habitación" numero={numero(estadia)} />
        )}
        <div className="p-4 p-lg-5 d-flex flex-column justify-content-center gap-2">
          <div className="d-flex justify-content-between align-items-start gap-3">
            <div>
              <h2 className="mb-1">Hola{nombre ? `, ${nombre}` : ''}</h2>
              <p className="suave mb-0">
                {estadia
                  ? (activa ? `Estás alojado en ${marca?.nombre ?? 'el hotel'}.` : `Tu próxima estadía en ${marca?.nombre ?? 'el hotel'}.`)
                  : `¿Cuándo vienes a ${marca?.nombre ?? 'vernos'}?`}
              </p>
            </div>
            <WidgetClima clima={clima} />
          </div>

          {estadia ? (
            <>
              <div className="cifra mt-3">
                {fmtFecha(estadia.fechaEntrada, FECHA_CORTA)} al {fmtFecha(estadia.fechaSalida, FECHA_CORTA)}
              </div>
              <p className="suave mb-3">
                {activa
                  ? `Tu salida es el ${fmtFecha(activa.fechaSalida, { weekday: 'long', day: 'numeric' })} antes de las 12:00.`
                  : cuentaRegresiva(estadia.fechaEntrada)}
                {' '}{nochesEntre(estadia.fechaEntrada, estadia.fechaSalida)} noches, {estadia.numHuespedes} {estadia.numHuespedes === 1 ? 'persona' : 'personas'}.
              </p>
              <div className="d-flex flex-wrap gap-2">
                {activa && <Link to="/huesped/servicios" className="btn btn-ss-gold">Pedir un servicio</Link>}
                <Link to="/huesped/reservas" className="btn btn-outline-light">Ver mis reservas</Link>
              </div>
            </>
          ) : (
            <div className="d-flex flex-wrap gap-2 mt-3">
              <Link to="/huesped/buscar" className="btn btn-ss-gold">Buscar habitaciones</Link>
              {reservas.length > 0 && <Link to="/huesped/reservas" className="btn btn-outline-light">Ver mis reservas</Link>}
            </div>
          )}
        </div>
      </section>

      {/* Reservas sin pagar: se liberan a las 24 h */}
      {sinPagar.map(r => (
        <div key={r.id} className="card shadow-sm aviso-pago mb-3">
          <div className="card-body d-flex flex-wrap align-items-center gap-3">
            {numero(r) && <Placa numero={numero(r)} />}
            <div className="flex-grow-1">
              <div className="fw-semibold">Tu reserva del {fmtFecha(r.fechaEntrada, FECHA_CORTA)} no está pagada</div>
              <small className="text-muted">Si no la pagas dentro de 24 horas desde que la hiciste, la habitación se libera.</small>
            </div>
            <button className="btn btn-ss-dark" onClick={() => pagar(r)}>Pagar {clp(r.precioTotal)}</button>
          </div>
        </div>
      ))}

      <div className="d-flex justify-content-between align-items-center mb-3 mt-4">
        <h5 className="mb-0">Tus reservas</h5>
        {reservas.length > 4 && <Link to="/huesped/reservas" className="small">Ver todas</Link>}
      </div>

      {loading && <LoadingSpinner message="Cargando tus reservas…" />}
      {!loading && reservas.length === 0 && (
        <p className="text-muted">Aún no tienes reservas. Cuando reserves, aquí verás tus fechas y tu cuenta.</p>
      )}
      {!loading && reservas.length > 0 && (
        <div className="row g-3">
          {reservas.slice(0, 4).map(r => (
            <div key={r.id} className="col-sm-6 col-xl-3">
              <div className="card shadow-sm h-100" style={{ overflow: 'hidden' }}>
                <Foto src={fotos[r.habitacionId]?.imagenPrincipal} alt="Habitación" numero={numero(r)} />
                <div className="card-body">
                  <div className="fw-semibold">{fmtFecha(r.fechaEntrada, FECHA_CORTA)} al {fmtFecha(r.fechaSalida, FECHA_CORTA)}</div>
                  <div className="d-flex justify-content-between small mt-1">
                    <span className="text-muted">{ESTADO_LABEL[r.estado] ?? r.estado}</span>
                    <span>{clp(r.precioTotal)}</span>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
