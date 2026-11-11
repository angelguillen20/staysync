import { describe, it, expect, vi, afterEach } from 'vitest';
import { parseFecha, fmtFecha, hoyHotel, nochesEntre, diasHasta, sumarDias, listaDias } from '../utils/fechas';

describe('fechas de reserva', () => {
  afterEach(() => vi.useRealTimers());

  it('lee "YYYY-MM-DD" como día local, no como UTC', () => {
    const d = parseFecha('2026-10-30');
    expect([d.getFullYear(), d.getMonth(), d.getDate()]).toEqual([2026, 9, 30]);
    expect(fmtFecha('2026-10-30')).toBe('30-10-2026');
  });

  it('muestra un guion si no hay fecha', () => {
    expect(fmtFecha(null)).toBe('—');
  });

  it('cuenta noches, también cruzando el cambio de horario', () => {
    expect(nochesEntre('2026-10-30', '2026-11-02')).toBe(3);
    expect(nochesEntre('2026-09-05', '2026-09-07')).toBe(2);
  });

  it('suma días cruzando fin de mes, de año y el cambio de horario', () => {
    expect(sumarDias('2026-10-30', 3)).toBe('2026-11-02');
    expect(sumarDias('2026-12-31', 1)).toBe('2027-01-01');
    expect(sumarDias('2026-09-05', 2)).toBe('2026-09-07');
    expect(listaDias('2026-02-27', 3)).toEqual(['2026-02-27', '2026-02-28', '2026-03-01']);
  });

  it('"hoy" es el día del hotel aunque en UTC ya sea mañana', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-31T01:30:00Z')); // 30/10 22:30 en Santiago
    expect(hoyHotel()).toBe('2026-10-30');
    expect(diasHasta('2026-10-30')).toBe(0);
    expect(diasHasta('2026-10-29')).toBe(-1);
  });
});
