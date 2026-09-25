import apiClient from './apiClient';
import { isDemoUser, validateDemoPassword } from './authService';

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

// Solo ADMIN. rol: 'ADMIN' | 'RECEPCIONISTA' | 'HUESPED'. Se aplica en el siguiente login del usuario.
export const cambiarRolUsuario = (id, rol) =>
  apiClient.patch(`/usuarios/${id}/rol`, { rol }).then(r => r.data);

export const desactivarUsuarioAdmin = (id) =>
  apiClient.delete(`/usuarios/${id}`).then(r => r.data);

// Perfil propio — usa el JWT para identificar al usuario (accesible para cualquier rol)
export const getPerfilPropio = () => apiClient.get('/usuarios/perfil').then(r => r.data);

export async function updatePerfilPropio(email, passwordActual, campos) {
  if (isDemoUser(email)) {
    if (!validateDemoPassword(email, passwordActual)) {
      const err = new Error('Contraseña incorrecta.');
      err.response = { data: { message: 'Contraseña incorrecta.' } };
      throw err;
    }
    return campos;
  }
  return apiClient.put('/usuarios/perfil', campos).then(r => r.data);
}

export async function updatePerfil(id, { email, passwordActual, ...campos }) {
  if (isDemoUser(email)) {
    if (!validateDemoPassword(email, passwordActual)) {
      const err = new Error('Contraseña incorrecta.');
      err.response = { data: { message: 'Contraseña incorrecta.' } };
      throw err;
    }
    return campos;
  }
  return apiClient.put(`/usuarios/${id}`, { ...campos, passwordActual }).then(r => r.data);
}
