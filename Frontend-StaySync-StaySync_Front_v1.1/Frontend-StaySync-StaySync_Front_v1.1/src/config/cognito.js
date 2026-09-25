// Config de Cognito Hosted UI — sobrescribible por variables de entorno de Vite en build/deploy.
export const COGNITO_DOMAIN       = import.meta.env.VITE_COGNITO_DOMAIN       ?? 'https://staysync-348143777102.auth.us-east-1.amazoncognito.com';
export const COGNITO_CLIENT_ID    = import.meta.env.VITE_COGNITO_CLIENT_ID    ?? '45vvlk0o108jov40ijgllr5okv';
export const COGNITO_REDIRECT_URI = import.meta.env.VITE_COGNITO_REDIRECT_URI ?? `${window.location.origin}/auth/callback`;

/**
 * URL que borra la sesión de Cognito (la cookie que hace que "Continuar con Google" entre
 * directo con la última cuenta) y vuelve al login. El logout_uri tiene que estar en
 * "Allowed sign-out URLs" del App Client, o Cognito muestra un error.
 */
export const cognitoLogoutUrl = () => {
  const params = new URLSearchParams({
    client_id:  COGNITO_CLIENT_ID,
    logout_uri: `${window.location.origin}/login`,
  });
  return `${COGNITO_DOMAIN}/logout?${params.toString()}`;
};
