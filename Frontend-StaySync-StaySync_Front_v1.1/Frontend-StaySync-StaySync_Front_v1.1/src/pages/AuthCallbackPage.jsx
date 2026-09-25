import { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { loginWithGoogle } from '../services/authService';

const REDIRECT = { ADMIN: '/recepcion', RECEPCIONISTA: '/recepcion', HUESPED: '/huesped' };

export default function AuthCallbackPage() {
  const [error, setError] = useState('');
  const { login } = useAuth();
  const navigate = useNavigate();
  const yaProcesado = useRef(false); // evita doble intercambio del code en StrictMode/re-render

  useEffect(() => {
    if (yaProcesado.current) return;
    yaProcesado.current = true;

    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const errorParam = params.get('error');
    const verifier = sessionStorage.getItem('ss_pkce_verifier');
    const redirectUri = sessionStorage.getItem('ss_oauth_redirect_uri');

    if (errorParam) {
      setError('Google canceló o rechazó el inicio de sesión.');
      return;
    }
    if (!code || !verifier || !redirectUri) {
      setError('Falta información para completar el inicio de sesión. Intenta nuevamente.');
      return;
    }

    loginWithGoogle(code, verifier, redirectUri)
      .then((data) => {
        sessionStorage.removeItem('ss_pkce_verifier');
        sessionStorage.removeItem('ss_oauth_redirect_uri');
        login(data);
        navigate(REDIRECT[data.rol] ?? '/huesped', { replace: true });
      })
      .catch((err) => {
        setError(err.response?.data?.message ?? 'No se pudo iniciar sesión con Google.');
      });
  }, [login, navigate]);

  return (
    <div className="d-flex align-items-center justify-content-center min-vh-100" style={{ background: 'var(--ss-cream)' }}>
      <div className="text-center px-4">
        {error ? (
          <>
            <i className="bi bi-exclamation-circle fs-1 d-block mb-3" style={{ color: 'var(--ss-danger)' }} />
            <p className="fw-semibold" style={{ color: 'var(--ss-dark)' }}>{error}</p>
            <button className="btn btn-ss-dark mt-2" onClick={() => navigate('/login')}>
              Volver al login
            </button>
          </>
        ) : (
          <>
            <div className="spinner-border" style={{ color: 'var(--ss-gold)', width: '3rem', height: '3rem' }} role="status" />
            <p className="mt-3 text-muted">Completando inicio de sesión con Google…</p>
          </>
        )}
      </div>
    </div>
  );
}
