import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { logoutServer } from '../../services/authService';

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

  const handleLogout = async () => {
    try {
      const rt = localStorage.getItem('ss_refresh_token');
      if (rt) await logoutServer(rt);
    } catch { /* ignore */ }
    finally {
      logout();
      navigate('/login', { replace: true });
    }
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
        <div className="d-flex align-items-center gap-3">
          <span className="text-white-50 d-none d-sm-inline small">
            <i className="bi bi-person-circle me-1" />
            {user.nombreCompleto}
          </span>
          <button
            className="btn btn-sm btn-ss-gold"
            onClick={handleLogout}
          >
            <i className="bi bi-box-arrow-right me-1" />
            Salir
          </button>
        </div>
      )}
    </nav>
  );
}
