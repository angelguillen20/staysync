import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const REDIRECT = { ADMIN: '/recepcion', RECEPCIONISTA: '/recepcion', HUESPED: '/huesped' };

export default function UnauthorizedPage() {
  const { user } = useAuth();
  return (
    <div className="d-flex flex-column align-items-center justify-content-center min-vh-100 gap-4 text-center px-3"
         style={{ background: 'var(--ss-cream)' }}>
      <span style={{ fontSize: '5rem' }}>🔒</span>
      <h1 className="fw-bold" style={{ color: 'var(--ss-dark)' }}>Acceso restringido</h1>
      <p className="text-muted" style={{ maxWidth: 400 }}>
        No tienes permiso para ver esta página. Si crees que esto es un error, contacta al administrador.
      </p>
      <Link
        to={user ? (REDIRECT[user.rol] ?? '/') : '/login'}
        className="btn btn-ss-dark px-4 py-2"
      >
        <i className="bi bi-arrow-left me-2" />
        Volver al inicio
      </Link>
    </div>
  );
}
