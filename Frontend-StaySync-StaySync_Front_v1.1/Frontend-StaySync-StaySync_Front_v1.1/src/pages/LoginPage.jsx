import { useEffect, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { login as loginService, register as registerSvc } from '../services/authService';
import { generateCodeVerifier, generateCodeChallenge } from '../services/pkce';
import AlertMessage from '../components/common/AlertMessage';
import { COGNITO_DOMAIN, COGNITO_CLIENT_ID, COGNITO_REDIRECT_URI } from '../config/cognito';
import { useMarca } from '../context/ThemeContext';
import { getMarcaPublica } from '../services/hotelesService';
import { esLogoValido, esSlugValido, guardarSlug, slugGuardado } from '../theme/hotelTheme';

const REDIRECT = { SUPER_ADMIN: '/recepcion', ADMIN: '/recepcion', RECEPCIONISTA: '/recepcion', HUESPED: '/huesped' };

// Cuentas que crea DataInitializer (usuarios-service). En un despliegue real se ocultan con
// VITE_DEMO_CREDENCIALES=false: incluyen la del super admin, que administra todos los hoteles.
const MOSTRAR_DEMO = import.meta.env.VITE_DEMO_CREDENCIALES !== 'false';
const CUENTAS_DEMO = [
  { hotel: 'Plataforma', slug: null, cuentas: [
    { rol: 'Super admin', email: 'superadmin@staysync.com', password: 'Super@1234' },
  ] },
  { hotel: 'StaySync Demo', slug: 'staysync-demo', cuentas: [
    { rol: 'Admin',     email: 'admin@staysync.com',   password: 'Admin@1234' },
    { rol: 'Recepción', email: 'recep@staysync.com',   password: 'Recep@1234' },
    { rol: 'Huésped',   email: 'huesped@staysync.com', password: 'Huesped@1234' },
  ] },
  { hotel: 'Hotel Andes', slug: 'hotel-andes', cuentas: [
    { rol: 'Admin',     email: 'admin@hotelandes.com',   password: 'Admin@1234' },
    { rol: 'Recepción', email: 'recep@hotelandes.com',   password: 'Recep@1234' },
    { rol: 'Huésped',   email: 'huesped@hotelandes.com', password: 'Huesped@1234' },
  ] },
];

/** Un clic en una cuenta rellena el formulario; "Ver portal" abre el login con la marca del hotel. */
function CredencialesDemo({ onUsar, onPortal }) {
  return (
    <div className="mt-4 p-3 rounded" style={{ background: 'rgba(239,193,67,0.1)', border: '1px solid rgba(239,193,67,0.3)' }}>
      <p className="mb-2 fw-semibold small" style={{ color: 'var(--ss-dark)' }}>
        <i className="bi bi-info-circle me-1" />Credenciales de demo <span className="fw-normal text-muted">· clic para usar</span>
      </p>
      {CUENTAS_DEMO.map(({ hotel, slug, cuentas }) => (
        <div key={hotel} className="mb-2">
          <div className="d-flex justify-content-between align-items-center">
            <small className="fw-semibold" style={{ color: 'var(--ss-dark)' }}>{hotel}</small>
            {slug && (
              <button type="button" className="btn btn-link btn-sm p-0 small" onClick={() => onPortal(slug)}>
                Ver portal <i className="bi bi-box-arrow-up-right" />
              </button>
            )}
          </div>
          <div className="d-flex flex-wrap gap-1 mt-1">
            {cuentas.map((c) => (
              <button
                key={c.email}
                type="button"
                className="btn btn-sm btn-outline-secondary py-0"
                style={{ fontSize: '0.75rem' }}
                title={`${c.email} / ${c.password}`}
                onClick={() => onUsar(c)}
              >
                {c.rol}
              </button>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}


const PW_RULES = [
  { id: 'len', label: 'Al menos 8 caracteres',  test: pw => pw.length >= 8 },
  { id: 'up',  label: 'Una letra mayúscula',     test: pw => /[A-Z]/.test(pw) },
  { id: 'low', label: 'Una letra minúscula',     test: pw => /[a-z]/.test(pw) },
  { id: 'num', label: 'Un número (0–9)',         test: pw => /\d/.test(pw) },
  { id: 'sym', label: 'Un símbolo (@$!%*?&)',   test: pw => /[@$!%*?&]/.test(pw) },
];

// Equivale a /^[^\s@]+@[^\s@]+\.[^\s@]+$/ pero sin regex, para evitar el backtracking superlineal.
function validEmail(e) {
  if (/\s/.test(e)) return false;
  const at = e.indexOf('@');
  if (at < 1 || at !== e.lastIndexOf('@')) return false;
  const dominio = e.slice(at + 1);
  const punto = dominio.indexOf('.', 1);
  return punto !== -1 && punto < dominio.length - 1;
}
function validTel(t)   { return !t || /^[+]?\d{7,15}$/.test(t); }

/** Sin respuesta o 5xx: el problema es del servidor, no de la contraseña. */
function mensajeDeError(err, porDefecto) {
  if (!err.response || err.response.status >= 500) {
    return 'El servicio no está disponible en este momento. Intenta de nuevo en unos minutos.';
  }
  return err.response.data?.message ?? porDefecto;
}

/** Logo y nombre del hotel del portal, o los de StaySync si no hay marca. */
function MarcaHotel({ marca, tamano }) {
  if (marca?.logoUrl && esLogoValido(marca.logoUrl)) {
    return <img src={marca.logoUrl} alt={marca.nombre} style={{ maxHeight: tamano, maxWidth: '70%' }} />;
  }
  return <span style={{ fontSize: tamano }}>🏨</span>;
}
function validPw(pw)   { return PW_RULES.every(r => r.test(pw)); }

// Devuelve los campos del registro que no pasan la validación
function camposInvalidos(reg, confirmar) {
  const checks = {
    nombre:    reg.nombre.trim() !== '',
    apellido:  reg.apellido.trim() !== '',
    email:     validEmail(reg.email),
    telefono:  validTel(reg.telefono),
    password:  validPw(reg.password),
    confirmar: confirmar === reg.password,
  };
  return Object.keys(checks).filter(k => !checks[k]);
}

function PasswordChecklist({ password }) {
  if (!password) return null;
  return (
    <div className="mt-2 p-2 rounded" style={{ background: 'rgba(0,0,0,0.03)', border: '1px solid rgba(0,0,0,0.07)' }}>
      {PW_RULES.map(r => {
        const ok = r.test(password);
        return (
          <div
            key={r.id}
            className="d-flex align-items-center gap-2 mb-1"
            style={{ fontSize: '0.76rem', color: ok ? '#198754' : '#6c757d', transition: 'color 0.2s' }}
          >
            <i className={`bi ${ok ? 'bi-check-circle-fill' : 'bi-circle'}`} style={{ fontSize: '0.72rem' }} />
            {r.label}
          </div>
        );
      })}
    </div>
  );
}

export default function LoginPage() {
  const [form,         setForm]         = useState({ email: '', password: '' });
  const [error,        setError]        = useState('');
  const [loading,      setLoading]      = useState(false);
  const [tab,          setTab]          = useState('login');

  // Registro
  const [reg,          setReg]          = useState({ nombre: '', apellido: '', email: '', password: '', telefono: '' });
  const [confirmar,    setConfirmar]    = useState('');
  const [regTouched,   setRegTouched]   = useState({});
  const [regErr,       setRegErr]       = useState('');
  const [regOk,        setRegOk]        = useState(false);

  // Visibilidad de contraseñas
  const [showPass,     setShowPass]     = useState(false);
  const [showRegPass,  setShowRegPass]  = useState(false);
  const [showConfPass, setShowConfPass] = useState(false);

  const { login } = useAuth();
  const { marca, setMarca } = useMarca();
  const navigate  = useNavigate();
  const location  = useLocation();
  const from      = location.state?.from?.pathname;

  // Portal del hotel: /login?hotel=hotel-andes muestra su logo y colores antes de entrar.
  // Sin ?hotel es el login principal de StaySync: se quita la marca del último portal y,
  // al entrar, el huésped elige el hotel en el selector de portales.
  const slugPortal = new URLSearchParams(location.search).get('hotel');
  useEffect(() => {
    const slug = new URLSearchParams(location.search).get('hotel');
    if (!slug) {
      guardarSlug(null);
      setMarca(null);
      return;
    }
    if (!esSlugValido(slug)) return;
    guardarSlug(slug);
    getMarcaPublica(slug)
      .then(setMarca)
      .catch(() => setError('No encontramos ese hotel. Revisa el enlace que te compartieron.'));
  }, [location.search, setMarca]);

  const nombreMarca = marca?.nombre ?? 'StaySync';

  const touch    = field => setRegTouched(p => ({ ...p, [field]: true }));
  const onChange  = field => e => {
    setReg(p => ({ ...p, [field]: e.target.value }));
    if (regTouched[field]) touch(field);
  };

  const fieldClass = (field, ok) => {
    if (!regTouched[field]) return 'form-control';
    return ok ? 'form-control is-valid' : 'form-control is-invalid';
  };

  /* ── Login ───────────────────────────────────────────── */
  const handleLogin = async e => {
    e.preventDefault();
    setError(''); setLoading(true);
    try {
      const data = await loginService(form.email, form.password, slugGuardado());
      login(data);
      const destinoHuesped = slugPortal ? '/huesped' : '/huesped/hoteles';
      navigate(from ?? (data.rol === 'HUESPED' ? destinoHuesped : REDIRECT[data.rol]) ?? '/huesped', { replace: true });
    } catch (err) {
      setError(mensajeDeError(err, 'Credenciales incorrectas. Intente nuevamente.'));
    } finally {
      setLoading(false);
    }
  };

  /* ── Google (Cognito Hosted UI + PKCE) ──────────────────── */
  const handleGoogleLogin = async () => {
    const verifier = generateCodeVerifier();
    sessionStorage.setItem('ss_pkce_verifier', verifier);
    sessionStorage.setItem('ss_oauth_redirect_uri', COGNITO_REDIRECT_URI);
    const challenge = await generateCodeChallenge(verifier);

    const params = new URLSearchParams({
      client_id: COGNITO_CLIENT_ID,
      response_type: 'code',
      scope: 'email openid profile',
      redirect_uri: COGNITO_REDIRECT_URI,
      identity_provider: 'Google',
      code_challenge: challenge,
      code_challenge_method: 'S256',
    });
    window.location.href = `${COGNITO_DOMAIN}/oauth2/authorize?${params.toString()}`;
  };

  /* ── Register ────────────────────────────────────────── */
  const handleRegister = async e => {
    e.preventDefault();
    setRegTouched({ nombre: true, apellido: true, email: true, telefono: true, password: true, confirmar: true });

    if (camposInvalidos(reg, confirmar).length > 0) {
      setRegErr('Corrige los campos marcados en rojo antes de continuar.');
      return;
    }

    setRegErr(''); setLoading(true);
    try {
      await registerSvc({ ...reg, hotelSlug: slugGuardado() });
      setRegOk(true);
      setTimeout(() => { setTab('login'); setRegOk(false); }, 2500);
    } catch (err) {
      // Un huésped tiene una sola cuenta para todos los hoteles: si ya existe, que inicie sesión aquí
      setRegErr(err.response?.status === 409
        ? `Ya tienes una cuenta con ese email. Inicia sesión con ella: sirve también en ${marca?.nombre ?? 'este hotel'}.`
        : mensajeDeError(err, 'No se pudo registrar. Intente más tarde.'));
    } finally {
      setLoading(false);
    }
  };

  const switchTab = t => {
    setTab(t);
    setError(''); setRegErr(''); setRegOk(false);
    setRegTouched({});
  };

  return (
    <div className="login-container row g-0 min-vh-100">
      {/* ── Panel de marca ── */}
      <div className="col-md-5 login-brand-panel d-none d-md-flex fade-in-up">
        <div className="brand-logo mb-4"><MarcaHotel marca={marca} tamano={72} /></div>
        <h1>{nombreMarca}</h1>
        <p className="text-center mt-2 px-4">
          Sistema integral de gestión hotelera.<br />
          Optimiza operaciones, maximiza la satisfacción de tus huéspedes.
        </p>
        <div className="mt-5 d-flex flex-column gap-3 w-100" style={{ maxWidth: 280 }}>
          {[
            { icon: 'bi-shield-check',   text: 'Seguridad JWT & RBAC' },
            { icon: 'bi-graph-up-arrow', text: 'Dashboard en tiempo real' },
            { icon: 'bi-bell',           text: 'Notificaciones automáticas' },
          ].map(({ icon, text }) => (
            <div key={text} className="d-flex align-items-center gap-3">
              <i className={`bi ${icon} fs-5 text-warning`} />
              <span className="text-white-50 small">{text}</span>
            </div>
          ))}
        </div>
      </div>

      {/* ── Panel de formulario ── */}
      <div className="col-md-7 login-form-panel fade-in-up">
        <div className="login-form-card">
          {/* Logo mobile */}
          <div className="text-center mb-4 d-md-none">
            <MarcaHotel marca={marca} tamano={56} />
            <h2 className="fw-bold" style={{ color: 'var(--ss-dark)' }}>{nombreMarca}</h2>
          </div>

          {/* Tabs */}
          <ul className="nav nav-pills mb-4 border-bottom pb-3">
            {[['login', 'Iniciar Sesión'], ['register', 'Registrarse']].map(([k, l]) => (
              <li key={k} className="nav-item">
                <button
                  className={`nav-link fw-semibold ${tab === k ? 'active' : ''}`}
                  style={tab === k
                    ? { backgroundColor: 'var(--ss-dark)', color: '#fff' }
                    : { color: 'var(--ss-dark)' }
                  }
                  onClick={() => switchTab(k)}
                >
                  {l}
                </button>
              </li>
            ))}
          </ul>

          {/* ══ LOGIN ══ */}
          {tab === 'login' && (
            <>
              <h3 className="fw-bold mb-1">Bienvenido</h3>
              <p className="text-muted small mb-4">Ingresa tus credenciales para continuar</p>
              {!error && location.state?.motivo === 'inactividad' && (
                <AlertMessage type="info" message="Tu sesión se cerró por inactividad. Vuelve a iniciar sesión." />
              )}
              <AlertMessage message={error} onClose={() => setError('')} />

              <form onSubmit={handleLogin} noValidate>
                <div className="mb-3">
                  <label htmlFor="login-email" className="form-label fw-medium small">Correo electrónico</label>
                  <div className="input-group">
                    <span className="input-group-text"><i className="bi bi-envelope" /></span>
                    <input
                      id="login-email"
                      type="email"
                      className="form-control"
                      placeholder="usuario@staysync.com"
                      value={form.email}
                      onChange={e => setForm(p => ({ ...p, email: e.target.value }))}
                      required
                      autoComplete="email"
                    />
                  </div>
                </div>

                <div className="mb-4">
                  <label htmlFor="login-password" className="form-label fw-medium small">Contraseña</label>
                  <div className="input-group">
                    <span className="input-group-text"><i className="bi bi-lock" /></span>
                    <input
                      id="login-password"
                      type={showPass ? 'text' : 'password'}
                      className="form-control"
                      placeholder="••••••••"
                      value={form.password}
                      onChange={e => setForm(p => ({ ...p, password: e.target.value }))}
                      required
                      autoComplete="current-password"
                    />
                    <button
                      type="button"
                      className="input-group-text bg-white border-start-0"
                      onClick={() => setShowPass(v => !v)}
                      tabIndex={-1}
                    >
                      <i className={`bi ${showPass ? 'bi-eye-slash' : 'bi-eye'}`} />
                    </button>
                  </div>
                </div>

                <button type="submit" className="btn btn-ss-dark w-100 py-2 fw-semibold" disabled={loading}>
                  {loading
                    ? <><span className="spinner-border spinner-border-sm me-2" />Verificando...</>
                    : <><i className="bi bi-box-arrow-in-right me-2" />Ingresar</>
                  }
                </button>
              </form>

              <div className="d-flex align-items-center gap-3 my-3">
                <hr className="flex-grow-1 m-0" />
                <span className="text-muted small">o</span>
                <hr className="flex-grow-1 m-0" />
              </div>

              <button
                type="button"
                className="btn btn-outline-secondary w-100 py-2 fw-semibold d-flex align-items-center justify-content-center gap-2"
                onClick={handleGoogleLogin}
              >
                <i className="bi bi-google" />Continuar con Google
              </button>

              {MOSTRAR_DEMO && (
                <CredencialesDemo
                  onUsar={({ email, password }) => { setForm({ email, password }); setError(''); }}
                  onPortal={(slug) => navigate(`/login?hotel=${slug}`)}
                />
              )}
            </>
          )}

          {/* ══ REGISTRO ══ */}
          {tab === 'register' && (
            <>
              <h3 className="fw-bold mb-1">Crear cuenta</h3>
              <p className="text-muted small mb-2">Registro de huéspedes · Todos los campos marcados con <span className="text-danger">*</span> son obligatorios</p>
              {/* El hotel sale del portal (?hotel=slug); sin portal, el backend usa el hotel por defecto */}
              <div className="small mb-4 p-2 rounded" style={{ background: 'rgba(0,0,0,0.03)' }}>
                <i className="bi bi-building me-1" />
                Te registrarás como huésped de <strong>{marca?.nombre ?? 'StaySync Demo'}</strong>.
                {' '}Para otro hotel, entra con el enlace de su portal.
              </div>

              {regOk ? (
                <div className="alert alert-success text-center">
                  <i className="bi bi-check-circle-fill me-2" />¡Registro exitoso! Redirigiendo al login...
                </div>
              ) : (
                <>
                  <AlertMessage message={regErr} onClose={() => setRegErr('')} />

                  <form onSubmit={handleRegister} noValidate>

                    {/* Nombre / Apellido */}
                    <div className="row g-3 mb-3">
                      <div className="col-6">
                        <label htmlFor="registro-nombre" className="form-label fw-medium small">
                          Nombre <span className="text-danger">*</span>
                        </label>
                        <input
                          id="registro-nombre"
                          className={fieldClass('nombre', reg.nombre.trim().length > 0)}
                          placeholder="Juan"
                          value={reg.nombre}
                          onBlur={() => touch('nombre')}
                          onChange={onChange('nombre')}
                        />
                        <div className="invalid-feedback">El nombre es requerido.</div>
                      </div>
                      <div className="col-6">
                        <label htmlFor="registro-apellido" className="form-label fw-medium small">
                          Apellido <span className="text-danger">*</span>
                        </label>
                        <input
                          id="registro-apellido"
                          className={fieldClass('apellido', reg.apellido.trim().length > 0)}
                          placeholder="García"
                          value={reg.apellido}
                          onBlur={() => touch('apellido')}
                          onChange={onChange('apellido')}
                        />
                        <div className="invalid-feedback">El apellido es requerido.</div>
                      </div>
                    </div>

                    {/* Email */}
                    <div className="mb-3">
                      <label htmlFor="registro-email" className="form-label fw-medium small">
                        Correo electrónico <span className="text-danger">*</span>
                      </label>
                      <div className="input-group">
                        <span className="input-group-text"><i className="bi bi-envelope" /></span>
                        <input
                          id="registro-email"
                          type="email"
                          className={fieldClass('email', validEmail(reg.email))}
                          placeholder="tu@email.com"
                          value={reg.email}
                          onBlur={() => touch('email')}
                          onChange={onChange('email')}
                          autoComplete="email"
                        />
                        <div className="invalid-feedback">
                          {reg.email ? 'El correo no tiene un formato válido.' : 'El correo es requerido.'}
                        </div>
                      </div>
                    </div>

                    {/* Teléfono */}
                    <div className="mb-3">
                      <label htmlFor="registro-telefono" className="form-label fw-medium small">
                        Teléfono <span className="text-muted fw-normal">(opcional)</span>
                      </label>
                      <div className="input-group">
                        <span className="input-group-text"><i className="bi bi-telephone" /></span>
                        <input
                          id="registro-telefono"
                          type="tel"
                          className={fieldClass('telefono', validTel(reg.telefono))}
                          placeholder="+573001234567"
                          value={reg.telefono}
                          onBlur={() => { if (reg.telefono) touch('telefono'); }}
                          onChange={onChange('telefono')}
                        />
                        <div className="invalid-feedback">Formato inválido. Ej: +573001234567</div>
                      </div>
                    </div>

                    {/* Contraseña */}
                    <div className="mb-3">
                      <label htmlFor="registro-password" className="form-label fw-medium small">
                        Contraseña <span className="text-danger">*</span>
                      </label>
                      <div className="input-group">
                        <span className="input-group-text"><i className="bi bi-lock" /></span>
                        <input
                          id="registro-password"
                          type={showRegPass ? 'text' : 'password'}
                          className={fieldClass('password', validPw(reg.password))}
                          placeholder="Crea una contraseña segura"
                          value={reg.password}
                          onBlur={() => touch('password')}
                          onChange={onChange('password')}
                          autoComplete="new-password"
                        />
                        <button
                          type="button"
                          className="input-group-text bg-white border-start-0"
                          onClick={() => setShowRegPass(v => !v)}
                          tabIndex={-1}
                        >
                          <i className={`bi ${showRegPass ? 'bi-eye-slash' : 'bi-eye'}`} />
                        </button>
                      </div>
                      <PasswordChecklist password={reg.password} />
                    </div>

                    {/* Confirmar contraseña */}
                    <div className="mb-4">
                      <label htmlFor="registro-confirmar" className="form-label fw-medium small">
                        Confirmar contraseña <span className="text-danger">*</span>
                      </label>
                      <div className="input-group">
                        <span className="input-group-text"><i className="bi bi-lock-fill" /></span>
                        <input
                          id="registro-confirmar"
                          type={showConfPass ? 'text' : 'password'}
                          className={fieldClass('confirmar', confirmar.length > 0 && confirmar === reg.password)}
                          placeholder="Repite tu contraseña"
                          value={confirmar}
                          onBlur={() => { if (confirmar) touch('confirmar'); }}
                          onChange={e => {
                            setConfirmar(e.target.value);
                            if (regTouched.confirmar) touch('confirmar');
                          }}
                          autoComplete="new-password"
                        />
                        <button
                          type="button"
                          className="input-group-text bg-white border-start-0"
                          onClick={() => setShowConfPass(v => !v)}
                          tabIndex={-1}
                        >
                          <i className={`bi ${showConfPass ? 'bi-eye-slash' : 'bi-eye'}`} />
                        </button>
                        <div className="invalid-feedback">
                          {!confirmar ? 'Confirma tu contraseña.' : 'Las contraseñas no coinciden.'}
                        </div>
                        {regTouched.confirmar && confirmar && confirmar === reg.password && (
                          <div className="valid-feedback">Las contraseñas coinciden.</div>
                        )}
                      </div>
                    </div>

                    <button
                      type="submit"
                      className="btn btn-ss-dark w-100 py-2 fw-semibold"
                      disabled={loading}
                    >
                      {loading
                        ? <><span className="spinner-border spinner-border-sm me-2" />Registrando...</>
                        : <><i className="bi bi-person-plus me-2" />Crear cuenta</>
                      }
                    </button>
                  </form>
                </>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
