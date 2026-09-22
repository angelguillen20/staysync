import { createContext, useState, useCallback, useContext, useEffect } from 'react';

const AuthContext = createContext(null);

const KEYS = {
  access:  'ss_access_token',
  refresh: 'ss_refresh_token',
  user:    'ss_user',
};

function readLocalUser() {
  try {
    const raw = localStorage.getItem(KEYS.user);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user,        setUser]        = useState(readLocalUser);
  const [accessToken, setAccessToken] = useState(() => localStorage.getItem(KEYS.access) || null);
  const [loading,     setLoading]     = useState(false);

  const isAuthenticated = !!(accessToken && user);

  /* Called after a successful /login or /refresh response */
  const login = useCallback((authData) => {
    const {
      accessToken:   at,
      refreshToken:  rt,
      email,
      rol,
      userId,
      nombreCompleto,
    } = authData;

    const userData = { email, rol, userId, nombreCompleto };

    localStorage.setItem(KEYS.access,  at);
    localStorage.setItem(KEYS.refresh, rt);
    localStorage.setItem(KEYS.user,    JSON.stringify(userData));

    setAccessToken(at);
    setUser(userData);
  }, []);

  const logout = useCallback(() => {
    Object.values(KEYS).forEach(k => localStorage.removeItem(k));
    setAccessToken(null);
    setUser(null);
  }, []);

  // Actualiza solo los datos del usuario en contexto y localStorage, sin tocar los tokens
  const updateUser = useCallback((partial) => {
    setUser(prev => {
      const updated = { ...prev, ...partial };
      localStorage.setItem(KEYS.user, JSON.stringify(updated));
      return updated;
    });
  }, []);

  /* Expose the current token for the axios interceptor */
  const getToken = useCallback(
    () => accessToken || localStorage.getItem(KEYS.access),
    [accessToken],
  );

  /* Sync token state if another tab logs in/out */
  useEffect(() => {
    const onStorage = (e) => {
      if (e.key === KEYS.access) {
        if (!e.newValue) { logout(); }
        else { setAccessToken(e.newValue); setUser(readLocalUser()); }
      }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [logout]);

  const hasRole = useCallback(
    (...roles) => !!user && roles.includes(user.rol),
    [user],
  );

  return (
    <AuthContext.Provider value={{
      user,
      accessToken,
      isAuthenticated,
      loading,
      setLoading,
      login,
      logout,
      updateUser,
      getToken,
      hasRole,
    }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}

export default AuthContext;
