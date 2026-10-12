import { describe, it, expect } from 'vitest';
import { tieneRol } from '../context/AuthContext';

describe('tieneRol()', () => {
  it('acepta el rol exacto', () => {
    expect(tieneRol('RECEPCIONISTA', ['ADMIN', 'RECEPCIONISTA'])).toBe(true);
  });

  it('SUPER_ADMIN entra donde entra un ADMIN', () => {
    expect(tieneRol('SUPER_ADMIN', ['ADMIN'])).toBe(true);
    expect(tieneRol('SUPER_ADMIN', ['ADMIN', 'RECEPCIONISTA'])).toBe(true);
  });

  it('ADMIN no entra donde se exige SUPER_ADMIN', () => {
    expect(tieneRol('ADMIN', ['SUPER_ADMIN'])).toBe(false);
  });

  it('rechaza roles ajenos o vacíos', () => {
    expect(tieneRol('HUESPED', ['ADMIN'])).toBe(false);
    expect(tieneRol(undefined, ['ADMIN'])).toBe(false);
  });
});
