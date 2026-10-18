import apiClient from './apiClient';

// Pública: nombre, logo y colores de un hotel por su slug (para la pantalla de login)
export const getMarcaPublica = (slug) =>
  apiClient.get(`/public/hoteles/${encodeURIComponent(slug)}/theme`).then((r) => r.data);

// ADMIN (su hotel) o SUPER_ADMIN. marca: { nombre, logoUrl, theme: { primary, secondary, background, text } }
export const actualizarMarca = (tenantId, marca) =>
  apiClient.put(`/hoteles/${encodeURIComponent(tenantId)}/marca`, marca).then((r) => r.data);
