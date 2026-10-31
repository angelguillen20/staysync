import apiClient from './apiClient';

/*
 * Sube una foto (JPG, PNG o WebP, hasta 5 MB) y devuelve su URL absoluta. Absoluta para que
 * pase la misma validación https que un enlace pegado (logo del hotel, correos).
 * carpeta: 'hoteles' | 'habitaciones' | 'servicios'
 */
export async function subirFoto(archivo, carpeta) {
  const datos = new FormData();
  datos.append('archivo', archivo);
  datos.append('carpeta', carpeta);
  // Sin este header axios convierte el FormData a JSON (el cliente manda application/json por defecto)
  const { data } = await apiClient.post('/media', datos, { headers: { 'Content-Type': 'multipart/form-data' } });
  return new URL(data.url, window.location.origin).href;
}
