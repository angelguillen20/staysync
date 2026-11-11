import { useEffect, useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { useMarca } from '../../context/ThemeContext';
import { actualizarMarca } from '../../services/hotelesService';
import { getHoteles } from '../../services/usuariosService';
import { esColorValido, esLogoValido, problemasDeContraste, variablesDeTema } from '../../theme/hotelTheme';
import AlertMessage from '../../components/common/AlertMessage';
import SubirFoto from '../../components/common/SubirFoto';

const COLORES = [
  { clave: 'primary',    label: 'Color principal',  ayuda: 'Barra superior, botones y títulos', defecto: '#222016' },
  { clave: 'secondary',  label: 'Color de acento',  ayuda: 'Íconos y detalles destacados',      defecto: '#efc143' },
  { clave: 'background', label: 'Fondo suave',      ayuda: 'Cabeceras de tablas y paneles',     defecto: '#fef8e1' },
  { clave: 'text',       label: 'Texto',            ayuda: 'Color del texto principal',         defecto: '#212529' },
];

/** Edición de la marca del hotel: el ADMIN la ve aplicada en vivo antes de guardar. */
export default function MarcaHotelAdmin() {
  const { user } = useAuth();
  const { marca, setMarca } = useMarca();
  const aFormulario = (m, nombreDefecto = '') => ({
    nombre: m?.nombre ?? nombreDefecto,
    logoUrl: m?.logoUrl ?? '',
    theme: Object.fromEntries(COLORES.map((c) => [c.clave, m?.theme?.[c.clave] ?? c.defecto])),
  });
  const [form, setForm] = useState(() => aFormulario(marca, user?.hotelNombre ?? ''));
  // La plataforma puede editar la marca de cualquier hotel; el ADMIN, solo la del suyo
  const esPlataforma = user?.rol === 'SUPER_ADMIN';
  const [hoteles, setHoteles] = useState([]);
  const [hotelEditado, setHotelEditado] = useState(user?.tenantId);

  useEffect(() => {
    if (esPlataforma) getHoteles().then(setHoteles).catch(() => setHoteles([]));
  }, [esPlataforma]);

  const elegirHotel = (tenantId) => {
    setHotelEditado(tenantId);
    setError(''); setExito('');
    setForm(tenantId === user?.tenantId ? aFormulario(marca) : aFormulario(hoteles.find((h) => h.tenantId === tenantId)));
  };
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState('');
  const [exito, setExito] = useState('');

  const logoInvalido = form.logoUrl.trim() !== '' && !esLogoValido(form.logoUrl.trim());
  const coloresInvalidos = Object.values(form.theme).some((c) => !esColorValido(c));
  const vistaPrevia = variablesDeTema(form.theme);
  // Solo avisa: el hotel decide, pero sabe antes de guardar qué partes no se leerían
  const avisosContraste = problemasDeContraste(form.theme);

  const setColor = (clave, valor) => setForm((p) => ({ ...p, theme: { ...p.theme, [clave]: valor } }));

  const guardar = async (e) => {
    e.preventDefault();
    if (logoInvalido || coloresInvalidos || form.nombre.trim().length < 2) {
      setError('Revisa los campos marcados antes de guardar.');
      return;
    }
    setGuardando(true);
    setError('');
    try {
      const guardada = await actualizarMarca(hotelEditado, {
        nombre: form.nombre.trim(),
        logoUrl: form.logoUrl.trim() || null,
        theme: form.theme,
      });
      // Solo se aplica a la sesión si es la marca del propio hotel
      if (hotelEditado === user.tenantId) setMarca(guardada);
      else setHoteles((prev) => prev.map((h) => (h.tenantId === hotelEditado ? { ...h, ...guardada } : h)));
      setExito(`Marca de ${guardada?.nombre ?? form.nombre} guardada. Los huéspedes la verán en su próximo ingreso.`);
      setTimeout(() => setExito(''), 4000);
    } catch (err) {
      setError(err.response?.data?.message ?? 'No se pudo guardar la marca.');
    } finally {
      setGuardando(false);
    }
  };

  return (
    <div className="fade-in-up p-3 p-md-4">
      <div className="mb-4">
        <h2 className="fw-bold mb-0" style={{ color: 'var(--ss-dark)' }}>
          <i className="bi bi-palette me-2" style={{ color: 'var(--ss-gold)' }} />Marca del hotel
        </h2>
        <small className="text-muted">Nombre, logo y colores que ven tus huéspedes y tu equipo</small>
        {esPlataforma && hoteles.length > 0 && (
          <div className="mt-3" style={{ maxWidth: 360 }}>
            <label htmlFor="marca-hotel" className="form-label fw-medium small">Hotel que estás editando</label>
            <select id="marca-hotel" className="form-select" value={hotelEditado} onChange={(e) => elegirHotel(e.target.value)}>
              {hoteles.map((h) => <option key={h.tenantId} value={h.tenantId}>{h.nombre}{h.tenantId === user?.tenantId ? ' (tu hotel)' : ''}</option>)}
            </select>
          </div>
        )}
      </div>

      <AlertMessage message={error} onClose={() => setError('')} />
      <AlertMessage type="success" message={exito} onClose={() => setExito('')} />

      <div className="row g-4">
        <form className="col-lg-6" onSubmit={guardar}>
          <div className="card border-0 shadow-sm p-4" style={{ borderRadius: 12 }}>
            <label htmlFor="marca-nombre" className="form-label fw-medium small">Nombre del hotel</label>
            <input
              id="marca-nombre"
              className="form-control mb-3"
              value={form.nombre}
              maxLength={150}
              onChange={(e) => setForm((p) => ({ ...p, nombre: e.target.value }))}
              disabled={guardando}
            />

            <SubirFoto label="Logo (opcional)" carpeta="hoteles" value={form.logoUrl}
                       onChange={(url) => setForm((p) => ({ ...p, logoUrl: url }))} />
            {logoInvalido && <div className="invalid-feedback d-block">El logo debe ser una URL que empiece con https://</div>}

            <div className="row g-3 mt-2">
              {COLORES.map((c) => (
                <div key={c.clave} className="col-sm-6">
                  <label htmlFor={`color-${c.clave}`} className="form-label fw-medium small mb-1">{c.label}</label>
                  <div className="d-flex gap-2 align-items-center">
                    <input
                      id={`color-${c.clave}`}
                      type="color"
                      className="form-control form-control-color"
                      value={esColorValido(form.theme[c.clave]) ? form.theme[c.clave] : c.defecto}
                      onChange={(e) => setColor(c.clave, e.target.value)}
                      disabled={guardando}
                    />
                    <input
                      className={`form-control form-control-sm font-monospace ${esColorValido(form.theme[c.clave]) ? '' : 'is-invalid'}`}
                      value={form.theme[c.clave]}
                      maxLength={7}
                      onChange={(e) => setColor(c.clave, e.target.value)}
                      aria-label={`${c.label} en hexadecimal`}
                      disabled={guardando}
                    />
                  </div>
                  <small className="text-muted">{c.ayuda}</small>
                </div>
              ))}
            </div>

            {avisosContraste.length > 0 && (
              <div className="alert alert-warning small mt-3 mb-0" role="status">
                <i className="bi bi-exclamation-triangle me-1" />
                <strong>Contraste bajo</strong>
                <ul className="mb-0 mt-1 ps-3">
                  {avisosContraste.map((a) => <li key={a}>{a}</li>)}
                </ul>
              </div>
            )}

            <button type="submit" className="btn btn-ss-dark fw-semibold mt-4" disabled={guardando}>
              {guardando
                ? <><span className="spinner-border spinner-border-sm me-2" />Guardando...</>
                : <><i className="bi bi-check-lg me-1" />Guardar marca</>}
            </button>
          </div>
        </form>

        {/* Vista previa: aplica las variables solo dentro de este bloque */}
        <div className="col-lg-6">
          <div className="card border-0 shadow-sm overflow-hidden" style={{ borderRadius: 12, ...vistaPrevia }}>
            <div className="p-3 d-flex align-items-center gap-2" style={{ background: 'var(--ss-dark)', color: 'var(--ss-gold)' }}>
              {esLogoValido(form.logoUrl.trim())
                ? <img src={form.logoUrl.trim()} alt="" style={{ maxHeight: 28 }} />
                : <i className="bi bi-building" />}
              <strong>{form.nombre || 'Tu hotel'}</strong>
            </div>
            <div className="p-4" style={{ background: 'var(--ss-cream)', color: 'var(--ss-text, #212529)' }}>
              <h5 className="fw-bold" style={{ color: 'var(--ss-dark)' }}>Vista previa</h5>
              <p className="small mb-3">Así se verán los títulos, el texto y los botones del portal.</p>
              <span className="badge me-2" style={{ background: 'var(--ss-gold)', color: 'var(--ss-dark)' }}>Acento</span>
              <button type="button" className="btn btn-sm" style={{ background: 'var(--ss-dark)', color: '#fff' }}>
                Botón principal
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
