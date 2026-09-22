import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import ProtectedRoute from './components/auth/ProtectedRoute';
import Layout         from './components/common/Layout';

import LoginPage        from './pages/LoginPage';
import UnauthorizedPage from './pages/UnauthorizedPage';
import PerfilPage       from './pages/PerfilPage';

/* Recepción / Admin */
import DashboardRecepcion   from './components/recepcion/DashboardRecepcion';
import HabitacionesRecepcion from './pages/recepcion/HabitacionesRecepcion';
import ReservasRecepcion    from './pages/recepcion/ReservasRecepcion';
import HuespedesRecepcion   from './pages/recepcion/HuespedesRecepcion';
import CheckinRecepcion     from './pages/recepcion/CheckinRecepcion';
import PagosRecepcion       from './pages/recepcion/PagosRecepcion';
import UsuariosAdmin        from './pages/recepcion/UsuariosAdmin';
import DashboardOperaciones from './components/operaciones/DashboardOperaciones';

/* Huésped */
import DashboardHuesped   from './pages/huesped/DashboardHuesped';
import ReservasHuesped    from './pages/huesped/ReservasHuesped';
import BuscarHabitaciones from './pages/huesped/BuscarHabitaciones';
import ServiciosHuesped   from './pages/huesped/ServiciosHuesped';
import PasarelaPago       from './pages/huesped/PasarelaPago';
import PagoExitoso        from './pages/huesped/PagoExitoso';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* ── Public ── */}
        <Route path="/login"        element={<LoginPage />} />
        <Route path="/unauthorized" element={<UnauthorizedPage />} />
        <Route path="/"             element={<Navigate to="/login" replace />} />

        {/* ── Portal Recepción — ADMIN + RECEPCIONISTA ── */}
        <Route element={<ProtectedRoute allowedRoles={['ADMIN', 'RECEPCIONISTA']} />}>
          <Route element={<Layout />}>
            <Route path="/recepcion"             element={<DashboardRecepcion />} />
            <Route path="/recepcion/habitaciones" element={<HabitacionesRecepcion />} />
            <Route path="/recepcion/reservas"    element={<ReservasRecepcion />} />
            <Route path="/recepcion/checkin"     element={<CheckinRecepcion />} />
            <Route path="/recepcion/huespedes"   element={<HuespedesRecepcion />} />
            <Route path="/recepcion/pagos"       element={<PagosRecepcion />} />
            <Route path="/recepcion/operaciones" element={<DashboardOperaciones />} />
          </Route>
        </Route>

        {/* ── Portal Recepción — solo ADMIN ── */}
        <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
          <Route element={<Layout />}>
            <Route path="/recepcion/usuarios" element={<UsuariosAdmin />} />
          </Route>
        </Route>

        {/* ── Portal Huésped ── */}
        <Route element={<ProtectedRoute allowedRoles={['HUESPED']} />}>
          <Route element={<Layout />}>
            <Route path="/huesped"           element={<DashboardHuesped />} />
            <Route path="/huesped/reservas"  element={<ReservasHuesped />} />
            <Route path="/huesped/buscar"       element={<BuscarHabitaciones />} />
            <Route path="/huesped/servicios"    element={<ServiciosHuesped />} />
            <Route path="/huesped/pago"         element={<PasarelaPago />} />
            <Route path="/huesped/pago-exitoso" element={<PagoExitoso />} />
            <Route path="/huesped/pago-fallido" element={<PagoExitoso />} />
          </Route>
        </Route>

        {/* ── Perfil — cualquier usuario autenticado ── */}
        <Route element={<ProtectedRoute allowedRoles={[]} />}>
          <Route element={<Layout />}>
            <Route path="/perfil" element={<PerfilPage />} />
          </Route>
        </Route>

        {/* ── Fallback ── */}
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
