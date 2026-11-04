import { useCallback, useEffect, useState } from 'react';
import { getHoteles } from '../../services/usuariosService';
import { crearHotel } from '../../services/hotelesService';
import { esColorValido, esLogoValido, esSlugValido, problemasDeContraste } from '../../theme/hotelTheme';
import AlertMessage from '../../components/common/AlertMessage';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import SubirFoto from '../../components/common/SubirFoto';
import EtiquetaHotel, { olvidarHoteles, useHotelesPlataforma } from '../../components/common/EtiquetaHotel';

const COLORES = [
  { clave: 'primary',    label: 'Principal', defecto: '#222016' },
  { clave: 'secondary',  label: 'Acento',    defecto: '#efc143' },
  { clave: 'background', label: 'Fondo',     defecto: '#fef8e1' },
  { clave: 'text',       label: 'Texto',     defecto: '#212529' },
];
const PASSWORD = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&]).{8,}$/;

/** "Hotel Pacífico Sur" -> "hotel-pacifico-sur": la URL del portal y el identificador del hotel. */
export function slugDe(nombre) {
  return nombre.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 64);
}

const FORM_VACIO = {
  nombre: '', slug: '', logoUrl: '',
  theme: Object.fromEntries(COLORES.map((c) => [c.clave, c.defecto])),
  admin: { nombre: '', apellido: '', email: '', password: '' },
};

const urlPortal = (slug) => `${window.location.origin}/login?hotel=${slug}`;

/** Hoteles de la plataforma: el SUPER_ADMIN los ve y da de alta uno nuevo con su administrador. */
export default function HotelesAdmin() {
  const [hoteles, setHoteles] = useState([]);
  useHotelesPlataforma(true); // colores de las etiquetas
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState(FORM_VACIO);
  const [slugEditado, setSlugEditado] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState('');
  const [creado, setCreado] = useState(null);

  const cargar = useCallback(() => {
    setLoading(true);
    getHoteles()
      .then((data) => setHoteles(Array.isArray(data) ? data : []))
      .catch(() => setError('No se pudieron cargar los hoteles.'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { cargar(); }, [cargar]);

  const setCampo = (campo, valor) => setForm((p) => ({
    ...p,
    [campo]: valor,
    // El slug sigue al nombre hasta que el super admin lo escribe a mano
    ...(campo === 'nombre' && !slugEditado ? { slug: slugDe(valor) } : {}),
  }));
  const setColor = (clave, valor) => setForm((p) => ({ ...p, theme: { ...p.theme, [clave]: valor } }));
  const setAdmin = (campo, valor) => setForm((p) => ({ ...p, admin: { ...p.admin, [campo]: valor } }));

  const logoInvalido = form.logoUrl.trim() !== '' && !esLogoValido(form.logoUrl.trim());
  const slugInvalido = form.slug !== '' && !esSlugValido(form.slug);
  const passwordInvalida = form.admin.password !== '' && !PASSWORD.test(form.admin.password);
  const avisosContraste = problemasDeContraste(form.theme);

  const guardar = async (e) => {
    e.preventDefault();
    const a = form.admin;
    if (form.nombre.trim().length < 2 || !esSlugValido(form.slug) || logoInvalido
        || Object.values(form.theme).some((c) => !esColorValido(c))
        || a.nombre.trim().length < 2 || a.apellido.trim().length < 2 || !a.email.includes('@') || !PASSWORD.test(a.password)) {
      setError('Revisa los campos del formulario antes de crear el hotel.');
      return;
    }
    setGuardando(true);
    setError('');
    try {
      const hotel = await crearHotel({
        nombre: form.nombre.trim(),
        slug: form.slug,
        logoUrl: form.logoUrl.trim() || null,
        theme: form.theme,
        admin: { ...a, nombre: a.nombre.trim(), apellido: a.apellido.trim(), email: a.email.trim() },
      });
      setCreado({ ...hotel, adminEmail: a.email.trim().toLowerCase() });
      olvidarHoteles(); // el hotel nuevo recibe su color en la próxima pantalla
      setForm(FORM_VACIO);
      setSlugEditado(false);
      cargar();
    } catch (err) {
      setError(err.response?.data?.message ?? 'No se pudo crear el hotel.');
    } finally {
      setGuardando(false);
    }
  };

  return (
    <div className="fade-in-up p-3 p-md-4">
      <div className="mb-4">
        <h2 className="fw-bold mb-0" style={{ color: 'var(--ss-dark)' }}>
          <i className="bi bi-buildings me-2" style={{ color: 'var(--ss-gold)' }} />Hoteles
        </h2>
        <small className="text-muted">Hoteles de la plataforma y alta de hoteles nuevos</small>
      </div>

      <AlertMessage message={error} onClose={() => setError('')} />
      {creado && (
        <div className="alert alert-success d-flex justify-content-between align-items-start" role="status">
          <div>
            <strong>{creado.nombre}</strong> quedó creado.
            {' '}Su administrador entra con <strong>{creado.adminEmail}</strong> y la contraseña que definiste.
            <div className="small mt-1">
              Portal para los huéspedes: <a href={urlPortal(creado.slug)}>{urlPortal(creado.slug)}</a>
            </div>
          </div>
          <button type="button" className="btn-close" aria-label="Cerrar" onClick={() => setCreado(null)} />
        </div>
      )}

      <div className="row g-4">
        {/* ── Hoteles existentes ── */}
        <div className="col-lg-5">
          <div className="card border-0 shadow-sm" style={{ borderRadius: 12 }}>
            <div className="card-body">
              <h6 className="fw-bold mb-3">Hoteles activos ({hoteles.length})</h6>
              {loading && <LoadingSpinner message="Cargando hoteles..." />}
              {!loading && hoteles.map((h) => (
                <div key={h.tenantId} className="d-flex align-items-center gap-3 py-2 border-bottom">
                  <div
                    className="rounded d-flex align-items-center justify-content-center flex-shrink-0"
                    style={{ width: 44, height: 44, background: h.theme?.primary ?? '#222016', color: h.theme?.secondary ?? '#efc143' }}
                  >
                    {esLogoValido(h.logoUrl)
                      ? <img src={h.logoUrl} alt="" style={{ maxWidth: 36, maxHeight: 36 }} />
                      : <i className="bi bi-building fs-5" />}
                  </div>
                  <div className="flex-grow-1 overflow-hidden">
                    <EtiquetaHotel tenantId={h.tenantId} nombre={h.nombre} />
                    <a className="small text-truncate d-block" href={urlPortal(h.slug)}>/login?hotel={h.slug}</a>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* ── Nuevo hotel ── */}
        <form className="col-lg-7" onSubmit={guardar} noValidate>
          <div className="card border-0 shadow-sm p-4" style={{ borderRadius: 12 }}>
            <h6 className="fw-bold mb-3"><i className="bi bi-plus-circle me-1" />Nuevo hotel</h6>
            <div className="row g-3">
              <div className="col-md-6">
                <label htmlFor="hotel-nombre" className="form-label small fw-medium">Nombre</label>
                <input id="hotel-nombre" className="form-control" value={form.nombre} maxLength={150}
                       onChange={(e) => setCampo('nombre', e.target.value)} disabled={guardando} />
              </div>
              <div className="col-md-6">
                <label htmlFor="hotel-slug" className="form-label small fw-medium">Portal (slug)</label>
                <input id="hotel-slug" className={`form-control font-monospace ${slugInvalido ? 'is-invalid' : ''}`}
                       value={form.slug} maxLength={64} disabled={guardando}
                       onChange={(e) => { setSlugEditado(true); setCampo('slug', e.target.value.toLowerCase()); }} />
                <small className={slugInvalido ? 'text-danger' : 'text-muted'}>
                  {slugInvalido ? 'Solo minúsculas, números y guiones.' : 'No se puede cambiar después.'}
                </small>
              </div>
              <div className="col-12">
                <SubirFoto label="Logo (opcional)" carpeta="hoteles" value={form.logoUrl}
                           onChange={(url) => setCampo('logoUrl', url)} />
                {logoInvalido && <small className="text-danger">El logo debe ser una URL que empiece con https://</small>}
              </div>
              {COLORES.map((c) => (
                <div key={c.clave} className="col-6 col-md-3">
                  <label htmlFor={`hotel-color-${c.clave}`} className="form-label small fw-medium mb-1">{c.label}</label>
                  <input id={`hotel-color-${c.clave}`} type="color" className="form-control form-control-color w-100"
                         value={esColorValido(form.theme[c.clave]) ? form.theme[c.clave] : c.defecto}
                         onChange={(e) => setColor(c.clave, e.target.value)} disabled={guardando} />
                </div>
              ))}
              {avisosContraste.length > 0 && (
                <div className="col-12">
                  <div className="alert alert-warning small mb-0 py-2" role="status">
                    <i className="bi bi-exclamation-triangle me-1" /><strong>Contraste bajo:</strong> {avisosContraste.join(' ')}
                  </div>
                </div>
              )}

              <div className="col-12 mt-4">
                <h6 className="fw-bold mb-0"><i className="bi bi-person-badge me-1" />Administrador del hotel</h6>
                <small className="text-muted">Gestionará habitaciones, reservas y personal de este hotel.</small>
              </div>
              <div className="col-md-6">
                <label htmlFor="admin-nombre" className="form-label small fw-medium">Nombre</label>
                <input id="admin-nombre" className="form-control" value={form.admin.nombre}
                       onChange={(e) => setAdmin('nombre', e.target.value)} disabled={guardando} />
              </div>
              <div className="col-md-6">
                <label htmlFor="admin-apellido" className="form-label small fw-medium">Apellido</label>
                <input id="admin-apellido" className="form-control" value={form.admin.apellido}
                       onChange={(e) => setAdmin('apellido', e.target.value)} disabled={guardando} />
              </div>
              <div className="col-md-6">
                <label htmlFor="admin-email" className="form-label small fw-medium">Email</label>
                <input id="admin-email" type="email" className="form-control" value={form.admin.email}
                       onChange={(e) => setAdmin('email', e.target.value)} disabled={guardando} />
              </div>
              <div className="col-md-6">
                <label htmlFor="admin-password" className="form-label small fw-medium">Contraseña</label>
                <input id="admin-password" type="password" autoComplete="new-password"
                       className={`form-control ${passwordInvalida ? 'is-invalid' : ''}`} value={form.admin.password}
                       onChange={(e) => setAdmin('password', e.target.value)} disabled={guardando} />
                <small className={passwordInvalida ? 'text-danger' : 'text-muted'}>
                  Mín. 8, con mayúscula, minúscula, número y símbolo (@$!%*?&).
                </small>
              </div>
            </div>
            <button type="submit" className="btn btn-ss-dark fw-semibold mt-4" disabled={guardando}>
              {guardando
                ? <><span className="spinner-border spinner-border-sm me-2" />Creando...</>
                : <><i className="bi bi-check-lg me-1" />Crear hotel</>}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
