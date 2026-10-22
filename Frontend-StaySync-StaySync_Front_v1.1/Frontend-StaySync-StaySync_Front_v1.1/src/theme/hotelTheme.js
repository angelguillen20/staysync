/**
 * Marca blanca: cada hotel define sus colores y el portal los aplica como variables CSS.
 * Toda la app usa var(--ss-*), así que basta con sobrescribir esas variables en :root.
 *
 *   primary    → --ss-dark   (cabeceras, botones principales, barra lateral)
 *   secondary  → --ss-gold   (acentos)
 *   background → --ss-cream  (fondos suaves)
 *   text       → --ss-text   (texto principal)
 */

const KEYS = { marca: 'ss_hotel_marca', slug: 'ss_hotel_slug' };

const HEX = /^#[0-9a-fA-F]{6}$/;

/** Solo #RRGGBB: un valor así no puede inyectar otras reglas CSS ni url(...). */
export function esColorValido(color) {
  return typeof color === 'string' && HEX.test(color);
}

/** Solo https: no se aceptan javascript:, data: ni http. */
export function esLogoValido(url) {
  return typeof url === 'string' && /^https:\/\/[^\s"'<>()]+$/.test(url);
}

/** Mezcla un color con blanco (cantidad > 0) o negro (cantidad < 0). */
export function ajustarColor(hex, cantidad) {
  const n = parseInt(hex.slice(1), 16);
  const destino = cantidad > 0 ? 255 : 0;
  const t = Math.abs(cantidad);
  const canal = (v) => Math.round(v + (destino - v) * t);
  const r = canal((n >> 16) & 255);
  const g = canal((n >> 8) & 255);
  const b = canal(n & 255);
  return `#${((1 << 24) | (r << 16) | (g << 8) | b).toString(16).slice(1)}`;
}

function luminancia(hex) {
  const n = parseInt(hex.slice(1), 16);
  const lineal = (v) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * lineal((n >> 16) & 255) + 0.7152 * lineal((n >> 8) & 255) + 0.0722 * lineal(n & 255);
}

/** Contraste WCAG entre dos colores #RRGGBB: 1 (iguales) a 21 (negro sobre blanco). */
export function contraste(a, b) {
  const [claro, oscuro] = [luminancia(a), luminancia(b)].sort((x, y) => y - x);
  return (claro + 0.05) / (oscuro + 0.05);
}

/**
 * Combinaciones del theme que el portal usa y que no se leerían bien.
 * Umbrales WCAG AA: 4.5 para texto normal, 3 para texto grande e íconos.
 */
export function problemasDeContraste(tema) {
  const t = tema ?? {};
  const validos = ['primary', 'secondary', 'background', 'text'].every((k) => esColorValido(t[k]));
  if (!validos) return [];
  const problemas = [];
  if (contraste(t.primary, '#ffffff') < 4.5) {
    problemas.push('El texto blanco de la barra y los botones no se lee sobre el color principal. Usa un principal más oscuro.');
  }
  if (contraste(t.secondary, t.primary) < 3) {
    problemas.push('El nombre del hotel y el menú activo (acento) no se leen sobre el color principal. Usa un acento más claro.');
  }
  if (contraste(t.text, t.background) < 4.5) {
    problemas.push('El texto no se lee sobre el fondo. Aumenta la diferencia entre ambos colores.');
  }
  return problemas;
}

/** Variables CSS que corresponden a un theme; ignora los colores inválidos. */
export function variablesDeTema(tema) {
  const theme = tema ?? {};
  const vars = {};
  if (esColorValido(theme.primary)) {
    vars['--ss-dark'] = theme.primary;
    vars['--ss-dark-hover'] = ajustarColor(theme.primary, 0.15);
  }
  if (esColorValido(theme.secondary)) {
    vars['--ss-gold'] = theme.secondary;
    vars['--ss-warning'] = theme.secondary;
    vars['--ss-gold-light'] = ajustarColor(theme.secondary, 0.3);
    vars['--ss-gold-dark'] = ajustarColor(theme.secondary, -0.12);
  }
  if (esColorValido(theme.background)) vars['--ss-cream'] = theme.background;
  if (esColorValido(theme.text)) vars['--ss-text'] = theme.text;
  return vars;
}

const VARIABLES = ['--ss-dark', '--ss-dark-hover', '--ss-gold', '--ss-warning', '--ss-gold-light',
  '--ss-gold-dark', '--ss-cream', '--ss-text'];

/** Aplica el theme sobre los colores por defecto de StaySync (global.css). */
export function aplicarTema(theme) {
  const raiz = document.documentElement.style;
  VARIABLES.forEach((v) => raiz.removeProperty(v));
  Object.entries(variablesDeTema(theme)).forEach(([v, color]) => raiz.setProperty(v, color));
}

function leer(clave) {
  try {
    return localStorage.getItem(clave);
  } catch {
    return null;
  }
}

function escribir(clave, valor) {
  try {
    if (valor == null) localStorage.removeItem(clave);
    else localStorage.setItem(clave, valor);
  } catch {
    // almacenamiento no disponible: el tema solo dura esta sesión
  }
}

/** Marca guardada { tenantId, nombre, slug, logoUrl, theme }, para no parpadear al recargar. */
export function marcaGuardada() {
  try {
    const raw = leer(KEYS.marca);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function guardarMarca(marca) {
  escribir(KEYS.marca, marca ? JSON.stringify(marca) : null);
  if (marca?.slug) escribir(KEYS.slug, marca.slug);
}

/** Slug del portal del hotel (de ?hotel=... o de la última marca usada). */
export function slugGuardado() {
  return leer(KEYS.slug);
}

export function guardarSlug(slug) {
  escribir(KEYS.slug, slug);
}

const SLUG = /^[a-z0-9][a-z0-9-]{1,63}$/;

export function esSlugValido(slug) {
  return typeof slug === 'string' && SLUG.test(slug);
}
