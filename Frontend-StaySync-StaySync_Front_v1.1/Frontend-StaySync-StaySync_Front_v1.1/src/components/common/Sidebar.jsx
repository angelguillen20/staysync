import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

const PERSONAL = ['ADMIN', 'RECEPCIONISTA', 'SUPER_ADMIN'];
const ADMIN = ['ADMIN', 'SUPER_ADMIN'];

/* Agrupado por tarea: lo de hoy arriba, la administración al final. roles: quién ve el enlace. */
const GRUPOS = [
  { titulo: 'Tu estadía', links: [
    { to: '/huesped',           icon: 'bi-house',          label: 'Inicio',              roles: ['HUESPED'] },
    { to: '/huesped/buscar',    icon: 'bi-search',         label: 'Buscar habitaciones', roles: ['HUESPED'] },
    { to: '/huesped/reservas',  icon: 'bi-calendar-check', label: 'Mis reservas',        roles: ['HUESPED'] },
    { to: '/huesped/servicios', icon: 'bi-bell',           label: 'Servicios',           roles: ['HUESPED'] },
    { to: '/huesped/hoteles',   icon: 'bi-buildings',      label: 'Otros hoteles',       roles: ['HUESPED'] },
  ] },
  { titulo: 'Hoy', links: [
    { to: '/recepcion',            icon: 'bi-speedometer2', label: 'Panel',                 roles: PERSONAL },
    { to: '/recepcion/checkin',    icon: 'bi-door-open',    label: 'Check-in y check-out',  roles: PERSONAL },
    { to: '/recepcion/calendario', icon: 'bi-calendar3',    label: 'Calendario',            roles: PERSONAL },
  ] },
  { titulo: 'Reservas', links: [
    { to: '/recepcion/reservas',  icon: 'bi-calendar-week', label: 'Reservas',  roles: PERSONAL },
    { to: '/recepcion/huespedes', icon: 'bi-people',        label: 'Huéspedes', roles: PERSONAL },
    { to: '/recepcion/pagos',     icon: 'bi-credit-card',   label: 'Pagos',     roles: PERSONAL },
  ] },
  { titulo: 'Hotel', links: [
    { to: '/recepcion/habitaciones', icon: 'bi-grid-3x3-gap',    label: 'Habitaciones', roles: PERSONAL },
    { to: '/recepcion/servicios',    icon: 'bi-cup-hot',         label: 'Servicios',    roles: ADMIN },
    { to: '/recepcion/operaciones',  icon: 'bi-clipboard-check', label: 'Operaciones',  roles: PERSONAL },
    { to: '/recepcion/aseo',         icon: 'bi-stars',           label: 'Aseo',         roles: PERSONAL },
  ] },
  { titulo: 'Administración', links: [
    { to: '/recepcion/reportes', icon: 'bi-bar-chart',     label: 'Reportes',        roles: ADMIN },
    { to: '/recepcion/usuarios', icon: 'bi-person-badge',  label: 'Usuarios',        roles: ADMIN },
    { to: '/recepcion/marca',    icon: 'bi-palette',       label: 'Marca del hotel', roles: ADMIN },
    { to: '/recepcion/hoteles',  icon: 'bi-buildings',     label: 'Hoteles',         roles: ['SUPER_ADMIN'] },
  ] },
];

export default function Sidebar({ open }) {
  const { user } = useAuth();
  const grupos = GRUPOS
    .map(g => ({ ...g, links: g.links.filter(l => l.roles.includes(user?.rol)) }))
    .filter(g => g.links.length > 0);

  return (
    <aside className={`sidebar ${open ? 'open' : ''}`} aria-label="Menú">
      {grupos.map(g => (
        <nav key={g.titulo} className="nav flex-column mb-1" aria-label={g.titulo}>
          <small className="sidebar-section-title">{g.titulo}</small>
          {g.links.map(({ to, icon, label }) => (
            <NavLink key={to} to={to} end={to === '/recepcion' || to === '/huesped'}
                     className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              <i className={`bi ${icon}`} aria-hidden="true" />
              {label}
            </NavLink>
          ))}
        </nav>
      ))}

      <nav className="nav flex-column" aria-label="Cuenta">
        <small className="sidebar-section-title">Cuenta</small>
        <NavLink to="/perfil" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <i className="bi bi-person-gear" aria-hidden="true" /> Mi perfil
        </NavLink>
      </nav>
    </aside>
  );
}
