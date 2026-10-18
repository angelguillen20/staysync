import { describe, it, expect, beforeEach } from 'vitest';
import {
  aplicarTema, ajustarColor, esColorValido, esLogoValido, variablesDeTema,
} from '../theme/hotelTheme';

describe('marca blanca: validación', () => {
  it('acepta solo colores #RRGGBB', () => {
    expect(esColorValido('#1F4E79')).toBe(true);
    expect(esColorValido('red')).toBe(false);
    expect(esColorValido('#fff')).toBe(false);
    expect(esColorValido('#000000; background:url(x)')).toBe(false);
  });

  it('acepta solo logos https', () => {
    expect(esLogoValido('https://cdn.hotel.cl/logo.png')).toBe(true);
    expect(esLogoValido('http://cdn.hotel.cl/logo.png')).toBe(false);
    expect(esLogoValido('javascript:alert(1)')).toBe(false);
    expect(esLogoValido('https://x.cl/a.png" onerror="x')).toBe(false);
  });
});

describe('marca blanca: variables CSS', () => {
  beforeEach(() => document.documentElement.removeAttribute('style'));

  it('traduce el theme a las variables --ss-* e ignora colores inválidos', () => {
    const vars = variablesDeTema({ primary: '#7A2E1F', secondary: 'verde', background: '#FBF8F4' });
    expect(vars['--ss-dark']).toBe('#7A2E1F');
    expect(vars['--ss-cream']).toBe('#FBF8F4');
    expect(vars['--ss-gold']).toBeUndefined();
  });

  it('aplicarTema sobrescribe y luego restaura los colores por defecto', () => {
    aplicarTema({ primary: '#123456' });
    expect(document.documentElement.style.getPropertyValue('--ss-dark')).toBe('#123456');

    aplicarTema(null);
    expect(document.documentElement.style.getPropertyValue('--ss-dark')).toBe('');
  });

  it('ajustarColor aclara hacia blanco y oscurece hacia negro', () => {
    expect(ajustarColor('#000000', 1)).toBe('#ffffff');
    expect(ajustarColor('#ffffff', -1)).toBe('#000000');
    expect(ajustarColor('#808080', 0)).toBe('#808080');
  });
});
