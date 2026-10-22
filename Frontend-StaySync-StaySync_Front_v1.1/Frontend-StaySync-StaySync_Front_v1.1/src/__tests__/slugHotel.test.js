import { describe, it, expect } from 'vitest';
import { slugDe } from '../pages/recepcion/HotelesAdmin';
import { esSlugValido } from '../theme/hotelTheme';

describe('alta de hotel: slug del portal', () => {
  it('quita tildes, espacios y símbolos', () => {
    expect(slugDe('Hotel Pacífico Sur')).toBe('hotel-pacifico-sur');
    expect(slugDe('  Ñandú & Cía. ')).toBe('nandu-cia');
  });

  it('el resultado es un slug válido para el backend', () => {
    expect(esSlugValido(slugDe('Hotel Viña del Mar 2026'))).toBe(true);
  });
});
