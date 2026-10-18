import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { aplicarTema, guardarMarca, marcaGuardada } from '../theme/hotelTheme';

const ThemeContext = createContext(null);

/**
 * Marca del hotel actual (nombre, logo y colores). La fija el login (hotel del usuario) o
 * la página de login de un portal (?hotel=slug). Se guarda en localStorage para que al
 * recargar la página no aparezcan un instante los colores por defecto.
 */
export function ThemeProvider({ children }) {
  const [marca, setMarcaState] = useState(marcaGuardada);

  useEffect(() => {
    aplicarTema(marca?.theme);
  }, [marca]);

  const setMarca = useCallback((nueva) => {
    guardarMarca(nueva);
    setMarcaState(nueva);
  }, []);

  const value = useMemo(() => ({ marca, setMarca }), [marca, setMarca]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useMarca() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useMarca debe usarse dentro de <ThemeProvider>');
  return ctx;
}
