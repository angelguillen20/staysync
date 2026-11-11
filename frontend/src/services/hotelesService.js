import apiClient from './apiClient';

// Pública: nombre, logo y colores de un hotel por su slug (para la pantalla de login)
export const getMarcaPublica = (slug) =>
  apiClient.get(`/public/hoteles/${encodeURIComponent(slug)}/theme`).then((r) => r.data);

// Pública: hoteles de la cadena (nombre, slug, logo y colores) para el selector de portales
export const getHotelesPublicos = () => apiClient.get('/public/hoteles').then((r) => r.data);

// Solo SUPER_ADMIN. hotel: { nombre, slug, logoUrl?, theme?, admin: { nombre, apellido, email, password } }
export const crearHotel = (hotel) => apiClient.post('/hoteles', hotel).then((r) => r.data);

// ADMIN (su hotel) o SUPER_ADMIN. marca: { nombre, logoUrl, theme: { primary, secondary, background, text } }
export const actualizarMarca = (tenantId, marca) =>
  apiClient.put(`/hoteles/${encodeURIComponent(tenantId)}/marca`, marca).then((r) => r.data);
