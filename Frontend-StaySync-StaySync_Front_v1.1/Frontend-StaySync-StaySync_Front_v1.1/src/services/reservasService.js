import apiClient from './apiClient';

export const getReservas          = ()         => apiClient.get('/reservas').then(r => r.data);
export const getReservasHoy       = ()         => apiClient.get('/reservas/hoy').then(r => r.data);
export const getMisReservas       = (userId)   => apiClient.get(`/reservas/usuario/${userId}?page=0&size=20`).then(r => r.data);
export const getReservaDetalle    = (id)       => apiClient.get(`/reservas/${id}/detalle`).then(r => r.data);
export const crearReserva         = (body)     => apiClient.post('/reservas', body).then(r => r.data);
export const cambiarEstadoReserva = (id, body) => apiClient.patch(`/reservas/${id}/estado`, body).then(r => r.data);
export const cancelarReserva      = (id)       => apiClient.patch(`/reservas/${id}/cancelar`).then(r => r.data);
