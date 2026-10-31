import apiClient from './apiClient';

// params.hotel (solo SUPER_ADMIN): 'todos' o el tenantId de otro hotel
export const getReservas          = (params)   => apiClient.get('/reservas', { params }).then(r => r.data);
export const getReservasHoy       = ()         => apiClient.get('/reservas/hoy').then(r => r.data);
export const getMisReservas       = (userId)   => apiClient.get(`/reservas/usuario/${userId}?page=0&size=20`).then(r => r.data);
export const getReservaDetalle    = (id)       => apiClient.get(`/reservas/${id}/detalle`).then(r => r.data);
export const crearReserva         = (body)     => apiClient.post('/reservas', body).then(r => r.data);
export const cambiarEstadoReserva = (id, body) => apiClient.patch(`/reservas/${id}/estado`, body).then(r => r.data);
export const cancelarReserva      = (id)       => apiClient.patch(`/reservas/${id}/cancelar`).then(r => r.data);
export const modificarReserva     = (id, body) => apiClient.patch(`/reservas/${id}/modificar`, body).then(r => r.data);
export const getReservasRango     = (desde, hasta, hotel) => apiClient.get('/reservas/rango', { params: { desde, hasta, hotel } }).then(r => r.data);
export const getCuenta            = (id)       => apiClient.get(`/reservas/${id}/cuenta`).then(r => r.data);
export const pagarSaldo           = (id, metodoPago) => apiClient.post(`/reservas/${id}/cuenta/pago`, { metodoPago }).then(r => r.data);
