import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap-icons/font/bootstrap-icons.css';
import './styles/global.css';
import { AuthProvider } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import App from './App';

// El deploy hace `s3 sync --delete`: una pestaña abierta desde antes pide páginas que ya no
// existen. Recargar trae el index.html nuevo (una sola vez, para no entrar en bucle).
window.addEventListener('vite:preloadError', () => {
  if (sessionStorage.getItem('ss_recarga_deploy')) return;
  sessionStorage.setItem('ss_recarga_deploy', '1');
  window.location.reload();
});
window.addEventListener('load', () => setTimeout(() => sessionStorage.removeItem('ss_recarga_deploy'), 10_000));

createRoot(document.getElementById('root')).render(
  <StrictMode>
    {/* La marca envuelve a la sesión: al iniciar sesión se aplica la del hotel del usuario */}
    <ThemeProvider>
      <AuthProvider>
        <App />
      </AuthProvider>
    </ThemeProvider>
  </StrictMode>
);
