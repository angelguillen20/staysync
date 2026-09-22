import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { login as loginService, register as registerSvc } from '../services/authService';
import AlertMessage from '../components/common/AlertMessage';

const REDIRECT = { ADMIN: '/recepcion', RECEPCIONISTA: '/recepcion', HUESPED: '/huesped' };

const PW_RULES = [
  { id: 'len', label: 'Al menos 8 caracteres',  test: pw => pw.length >= 8 },
  { id: 'up',  label: 'Una letra mayúscula',     test: pw => /[A-Z]/.test(pw) },
  { id: 'low', label: 'Una letra minúscula',     test: pw => /[a-z]/.test(pw) },
  { id: 'num', label: 'Un número (0–9)',         test: pw => /[0-9]/.test(pw) },
  { id: 'sym', label: 'Un símbolo (@$!%*?&)',   test: pw => /[@$!%*?&]/.test(pw) },
];

function validEmail(e) { return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e); }
function validTel(t)   { return !t || /^[+]?[0-9]{7,15}$/.test(t); }
function validPw(pw)   { return PW_RULES.every(r => r.test(pw)); }

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
  const navigate  = useNavigate();
  const location  = useLocation();
  const from      = location.state?.from?.pathname;

  const touch    = field => setRegTouched(p => ({ ...p, [field]: true }));
  const onChange  = field => e => {
    setReg(p => ({ ...p, [field]: e.target.value }));
    if (regTouched[field]) touch(field);
  };

  const fieldClass = (field, ok) =>
    `form-control${regTouched[field] ? (ok ? ' is-valid' : ' is-invalid') : ''}`;

  /* ── Login ───────────────────────────────────────────── */
  const handleLogin = async e => {
    e.preventDefault();
    setError(''); setLoading(true);
    try {
      const data = await loginService(form.email, form.password);
      login(data);
      navigate(from ?? REDIRECT[data.rol] ?? '/huesped', { replace: true });
    } catch (err) {
      setError(err.response?.data?.message ?? 'Credenciales incorrectas. Intente nuevamente.');
    } finally {
      setLoading(false);
    }
  };

  /* ── Register ────────────────────────────────────────── */
  const handleRegister = async e => {
    e.preventDefault();
    setRegTouched({ nombre: true, apellido: true, email: true, telefono: true, password: true, confirmar: true });

    const errors = [];
    if (!reg.nombre.trim())        errors.push('nombre');
    if (!reg.apellido.trim())      errors.push('apellido');
    if (!validEmail(reg.email))    errors.push('email');
    if (!validTel(reg.telefono))   errors.push('telefono');
    if (!validPw(reg.password))    errors.push('password');
    if (confirmar !== reg.password) errors.push('confirmar');

    if (errors.length > 0) {
      setRegErr('Corrige los campos marcados en rojo antes de continuar.');
      return;
    }

    setRegErr(''); setLoading(true);
    try {
      await registerSvc(reg);
      setRegOk(true);
      setTimeout(() => { setTab('login'); setRegOk(false); }, 2500);
    } catch (err) {
      setRegErr(err.response?.data?.message ?? 'No se pudo registrar. Intente más tarde.');
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
        <div className="brand-logo mb-4">🏨</div>
        <h1>StaySync</h1>
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
            <span style={{ fontSize: '3rem' }}>🏨</span>
            <h2 className="fw-bold" style={{ color: 'var(--ss-dark)' }}>StaySync</h2>
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
              <AlertMessage message={error} onClose={() => setError('')} />

              <form onSubmit={handleLogin} noValidate>
                <div className="mb-3">
                  <label className="form-label fw-medium small">Correo electrónico</label>
                  <div className="input-group">
                    <span className="input-group-text"><i className="bi bi-envelope" /></span>
                    <input
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
                  <label className="form-label fw-medium small">Contraseña</label>
                  <div className="input-group">
                    <span className="input-group-text"><i className="bi bi-lock" /></span>
                    <input
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

              <div className="mt-4 p-3 rounded" style={{ background: 'rgba(239,193,67,0.1)', border: '1px solid rgba(239,193,67,0.3)' }}>
                <p className="mb-1 fw-semibold small" style={{ color: 'var(--ss-dark)' }}>
                  <i className="bi bi-info-circle me-1" />Credenciales de demo
                </p>
                <small className="text-muted">
                  Admin: admin@staysync.com / Admin@1234<br />
                  Recepción: recep@staysync.com / Recep@1234<br />
                  Huésped: huesped@staysync.com / Huesped@1234
                </small>
              </div>
            </>
          )}

          {/* ══ REGISTRO ══ */}
          {tab === 'register' && (
            <>
              <h3 className="fw-bold mb-1">Crear cuenta</h3>
              <p className="text-muted small mb-4">Registro de huéspedes · Todos los campos marcados con <span className="text-danger">*</span> son obligatorios</p>

              {regOk ? (
                <div className="alert alert-success text-center">
                  <i className="bi bi-check-circle-fill me-2" />
                  ¡Registro exitoso! Redirigiendo al login...
                </div>
              ) : (
                <>
                  <AlertMessage message={regErr} onClose={() => setRegErr('')} />

                  <form onSubmit={handleRegister} noValidate>

                    {/* Nombre / Apellido */}
                    <div className="row g-3 mb-3">
                      <div className="col-6">
                        <label className="form-label fw-medium small">
                          Nombre <span className="text-danger">*</span>
                        </label>
                        <input
                          className={fieldClass('nombre', reg.nombre.trim().length > 0)}
                          placeholder="Juan"
                          value={reg.nombre}
                          onBlur={() => touch('nombre')}
                          onChange={onChange('nombre')}
                        />
                        <div className="invalid-feedback">El nombre es requerido.</div>
                      </div>
                      <div className="col-6">
                        <label className="form-label fw-medium small">
                          Apellido <span className="text-danger">*</span>
                        </label>
                        <input
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
                      <label className="form-label fw-medium small">
                        Correo electrónico <span className="text-danger">*</span>
                      </label>
                      <div className="input-group">
                        <span className="input-group-text"><i className="bi bi-envelope" /></span>
                        <input
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
                      <label className="form-label fw-medium small">
                        Teléfono <span className="text-muted fw-normal">(opcional)</span>
                      </label>
                      <div className="input-group">
                        <span className="input-group-text"><i className="bi bi-telephone" /></span>
                        <input
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
                      <label className="form-label fw-medium small">
                        Contraseña <span className="text-danger">*</span>
                      </label>
                      <div className="input-group">
                        <span className="input-group-text"><i className="bi bi-lock" /></span>
                        <input
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
                      <label className="form-label fw-medium small">
                        Confirmar contraseña <span className="text-danger">*</span>
                      </label>
                      <div className="input-group">
                        <span className="input-group-text"><i className="bi bi-lock-fill" /></span>
                        <input
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
