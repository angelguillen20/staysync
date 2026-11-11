import { useEffect, useMemo, useState } from 'react';
import { getHabitaciones } from '../../services/habitacionesService';
import { getReservasRango } from '../../services/reservasService';
import AlertMessage from '../../components/common/AlertMessage';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import { fmtFecha, hoyHotel, listaDias, nochesEntre, parseFecha, sumarDias } from '../../utils/fechas';
import { metricasPeriodo, nochesEnRango, VENDIDAS } from '../../utils/ocupacion';

const clp = (v) => `$${Math.round(v).toLocaleString('es-CL')}`;
const pct = (v) => `${Math.round(v * 100)}%`;

/* Primer día del mes "YYYY-MM" y del mes siguiente */
const limitesMes = (mes) => {
  const desde = `${mes}-01`;
  const d = parseFecha(desde);
  d.setMonth(d.getMonth() + 1);
  const hasta = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`;
  return [desde, hasta];
};

function descargarCsv(nombre, filas) {
  const csv = filas.map(f => f.map(v => `"${String(v ?? '').replaceAll('"', '""')}"`).join(';')).join('\r\n');
  // BOM para que Excel lea bien los acentos
  const url = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }));
  const a = Object.assign(document.createElement('a'), { href: url, download: nombre });
  a.click();
  URL.revokeObjectURL(url);
}

function Indicador({ valor, nombre, detalle }) {
  return (
    <div className="col-6 col-lg-3">
      <div className="card shadow-sm h-100">
        <div className="card-body">
          <div className="cifra">{valor}</div>
          <div className="fw-medium mt-2">{nombre}</div>
          <small className="text-muted">{detalle}</small>
        </div>
      </div>
    </div>
  );
}

/* Ocupación por día: una sola serie, barras finas ancladas a la base, el detalle al pasar o enfocar */
function GraficoOcupacion({ porDia, numHabitaciones }) {
  const [activo, setActivo] = useState(null);
  const dia = activo != null ? porDia[activo] : null;
  return (
    <div>
      <div className="d-flex justify-content-between align-items-baseline mb-2">
        <h5 className="mb-0">Ocupación por día</h5>
        <small className="text-muted" aria-live="polite">
          {dia ? `${fmtFecha(dia.dia, { weekday: 'long', day: 'numeric' })}: ${dia.ocupadas} de ${numHabitaciones} habitaciones (${pct(dia.ocupacion)})` : 'Pasa sobre una barra para ver el día'}
        </small>
      </div>
      <div className="d-flex align-items-end" style={{ height: 160, gap: 2, borderBottom: '1px solid var(--ss-line)' }}
           role="img" aria-label="Ocupación diaria del mes; el detalle está en la tabla">
        {porDia.map((d, i) => (
          <div key={d.dia} className="flex-grow-1 h-100 d-flex align-items-end"
               onMouseEnter={() => setActivo(i)} onMouseLeave={() => setActivo(null)}>
            <div style={{
              width: '100%', height: `${Math.max(d.ocupacion * 100, d.ocupadas ? 2 : 0)}%`,
              background: activo === i ? 'var(--ss-gold)' : 'var(--ss-dark)',
              borderRadius: '4px 4px 0 0',
            }} />
          </div>
        ))}
      </div>
      <div className="d-flex justify-content-between small text-muted mt-1">
        <span>{fmtFecha(porDia[0]?.dia, { day: 'numeric', month: 'short' })}</span>
        <span>{fmtFecha(porDia[porDia.length - 1]?.dia, { day: 'numeric', month: 'short' })}</span>
      </div>
      <details className="mt-3">
        <summary className="small">Ver como tabla</summary>
        <table className="table table-sm mt-2 mb-0">
          <thead><tr><th>Día</th><th>Habitaciones ocupadas</th><th>Ocupación</th></tr></thead>
          <tbody>
            {porDia.map(d => (
              <tr key={d.dia}><td>{fmtFecha(d.dia)}</td><td>{d.ocupadas} de {numHabitaciones}</td><td>{pct(d.ocupacion)}</td></tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  );
}

export default function ReportesRecepcion() {
  const [mes, setMes] = useState(hoyHotel().slice(0, 7));
  const [habitaciones, setHabitaciones] = useState([]);
  const [reservas, setReservas] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [desde, hasta] = limitesMes(mes);
  const dias = useMemo(() => listaDias(desde, nochesEntre(desde, hasta)), [desde, hasta]);

  useEffect(() => {
    setLoading(true); setError('');
    Promise.all([getHabitaciones(), getReservasRango(desde, hasta)])
      .then(([habs, res]) => { setHabitaciones(habs); setReservas(res); })
      .catch(err => setError(err.response?.data?.message ?? 'No se pudo cargar el reporte.'))
      .finally(() => setLoading(false));
  }, [desde, hasta]);

  const m = useMemo(() => metricasPeriodo(habitaciones.length, reservas, desde, hasta, dias),
    [habitaciones.length, reservas, desde, hasta, dias]);
  const numero = (id) => habitaciones.find(h => h.id === id)?.numero ?? id;
  const sinPagar = reservas.filter(r => r.estado === 'PENDIENTE').length;

  const exportar = () => descargarCsv(`reservas-${mes}.csv`, [
    ['Código', 'Huésped', 'Habitación', 'Entrada', 'Salida', 'Noches en el mes', 'Estado', 'Total reserva'],
    ...reservas.map(r => [r.codigo, r.huespedNombre, numero(r.habitacionId), r.fechaEntrada, r.fechaSalida,
      nochesEnRango(r, desde, hasta), r.estado, r.precioTotal]),
  ]);

  return (
    <div className="fade-in-up p-3 p-md-4">
      <div className="d-flex flex-wrap justify-content-between align-items-end gap-3 mb-4">
        <div>
          <h2 className="mb-1">Reportes</h2>
          <p className="text-muted mb-0">Noches vendidas: reservas confirmadas, en curso o terminadas. Las sin pagar no cuentan.</p>
        </div>
        <div className="d-flex gap-2 align-items-center">
          <label htmlFor="rep-mes" className="visually-hidden">Mes</label>
          <input id="rep-mes" type="month" className="form-control w-auto" value={mes} max={sumarDias(hoyHotel(), 400).slice(0, 7)}
                 onChange={e => e.target.value && setMes(e.target.value)} />
          <button className="btn btn-outline-secondary" onClick={exportar} disabled={loading || reservas.length === 0}>
            <i className="bi bi-download me-1" aria-hidden="true" />Exportar CSV
          </button>
        </div>
      </div>

      <AlertMessage message={error} onClose={() => setError('')} />
      {loading && <LoadingSpinner message="Calculando…" />}

      {!loading && !error && (
        <>
          <div className="row g-3 mb-4">
            <Indicador valor={pct(m.ocupacion)} nombre="Ocupación" detalle={`${m.noches} de ${habitaciones.length * dias.length} noches posibles`} />
            <Indicador valor={clp(m.ingresos)} nombre="Ingresos por estadías" detalle="Tarifa de las noches de este mes" />
            <Indicador valor={clp(m.adr)} nombre="Tarifa promedio" detalle="Por noche vendida" />
            <Indicador valor={clp(m.revpar)} nombre="Ingreso por habitación" detalle="Por habitación y noche disponible" />
          </div>

          <div className="card shadow-sm mb-3">
            <div className="card-body">
              {habitaciones.length === 0
                ? <p className="text-muted mb-0">Agrega habitaciones para calcular la ocupación.</p>
                : <GraficoOcupacion porDia={m.porDia} numHabitaciones={habitaciones.length} />}
            </div>
          </div>

          <p className="small text-muted">
            {reservas.filter(r => VENDIDAS.includes(r.estado)).length} reservas vendidas este mes
            {sinPagar > 0 && `, y ${sinPagar} sin pagar que no se cuentan`}. Los consumos de servicios se ven en la cuenta de cada reserva.
          </p>
        </>
      )}
    </div>
  );
}
