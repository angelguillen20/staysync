import { describe, it, expect } from 'vitest';
import { filasCalendario, metricasPeriodo, nochesEnRango } from '../utils/ocupacion';
import { listaDias } from '../utils/fechas';

const dias = listaDias('2026-11-01', 7); // 1 al 7 de noviembre
const hab = [{ id: 1, numero: '101' }, { id: 2, numero: '102' }];
const r = (id, habitacionId, fechaEntrada, fechaSalida, estado = 'CONFIRMADA', precioTotal = 0) =>
  ({ id, habitacionId, fechaEntrada, fechaSalida, estado, precioTotal });

describe('calendario de ocupación', () => {
  it('una estadía ocupa sus noches como una sola celda y el resto son días libres', () => {
    const [fila] = filasCalendario(hab, [r(1, 1, '2026-11-02', '2026-11-05')], dias);
    expect(fila.celdas.map(c => c.tipo === 'estadia' ? `E${c.span}` : 'L')).toEqual(['L', 'E3', 'L', 'L', 'L']);
  });

  it('recorta las estadías que empiezan antes o terminan después de la ventana', () => {
    const [fila] = filasCalendario(hab, [r(1, 1, '2026-10-28', '2026-11-02'), r(2, 1, '2026-11-06', '2026-11-12')], dias);
    expect(fila.celdas.map(c => c.tipo === 'estadia' ? `E${c.span}` : 'L')).toEqual(['E1', 'L', 'L', 'L', 'L', 'E2']);
    expect(fila.celdas[0].continua).toBe(true);
  });

  it('cancelada y no-show no ocupan; cada habitación ve solo lo suyo', () => {
    const filas = filasCalendario(hab, [r(1, 1, '2026-11-01', '2026-11-03', 'CANCELADA'), r(2, 2, '2026-11-01', '2026-11-02', 'NO_SHOW')], dias);
    expect(filas.every(f => f.celdas.length === 7 && f.celdas.every(c => c.tipo === 'libre'))).toBe(true);
  });

  it('suma siempre los días de la ventana aunque dos estadías se pisen', () => {
    const [fila] = filasCalendario(hab, [r(1, 1, '2026-11-01', '2026-11-04'), r(2, 1, '2026-11-03', '2026-11-06')], dias);
    expect(fila.celdas.reduce((n, c) => n + (c.span ?? 1), 0)).toBe(7);
  });
});

describe('indicadores del período', () => {
  it('cuenta solo las noches dentro del período', () => {
    expect(nochesEnRango(r(1, 1, '2026-10-30', '2026-11-03'), '2026-11-01', '2026-11-08')).toBe(2);
  });

  it('calcula ocupación, ingresos, tarifa promedio y RevPAR con las noches vendidas', () => {
    const reservas = [
      r(1, 1, '2026-11-01', '2026-11-03', 'CONFIRMADA', 200000), // 2 noches × 100.000
      r(2, 2, '2026-11-06', '2026-11-10', 'CHECKIN', 400000),    // 4 noches, 2 en el período
      r(3, 1, '2026-11-04', '2026-11-05', 'PENDIENTE', 999999),  // sin pagar: no cuenta
    ];
    const m = metricasPeriodo(2, reservas, '2026-11-01', '2026-11-08', dias);
    expect(m.noches).toBe(4);
    expect(m.ingresos).toBe(400000);
    expect(m.ocupacion).toBeCloseTo(4 / 14);
    expect(m.adr).toBe(100000);
    expect(m.revpar).toBe(Math.round(400000 / 14));
    expect(m.porDia.map(d => d.ocupadas)).toEqual([1, 1, 0, 0, 0, 1, 1]);
  });
});
