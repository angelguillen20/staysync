import apiClient from './apiClient';

/**
 * Authenticate a user — always via BFF (real JWT + refresh token in DB).
 * Demo user credentials (admin@, recep@, huesped@) must exist in the database.
 * hotelSlug = portal desde el que entra: un huésped queda en ese hotel (una cuenta para toda
 * la cadena); el personal siempre queda en el suyo.
 * @returns {Promise<AuthResponse>} Auth payload from BFF.
 */
export async function login(email, password, hotelSlug) {
  const { data } = await apiClient.post('/auth/login', { email, password, hotelSlug: hotelSlug || undefined });
  return data;
}

/**
 * Register a new guest. El registro público siempre crea huéspedes: el rol lo asigna
 * después un administrador. hotelSlug = portal del hotel desde el que se registra.
 * @returns {Promise<UsuarioResponse>} Created user from BFF.
 */
export async function register({ nombre, apellido, email, password, telefono, hotelSlug }) {
  const { data } = await apiClient.post('/auth/registro', {
    nombre, apellido, email, password, telefono, hotelSlug: hotelSlug || undefined,
  });
  return data;
}

/**
 * Refresh the access token using the stored refresh token.
 * @returns {Promise<AuthResponse>}
 */
export async function refreshToken(token) {
  const { data } = await apiClient.post('/auth/refresh', { refreshToken: token });
  return data;
}

/**
 * Invalidate the refresh token on the server (logout).
 */
export async function logoutServer(token) {
  await apiClient.post('/auth/logout', { refreshToken: token });
}

/**
 * Intercambia el code de Cognito (tras el login con Google) por un JWT propio.
 * El BFF hace el intercambio real contra Cognito y busca/crea el usuario.
 * @returns {Promise<AuthResponse>} Mismo formato que login().
 */
export async function loginWithGoogle(code, codeVerifier, redirectUri, hotelSlug) {
  const { data } = await apiClient.post('/auth/google', {
    code, codeVerifier, redirectUri, hotelSlug: hotelSlug || undefined,
  });
  return data;
}
