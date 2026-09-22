import apiClient from './apiClient';

export const getHabitaciones = () => apiClient.get('/habitaciones').then(r => r.data);

// params: { capacidad?: number, amenidad?: string, sort?: 'precio_asc'|'precio_desc' }
export const getDisponibles = (params = {}) =>
    apiClient.get('/habitaciones/disponibles', { params }).then(r => r.data);

export const getHabitacionById = (id)       => apiClient.get(`/habitaciones/${id}`).then(r => r.data);
export const cambiarEstado     = (id, body) => apiClient.patch(`/habitaciones/${id}/estado`, body).then(r => r.data);

// Gestión (ADMIN)
export const getTiposHabitacion = () => apiClient.get('/habitaciones/tipos').then(r => r.data);
export const getAmenidades      = () => apiClient.get('/habitaciones/amenidades').then(r => r.data);
export const crearHabitacion    = (body) => apiClient.post('/habitaciones', body).then(r => r.data);
export const desactivarHabitacion = (id) => apiClient.delete(`/habitaciones/${id}`).then(r => r.data);
