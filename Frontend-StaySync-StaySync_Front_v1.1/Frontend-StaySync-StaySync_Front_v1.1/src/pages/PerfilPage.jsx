import { useState, useEffect } from 'react';
import { useAuth }        from '../context/AuthContext';
import { getPerfilPropio, updatePerfilPropio } from '../services/usuariosService';
import AlertMessage  from '../components/common/AlertMessage';
import LoadingSpinner from '../components/common/LoadingSpinner';

const ROL_LABEL = {
  ADMIN:         'Administrador',
  RECEPCIONISTA: 'Recepcionista',
  HUESPED:       'Huésped',
};

function initials(nombre, apellido) {
  return `${nombre?.[0] ?? ''}${apellido?.[0] ?? ''}`.toUpperCase() || '?';
}

export default function PerfilPage() {
  const { user, updateUser } = useAuth();

  const [profile,   setProfile]   = useState({ nombre: '', apellido: '', telefono: '' });
  const [form,      setForm]      = useState({ nombre: '', apellido: '', telefono: '' });
  const [password,  setPassword]  = useState('');
  const [showPass,  setShowPass]  = useState(false);
  const [editing,   setEditing]   = useState(false);
  const [loading,  setLoading]  = useState(true);
  const [saving,   setSaving]   = useState(false);
  const [error,    setError]    = useState('');
  const [success,  setSuccess]  = useState('');

  useEffect(() => {
    if (!user) { setLoading(false); return; }
    getPerfilPropio()
      .then(data => {
        const p = { nombre: data.nombre ?? '', apellido: data.apellido ?? '', telefono: data.telefono ?? '' };
        setProfile(p);
        setForm(p);
      })
      .catch(() => {
        const parts    = (user.nombreCompleto ?? '').split(' ');
        const nombre   = parts[0] ?? '';
        const apellido = parts.slice(1).join(' ');
        const p = { nombre, apellido, telefono: '' };
        setProfile(p);
        setForm(p);
      })
      .finally(() => setLoading(false));
  }, [user?.userId]);

  const handleEdit = () => {
    setForm({ ...profile });
    setPassword('');
    setShowPass(false);
    setError('');
    setSuccess('');
    setEditing(true);
  };

  const handleCancel = () => {
    setForm({ ...profile });
    setPassword('');
    setShowPass(false);
    setError('');
    setEditing(false);
  };

  const handleSubmit = async e => {
    e.preventDefault();
    if (!password.trim()) {
      setError('Debes ingresar tu contraseña actual para confirmar los cambios.');
      return;
    }
    setSaving(true); setError('');
    try {
      const updated = await updatePerfilPropio(user.email, password, form);
      const saved = {
        nombre:   updated.nombre   ?? form.nombre,
        apellido: updated.apellido ?? form.apellido,
        telefono: updated.telefono ?? form.telefono,
      };
      setProfile(saved);
      setForm(saved);
      setPassword('');
      updateUser({ nombreCompleto: `${saved.nombre} ${saved.apellido}`.trim() });
      setSuccess('Perfil actualizado correctamente.');
      setEditing(false);
    } catch (err) {
      setError(err.response?.data?.message ?? 'No se pudo actualizar el perfil. Intente nuevamente.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <LoadingSpinner message="Cargando perfil..." />;

  const fullName = `${profile.nombre} ${profile.apellido}`.trim() || user?.nombreCompleto || '—';

  return (
    <div className="fade-in-up p-3 p-md-4" style={{ maxWidth: 560 }}>
      <h2 className="fw-bold mb-4" style={{ color: 'var(--ss-dark)' }}>
        <i className="bi bi-person-gear me-2" style={{ color: 'var(--ss-gold)' }} />
        Mi Perfil
      </h2>

      <div className="card border-0 shadow-sm">
        {/* ── Avatar + name header ── */}
        <div className="card-body pb-0">
          <div className="d-flex align-items-center gap-3 mb-4">
            <div
              className="d-flex align-items-center justify-content-center rounded-circle fw-bold fs-4 text-white flex-shrink-0"
              style={{ width: 64, height: 64, background: 'var(--ss-dark)', letterSpacing: 1 }}
            >
              {initials(profile.nombre, profile.apellido)}
            </div>
            <div>
              <h5 className="mb-0 fw-bold">{fullName}</h5>
              <span
                className="badge mt-1"
                style={{ background: 'var(--ss-gold)', color: 'var(--ss-dark)', fontWeight: 600 }}
              >
                {ROL_LABEL[user?.rol] ?? user?.rol}
              </span>
            </div>
          </div>
        </div>

        <hr className="my-0" />

        {/* ── Alerts ── */}
        <div className="card-body pb-0">
          <AlertMessage message={error}   onClose={() => setError('')} />
          <AlertMessage type="success" message={success} onClose={() => setSuccess('')} />
        </div>

        {/* ── Read view ── */}
        {!editing && (
          <div className="card-body">
            <InfoRow icon="bi-person"      label="Nombre"   value={profile.nombre   || '—'} />
            <InfoRow icon="bi-person"      label="Apellido" value={profile.apellido || '—'} />
            <InfoRow icon="bi-envelope"    label="Correo"   value={user?.email      || '—'} />
            <InfoRow icon="bi-telephone"   label="Teléfono" value={profile.telefono || '—'} />

            <button
              className="btn btn-ss-dark mt-3 px-4"
              onClick={handleEdit}
            >
              <i className="bi bi-pencil me-2" />
              Editar perfil
            </button>
          </div>
        )}

        {/* ── Edit form ── */}
        {editing && (
          <div className="card-body">
            <form onSubmit={handleSubmit} noValidate>
              <div className="row g-3">
                <div className="col-sm-6">
                  <label className="form-label fw-medium small">Nombre</label>
                  <input
                    className="form-control"
                    value={form.nombre}
                    onChange={e => setForm(p => ({ ...p, nombre: e.target.value }))}
                    required
                    autoFocus
                  />
                </div>
                <div className="col-sm-6">
                  <label className="form-label fw-medium small">Apellido</label>
                  <input
                    className="form-control"
                    value={form.apellido}
                    onChange={e => setForm(p => ({ ...p, apellido: e.target.value }))}
                  />
                </div>
                <div className="col-12">
                  <label className="form-label fw-medium small">Teléfono</label>
                  <input
                    type="tel"
                    className="form-control"
                    placeholder="+573001234567"
                    value={form.telefono}
                    onChange={e => setForm(p => ({ ...p, telefono: e.target.value }))}
                  />
                </div>

                <div className="col-12">
                  <hr className="my-1" />
                  <label className="form-label fw-medium small">
                    Contraseña actual
                    <span className="text-danger ms-1">*</span>
                  </label>
                  <div className="input-group">
                    <span className="input-group-text"><i className="bi bi-lock" /></span>
                    <input
                      type={showPass ? 'text' : 'password'}
                      className="form-control"
                      placeholder="Ingresa tu contraseña para confirmar"
                      value={password}
                      onChange={e => setPassword(e.target.value)}
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
                  <small className="text-muted">Requerida para confirmar los cambios.</small>
                </div>
              </div>

              <div className="d-flex gap-2 mt-4">
                <button type="submit" className="btn btn-ss-dark px-4" disabled={saving}>
                  {saving
                    ? <><span className="spinner-border spinner-border-sm me-2" />Guardando...</>
                    : <><i className="bi bi-check-lg me-2" />Guardar cambios</>
                  }
                </button>
                <button type="button" className="btn btn-outline-secondary px-4" onClick={handleCancel} disabled={saving}>
                  Cancelar
                </button>
              </div>
            </form>
          </div>
        )}
      </div>
    </div>
  );
}

function InfoRow({ icon, label, value }) {
  return (
    <div className="d-flex align-items-center gap-3 py-2 border-bottom">
      <i className={`bi ${icon} text-muted`} style={{ width: 20, textAlign: 'center' }} />
      <span className="text-muted small" style={{ minWidth: 72 }}>{label}</span>
      <span className="fw-medium">{value}</span>
    </div>
  );
}
