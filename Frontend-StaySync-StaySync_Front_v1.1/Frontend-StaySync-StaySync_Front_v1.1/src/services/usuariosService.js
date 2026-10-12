import apiClient from './apiClient';

// params: { page?: number, size?: number, sort?: string } — respuesta paginada (Page<Usuario>)
export const getUsuarios    = (params = {}) =>
  apiClient.get('/usuarios', { params: { page: 0, size: 10, sort: 'id', ...params } }).then(r => r.data);

export const getHuespedes   = ()        => apiClient.get('/usuarios/huespedes').then(r => r.data);
export const buscarUsuarios = (q, rol)  => apiClient.get('/usuarios/buscar', { params: { q: q ?? '', rol: rol ?? '' } }).then(r => r.data);
export const getPerfil      = (id)      => apiClient.get(`/usuarios/${id}`).then(r => r.data);

// Edición administrativa (ADMIN) de cualquier usuario — sin el gate de contraseña de auto-edición,
// ya que el backend protege /usuarios/{id} con @PreAuthorize("hasRole('ADMIN')") y no exige
// la contraseña del usuario editado. campos: { nombre?, apellido?, telefono?, nuevaPassword? }
export const updateUsuarioAdmin = (id, campos) =>
  apiClient.put(`/usuarios/${id}`, campos).then(r => r.data);

// ADMIN (su hotel) o SUPER_ADMIN. rol: 'SUPER_ADMIN' | 'ADMIN' | 'RECEPCIONISTA' | 'HUESPED'.
// Se aplica en el siguiente login del usuario (el backend cierra sus sesiones).
export const cambiarRolUsuario = (id, rol) =>
  apiClient.patch(`/usuarios/${id}/rol`, { rol }).then(r => r.data);

// Solo SUPER_ADMIN. Mueve una cuenta de personal a otro hotel; cierra sus sesiones.
export const cambiarHotelUsuario = (id, tenantId) =>
  apiClient.patch(`/usuarios/${id}/hotel`, { tenantId }).then(r => r.data);

// Solo SUPER_ADMIN. Hoteles activos para el selector: [{ tenantId, nombre, slug, ... }]
export const getHoteles = () => apiClient.get('/hoteles').then(r => r.data);

export const desactivarUsuarioAdmin = (id) =>
  apiClient.delete(`/usuarios/${id}`).then(r => r.data);

// Perfil propio — usa el JWT para identificar al usuario (accesible para cualquier rol)
export const getPerfilPropio = () => apiClient.get('/usuarios/perfil').then(r => r.data);

export async function updatePerfilPropio(campos) {
  return apiClient.put('/usuarios/perfil', campos).then(r => r.data);
}

export async function updatePerfil(id, { email, passwordActual, ...campos }) {
  return apiClient.put(`/usuarios/${id}`, { ...campos, passwordActual }).then(r => r.data);
}
