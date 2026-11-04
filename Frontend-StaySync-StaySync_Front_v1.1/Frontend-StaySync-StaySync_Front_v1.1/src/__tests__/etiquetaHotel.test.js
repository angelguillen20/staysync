import { describe, it, expect } from 'vitest';
import { asignarColores, COLORES_HOTEL } from '../components/common/EtiquetaHotel';

describe('color de cada hotel', () => {
  it('cada hotel tiene un color distinto de la paleta validada', () => {
    const c = asignarColores(['hotel-andes', 'staysync-demo', 'hotel-sur']);
    expect(new Set(Object.values(c)).size).toBe(3);
    Object.values(c).forEach(color => expect(COLORES_HOTEL).toContain(color));
  });

  it('no depende del orden de la lista (que va por nombre)', () => {
    expect(asignarColores(['staysync-demo', 'hotel-andes'])).toEqual(asignarColores(['hotel-andes', 'staysync-demo']));
  });

  it('crear un hotel nuevo no repinta a los que ya existían si no choca con ellos', () => {
    const antes = asignarColores(['hotel-andes', 'staysync-demo']);
    const despues = asignarColores(['hotel-andes', 'staysync-demo', 'zz-nuevo']);
    expect(despues['hotel-andes']).toBe(antes['hotel-andes']);
    expect(despues['staysync-demo']).toBe(antes['staysync-demo']);
  });

  it('con más hoteles que colores sigue asignando a todos', () => {
    const ids = Array.from({ length: 10 }, (_, i) => `hotel-${i}`);
    expect(Object.keys(asignarColores(ids))).toHaveLength(10);
  });
});
