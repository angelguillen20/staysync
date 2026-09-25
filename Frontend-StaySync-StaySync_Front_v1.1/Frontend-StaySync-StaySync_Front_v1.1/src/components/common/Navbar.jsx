import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { logoutServer } from '../../services/authService';
import { cognitoLogoutUrl } from '../../config/cognito';

const PORTAL_LABELS = {
  ADMIN:         'Administración',
  RECEPCIONISTA: 'Recepción',
  HUESPED:       'Portal Huésped',
};

const HOME_ROUTE = {
  ADMIN:         '/recepcion',
  RECEPCIONISTA: '/recepcion',
  HUESPED:       '/huesped',
};

export default function Navbar({ onToggleSidebar }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const cerrarSesionLocal = async () => {
    try {
      const rt = localStorage.getItem('ss_refresh_token');
      if (rt) await logoutServer(rt);
    } catch { /* ignore */ }
    finally {
      logout();
    }
  };

  // Salir: cierra la sesión de StaySync. "Continuar con Google" vuelve a entrar con la misma cuenta.
  const handleSalir = async () => {
    await cerrarSesionLocal();
    navigate('/login', { replace: true });
  };

  // Cerrar sesión: además borra la sesión de Cognito, para poder entrar con otra cuenta de Google.
  const handleCerrarSesion = async () => {
    await cerrarSesionLocal();
    window.location.href = cognitoLogoutUrl();
  };

  return (
    <nav className="navbar navbar-staysync fixed-top px-3">
      <div className="d-flex align-items-center gap-3">
        <button
          className="btn btn-sm btn-outline-warning d-md-none"
          onClick={onToggleSidebar}
          aria-label="Toggle sidebar"
        >
          <i className="bi bi-list fs-5" />
        </button>

        <Link to={HOME_ROUTE[user?.rol] ?? '/'} className="navbar-brand mb-0">
          <i className="bi bi-building me-2" />
          StaySync
        </Link>

        {user && (
          <span className="badge rounded-pill text-bg-warning text-dark fw-normal">
            {PORTAL_LABELS[user.rol] ?? user.rol}
          </span>
        )}
      </div>

      {user && (
        <div className="d-flex align-items-center gap-2">
          <span className="text-white-50 d-none d-sm-inline small me-1">
            <i className="bi bi-person-circle me-1" />
            {user.nombreCompleto}
          </span>
          <button
            className="btn btn-sm btn-ss-gold"
            onClick={handleSalir}
            title="Salir de StaySync (al volver con Google entrarás con la misma cuenta)"
          >
            <i className="bi bi-box-arrow-right" />
            <span className="d-none d-sm-inline ms-1">Salir</span>
          </button>
          <button
            className="btn btn-sm btn-outline-warning"
            onClick={handleCerrarSesion}
            title="Cerrar sesión por completo para ingresar con otra cuenta de Google"
          >
            <i className="bi bi-person-x" />
            <span className="d-none d-sm-inline ms-1">Cerrar sesión</span>
          </button>
        </div>
      )}
    </nav>
  );
}
