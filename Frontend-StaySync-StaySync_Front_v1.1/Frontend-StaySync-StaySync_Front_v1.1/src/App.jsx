import { lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import ProtectedRoute from './components/auth/ProtectedRoute';
import Layout         from './components/common/Layout';

import LoginPage        from './pages/LoginPage';
import AuthCallbackPage from './pages/AuthCallbackPage';
import UnauthorizedPage from './pages/UnauthorizedPage';

/* Las páginas del Layout se descargan al entrar: un huésped no baja el panel de recepción.
   El <Suspense> está en Layout, así la barra lateral no parpadea al cambiar de página. */
const PerfilPage = lazy(() => import('./pages/PerfilPage'));

/* Recepción / Admin */
const DashboardRecepcion    = lazy(() => import('./components/recepcion/DashboardRecepcion'));
const HabitacionesRecepcion = lazy(() => import('./pages/recepcion/HabitacionesRecepcion'));
const ReservasRecepcion     = lazy(() => import('./pages/recepcion/ReservasRecepcion'));
const HuespedesRecepcion    = lazy(() => import('./pages/recepcion/HuespedesRecepcion'));
const CheckinRecepcion      = lazy(() => import('./pages/recepcion/CheckinRecepcion'));
const PagosRecepcion        = lazy(() => import('./pages/recepcion/PagosRecepcion'));
const UsuariosAdmin         = lazy(() => import('./pages/recepcion/UsuariosAdmin'));
const DashboardOperaciones  = lazy(() => import('./components/operaciones/DashboardOperaciones'));
const AseoRecepcion         = lazy(() => import('./pages/recepcion/AseoRecepcion'));
const MarcaHotelAdmin       = lazy(() => import('./pages/recepcion/MarcaHotelAdmin'));
const HotelesAdmin          = lazy(() => import('./pages/recepcion/HotelesAdmin'));
const CalendarioRecepcion   = lazy(() => import('./pages/recepcion/CalendarioRecepcion'));
const ReportesRecepcion     = lazy(() => import('./pages/recepcion/ReportesRecepcion'));
const ServiciosAdmin        = lazy(() => import('./pages/recepcion/ServiciosAdmin'));

/* Huésped */
const DashboardHuesped   = lazy(() => import('./pages/huesped/DashboardHuesped'));
const ReservasHuesped    = lazy(() => import('./pages/huesped/ReservasHuesped'));
const BuscarHabitaciones = lazy(() => import('./pages/huesped/BuscarHabitaciones'));
const ServiciosHuesped   = lazy(() => import('./pages/huesped/ServiciosHuesped'));
const PasarelaPago       = lazy(() => import('./pages/huesped/PasarelaPago'));
const PagoExitoso        = lazy(() => import('./pages/huesped/PagoExitoso'));
const PortalesHuesped    = lazy(() => import('./pages/huesped/PortalesHuesped'));

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* ── Public ── */}
        <Route path="/login"        element={<LoginPage />} />
        <Route path="/auth/callback" element={<AuthCallbackPage />} />
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
            <Route path="/recepcion/aseo"        element={<AseoRecepcion />} />
            <Route path="/recepcion/calendario"  element={<CalendarioRecepcion />} />
          </Route>
        </Route>

        {/* ── Portal Recepción — solo ADMIN ── */}
        <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
          <Route element={<Layout />}>
            <Route path="/recepcion/usuarios" element={<UsuariosAdmin />} />
            <Route path="/recepcion/marca"    element={<MarcaHotelAdmin />} />
            <Route path="/recepcion/servicios" element={<ServiciosAdmin />} />
            <Route path="/recepcion/reportes" element={<ReportesRecepcion />} />
          </Route>
        </Route>

        {/* ── Plataforma — solo SUPER_ADMIN ── */}
        <Route element={<ProtectedRoute allowedRoles={['SUPER_ADMIN']} />}>
          <Route element={<Layout />}>
            <Route path="/recepcion/hoteles" element={<HotelesAdmin />} />
          </Route>
        </Route>

        {/* ── Portal Huésped ── */}
        <Route element={<ProtectedRoute allowedRoles={['HUESPED']} />}>
          <Route element={<Layout />}>
            <Route path="/huesped"           element={<DashboardHuesped />} />
            <Route path="/huesped/hoteles"   element={<PortalesHuesped />} />
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
