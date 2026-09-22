import apiClient from './apiClient';

export const getServicios        = ()         => apiClient.get('/servicios/disponibles').then(r => r.data);
export const getTareas           = ()         => apiClient.get('/servicios/solicitudes/todas').then(r => r.data);
export const getSolicitudes      = (userId)   => {
  if (!userId || userId === 'undefined') return Promise.resolve([]);
  return apiClient.get(`/servicios/solicitudes?usuarioId=${userId}`).then(r => r.data);
};
export const solicitarServicio   = (body)     => apiClient.post('/servicios/solicitudes', body).then(r => r.data);
export const actualizarSolicitud = (id, body) => apiClient.patch(`/servicios/solicitudes/${id}/estado`, body).then(r => r.data);
