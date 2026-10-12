import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth, tieneRol } from '../../context/AuthContext';

/**
 * RBAC guard.
 * @param {string[]} allowedRoles - roles allowed to access this route.
 *   Empty array = only authentication required.
 */
export default function ProtectedRoute({ allowedRoles = [] }) {
  const { isAuthenticated, user } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return (
      <Navigate
        to="/login"
        state={{ from: location }}
        replace
      />
    );
  }

  if (allowedRoles.length > 0 && !tieneRol(user?.rol, allowedRoles)) {
    return <Navigate to="/unauthorized" replace />;
  }

  return <Outlet />;
}
