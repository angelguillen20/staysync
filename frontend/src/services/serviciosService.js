import apiClient from './apiClient';

export const getServicios        = ()         => apiClient.get('/servicios/disponibles').then(r => r.data);
export const getTareas           = ()         => apiClient.get('/servicios/solicitudes/todas').then(r => r.data);
export const getSolicitudes      = (userId)   => {
  if (!userId || userId === 'undefined') return Promise.resolve([]);
  return apiClient.get(`/servicios/solicitudes?usuarioId=${userId}`).then(r => r.data);
};
export const solicitarServicio   = (body)     => apiClient.post('/servicios/solicitudes', body).then(r => r.data);
export const actualizarSolicitud = (id, body) => apiClient.patch(`/servicios/solicitudes/${id}/estado`, body).then(r => r.data);

// Tareas de aseo: las genera el sistema con los eventos de reserva
export const getAseo       = ()   => apiClient.get('/servicios/aseo').then(r => r.data);
export const completarAseo = (id) => apiClient.patch(`/servicios/aseo/${id}/completar`).then(r => r.data);

// Catálogo del hotel (ADMIN)
export const getCatalogo        = ()         => apiClient.get('/servicios/catalogo').then(r => r.data);
export const getCategorias      = ()         => apiClient.get('/servicios/categorias').then(r => r.data);
export const crearServicio      = (body)     => apiClient.post('/servicios', body).then(r => r.data);
export const actualizarServicio = (id, body) => apiClient.put(`/servicios/${id}`, body).then(r => r.data);
