import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import Navbar  from './Navbar';
import Sidebar from './Sidebar';

export default function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <>
      <Navbar onToggleSidebar={() => setSidebarOpen(p => !p)} />
      <Sidebar open={sidebarOpen} />
      <main className="main-content">
        <Outlet />
      </main>
    </>
  );
}
