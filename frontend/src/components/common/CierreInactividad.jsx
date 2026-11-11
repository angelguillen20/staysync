import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth, SESSION_TIMEOUT_MS, readLastActivity, touchActivity } from '../../context/AuthContext';
import { logoutServer } from '../../services/authService';

const AVISO_MS = 60 * 1000;       // se avisa 1 minuto antes de cerrar
const CHEQUEO_MS = 1000;
const ESCRITURA_MS = 15 * 1000;   // no escribir en localStorage en cada movimiento del mouse
const EVENTOS = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart'];

/**
 * Cierra la sesión tras SESSION_TIMEOUT_MS sin actividad. La última actividad vive en
 * localStorage, así que usar cualquier pestaña mantiene viva la sesión en todas.
 */
export default function CierreInactividad() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [restanteMs, setRestanteMs] = useState(null);

  const cerrarSesion = useCallback(async () => {
    try {
      const rt = localStorage.getItem('ss_refresh_token');
      if (rt) await logoutServer(rt);
    } catch { /* ignore */ }
    finally {
      logout();
      navigate('/login', { replace: true, state: { motivo: 'inactividad' } });
    }
  }, [logout, navigate]);

  useEffect(() => {
    touchActivity();
    let ultimaEscritura = Date.now();

    const onActividad = () => {
      if (Date.now() - ultimaEscritura > ESCRITURA_MS) {
        touchActivity();
        ultimaEscritura = Date.now();
      }
    };
    EVENTOS.forEach(ev => window.addEventListener(ev, onActividad, { passive: true }));

    const timer = setInterval(() => {
      const restante = SESSION_TIMEOUT_MS - (Date.now() - readLastActivity());
      if (restante <= 0) {
        clearInterval(timer);
        cerrarSesion();
      } else {
        setRestanteMs(restante <= AVISO_MS ? restante : null);
      }
    }, CHEQUEO_MS);

    return () => {
      EVENTOS.forEach(ev => window.removeEventListener(ev, onActividad));
      clearInterval(timer);
    };
  }, [cerrarSesion]);

  if (restanteMs === null) return null;

  return (
    <div
      style={{ position: 'fixed', inset: 0, zIndex: 1060, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1rem', background: 'rgba(34,32,22,0.45)' }}
    >
      <div className="card border-0 shadow" style={{ maxWidth: 400, borderRadius: 14 }}>
        <div className="card-body p-4 text-center">
          <i className="bi bi-hourglass-split fs-1" style={{ color: 'var(--ss-gold)' }} />
          <h5 className="fw-bold mt-2">¿Sigues ahí?</h5>
          <p className="text-muted mb-4">
            Por seguridad, tu sesión se cerrará en <strong>{Math.ceil(restanteMs / 1000)} s</strong> por inactividad.
          </p>
          <div className="d-flex gap-2 justify-content-center">
            <button className="btn btn-outline-secondary" onClick={cerrarSesion}>Cerrar sesión</button>
            <button className="btn btn-ss-dark fw-semibold" onClick={() => { touchActivity(); setRestanteMs(null); }}>
              Seguir conectado
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
