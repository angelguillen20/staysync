import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

const NAV = {
  HUESPED: [
    { to: '/huesped',           icon: 'bi-house',          label: 'Inicio' },
    { to: '/huesped/buscar',    icon: 'bi-search',         label: 'Buscar Habitaciones' },
    { to: '/huesped/reservas',  icon: 'bi-calendar-check', label: 'Mis Reservas' },
    { to: '/huesped/servicios', icon: 'bi-bell',           label: 'Servicios' },
  ],
  RECEPCIONISTA: [
    { to: '/recepcion',              icon: 'bi-speedometer2',      label: 'Dashboard' },
    { to: '/recepcion/habitaciones', icon: 'bi-grid-3x3-gap',      label: 'Habitaciones' },
    { to: '/recepcion/reservas',     icon: 'bi-calendar-week',     label: 'Reservas' },
    { to: '/recepcion/checkin',      icon: 'bi-door-open',         label: 'Check-in / Check-out' },
    { to: '/recepcion/pagos',        icon: 'bi-credit-card',       label: 'Pagos' },
    { to: '/recepcion/huespedes',    icon: 'bi-people',            label: 'Huéspedes' },
    { to: '/recepcion/operaciones',  icon: 'bi-clipboard-check',   label: 'Operaciones' },
  ],
  ADMIN: [
    { to: '/recepcion',              icon: 'bi-speedometer2',      label: 'Dashboard' },
    { to: '/recepcion/habitaciones', icon: 'bi-grid-3x3-gap',      label: 'Habitaciones' },
    { to: '/recepcion/reservas',     icon: 'bi-calendar-week',     label: 'Reservas' },
    { to: '/recepcion/checkin',      icon: 'bi-door-open',         label: 'Check-in / Check-out' },
    { to: '/recepcion/pagos',        icon: 'bi-credit-card',       label: 'Pagos' },
    { to: '/recepcion/huespedes',    icon: 'bi-people',            label: 'Huéspedes' },
    { to: '/recepcion/usuarios',     icon: 'bi-person-badge',      label: 'Usuarios' },
    { to: '/recepcion/operaciones',  icon: 'bi-clipboard-check',   label: 'Operaciones' },
  ],
};

export default function Sidebar({ open }) {
  const { user } = useAuth();
  const links = NAV[user?.rol] ?? [];

  return (
    <aside className={`sidebar ${open ? 'open' : ''}`}>
      <div className="px-3 pb-2">
        <small className="sidebar-section-title">Menú principal</small>
      </div>

      <nav className="nav flex-column">
        {links.map(({ to, icon, label }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/recepcion' || to === '/huesped'}
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          >
            <i className={`bi ${icon}`} />
            {label}
          </NavLink>
        ))}
      </nav>

      <hr className="border-secondary mx-3" />
      <small className="sidebar-section-title">Cuenta</small>
      <nav className="nav flex-column">
        <NavLink to="/perfil" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <i className="bi bi-person-gear" /> Mi Perfil
        </NavLink>
      </nav>
    </aside>
  );
}
