import { Suspense, useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import Navbar  from './Navbar';
import Sidebar from './Sidebar';
import CierreInactividad from './CierreInactividad';
import LoadingSpinner from './LoadingSpinner';

export default function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const { pathname } = useLocation();

  // En celular el menú tapa el contenido: se cierra al navegar
  useEffect(() => setSidebarOpen(false), [pathname]);

  return (
    <>
      <Navbar onToggleSidebar={() => setSidebarOpen(p => !p)} />
      <Sidebar open={sidebarOpen} />
      {sidebarOpen && <div className="sidebar-backdrop d-md-none" onClick={() => setSidebarOpen(false)} />}
      <CierreInactividad />
      <main className="main-content">
        {/* Las páginas se cargan bajo demanda (React.lazy en App.jsx) */}
        <Suspense fallback={<LoadingSpinner />}>
          <Outlet />
        </Suspense>
      </main>
    </>
  );
}
