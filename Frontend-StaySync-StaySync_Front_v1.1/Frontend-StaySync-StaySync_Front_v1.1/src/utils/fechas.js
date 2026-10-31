/*
 * Fechas de reserva (LocalDate del backend, "YYYY-MM-DD").
 * new Date('2026-10-30') las lee como medianoche UTC, que en Chile es el 29 a las 21:00:
 * por eso se mostraban un día antes. Aquí se leen como día local.
 */
const ZONA_HOTEL = 'America/Santiago';

export function parseFecha(iso) {
  const [y, m, d] = String(iso).slice(0, 10).split('-').map(Number);
  return new Date(y, m - 1, d);
}

export const fmtFecha = (iso, opts) => (iso ? parseFecha(iso).toLocaleDateString('es-CL', opts) : '—');

/* "Hoy" del hotel, igual que el backend: desde las 21:00 en Chile toISOString() ya da mañana. */
export const hoyHotel = () => new Intl.DateTimeFormat('en-CA', { timeZone: ZONA_HOTEL }).format(new Date());

/* Noches entre dos fechas; Math.round absorbe los días de 23/25 h del cambio de horario. */
export const nochesEntre = (entrada, salida) =>
  Math.round((parseFecha(salida) - parseFecha(entrada)) / 86_400_000);

/* Días desde hoy (del hotel) hasta la fecha: 0 = hoy, negativo = ya pasó. */
export const diasHasta = (iso) => nochesEntre(hoyHotel(), iso);
