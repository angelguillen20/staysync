/*
 * Cálculos de ocupación para el calendario y los reportes. Las fechas son "YYYY-MM-DD", que
 * se comparan bien como texto; una estadía ocupa las noches [fechaEntrada, fechaSalida).
 */
import { nochesEntre } from './fechas';

/* Canceladas y no-show liberaron sus noches: no ocupan el calendario. */
const NO_OCUPAN = ['CANCELADA', 'NO_SHOW'];
/* Noches vendidas: pagadas o en curso. Las pendientes aún pueden vencer sin pago. */
export const VENDIDAS = ['CONFIRMADA', 'CHECKIN', 'CHECKOUT'];

/*
 * Por habitación, las celdas de la grilla: un día libre o una estadía que abarca sus noches
 * visibles (span). Si dos estadías se pisan, la segunda empieza donde termina la primera.
 */
export function filasCalendario(habitaciones, reservas, dias) {
  const indice = new Map(dias.map((d, i) => [d, i]));
  const primero = dias[0];
  const ultimo = dias[dias.length - 1];

  return habitaciones.map((habitacion) => {
    const propias = reservas
      .filter(r => r.habitacionId === habitacion.id && !NO_OCUPAN.includes(r.estado)
        && r.fechaEntrada <= ultimo && r.fechaSalida > primero)
      .sort((a, b) => a.fechaEntrada.localeCompare(b.fechaEntrada));

    const celdas = [];
    let i = 0;
    for (const reserva of propias) {
      const desde = Math.max(reserva.fechaEntrada < primero ? 0 : indice.get(reserva.fechaEntrada), i);
      const hasta = reserva.fechaSalida > ultimo ? dias.length : indice.get(reserva.fechaSalida);
      if (hasta <= desde) continue;
      for (; i < desde; i++) celdas.push({ tipo: 'libre', dia: dias[i] });
      celdas.push({ tipo: 'estadia', reserva, span: hasta - desde, dia: dias[desde], continua: reserva.fechaEntrada < primero });
      i = hasta;
    }
    for (; i < dias.length; i++) celdas.push({ tipo: 'libre', dia: dias[i] });
    return { habitacion, celdas };
  });
}

/* Noches de la reserva que caen en [desde, hasta). */
export function nochesEnRango(reserva, desde, hasta) {
  const inicio = reserva.fechaEntrada > desde ? reserva.fechaEntrada : desde;
  const fin = reserva.fechaSalida < hasta ? reserva.fechaSalida : hasta;
  return Math.max(0, nochesEntre(inicio, fin));
}

/*
 * Indicadores del período [desde, hasta) con las noches vendidas:
 *   ocupación = noches vendidas / (habitaciones × días)
 *   ingresos  = tarifa por noche de cada reserva × sus noches en el período
 *   tarifa promedio (ADR) = ingresos / noches vendidas
 *   ingreso por habitación disponible (RevPAR) = ingresos / (habitaciones × días)
 * porDia: ocupación de cada día, para el gráfico.
 */
export function metricasPeriodo(numHabitaciones, reservas, desde, hasta, dias) {
  const vendidas = reservas.filter(r => VENDIDAS.includes(r.estado));
  let noches = 0;
  let ingresos = 0;
  for (const r of vendidas) {
    const n = nochesEnRango(r, desde, hasta);
    const tarifa = Number(r.precioTotal ?? 0) / Math.max(1, nochesEntre(r.fechaEntrada, r.fechaSalida));
    noches += n;
    ingresos += tarifa * n;
  }
  const capacidad = numHabitaciones * dias.length;
  const porDia = dias.map((dia) => {
    const ocupadas = vendidas.filter(r => r.fechaEntrada <= dia && r.fechaSalida > dia).length;
    return { dia, ocupadas, ocupacion: numHabitaciones ? ocupadas / numHabitaciones : 0 };
  });
  return {
    noches,
    ingresos: Math.round(ingresos),
    ocupacion: capacidad ? noches / capacidad : 0,
    adr: noches ? Math.round(ingresos / noches) : 0,
    revpar: capacidad ? Math.round(ingresos / capacidad) : 0,
    porDia,
  };
}

/*
 * Panel de plataforma: una fila por hotel con lo de hoy y el mes. habitaciones y reservas
 * vienen de la vista "todos" del BFF (cada fila trae hotelId).
 */
export function resumenPorHotel(hoteles, habitaciones, reservas, desde, hasta, dias, hoy) {
  return hoteles.map((hotel) => {
    const habs = habitaciones.filter(h => h.hotelId === hotel.tenantId);
    const res = reservas.filter(r => r.hotelId === hotel.tenantId);
    const mes = metricasPeriodo(habs.length, res, desde, hasta, dias);
    return {
      hotel,
      habitaciones: habs.length,
      ocupadasHoy: habs.filter(h => h.estado === 'OCUPADA').length,
      alojados: res.filter(r => r.estado === 'CHECKIN').length,
      llegadasHoy: res.filter(r => r.estado === 'CONFIRMADA' && r.fechaEntrada === hoy).length,
      sinPagar: res.filter(r => r.estado === 'PENDIENTE').length,
      ocupacion: mes.ocupacion,
      ingresos: mes.ingresos,
    };
  });
}
