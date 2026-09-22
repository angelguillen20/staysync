import { useState, useEffect, useCallback } from 'react';
import { getUsuarios, buscarUsuarios, updateUsuarioAdmin, desactivarUsuarioAdmin } from '../../services/usuariosService';
import { useAuth } from '../../context/AuthContext';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import AlertMessage   from '../../components/common/AlertMessage';

const ROL_META = {
  ADMIN:         { label: 'Administrador', badge: 'bg-dark' },
  RECEPCIONISTA: { label: 'Recepcionista', style: { background: 'var(--ss-gold)', color: 'var(--ss-dark)' } },
  HUESPED:       { label: 'Huésped',       badge: 'bg-light text-dark border' },
};

const PASSWORD_HINT = 'Mín. 8 caracteres, con mayúscula, minúscula, número y carácter especial (@$!%*?&).';

function RolBadge({ rol }) {
  const meta = ROL_META[rol] ?? { label: rol, badge: 'bg-secondary' };
  return meta.style
    ? <span className="badge" style={meta.style}>{meta.label}</span>
    : <span className={`badge ${meta.badge}`}>{meta.label}</span>;
}

function ModalEditarUsuario({ usuario, onClose, onSave, saving, error }) {
  const [form, setForm] = useState({ nombre: usuario.nombre ?? '', apellido: usuario.apellido ?? '', telefono: usuario.telefono ?? '' });
  const [nuevaPassword, setNuevaPassword] = useState('');
  const [showPass, setShowPass] = useState(false);

  const handleSubmit = (e) => {
    e.preventDefault();
    const campos = { ...form };
    if (nuevaPassword.trim()) campos.nuevaPassword = nuevaPassword.trim();
    onSave(campos);
  };

  return (
    <div
      style={{ position: 'fixed', inset: 0, zIndex: 1055, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1rem', background: 'rgba(34,32,22,0.45)' }}
      onClick={(e) => { if (e.target === e.currentTarget && !saving) onClose(); }}
    >
      <div style={{ width: '100%', maxWidth: 460 }}>
        <div className="modal-content border-0" style={{ borderRadius: 14, overflow: 'hidden', boxShadow: '0 8px 40px rgba(34,32,22,0.35)' }}>

          <div className="modal-header" style={{ background: 'var(--ss-dark)', borderBottom: '3px solid var(--ss-gold)', padding: '1.1rem 1.5rem' }}>
            <div>
              <h5 className="modal-title mb-0 fw-bold" style={{ color: '#fff' }}>
                <i className="bi bi-pencil-square me-2" style={{ color: 'var(--ss-gold)' }} />
                Editar usuario
              </h5>
              <small style={{ color: 'rgba(255,255,255,0.65)' }}>{usuario.email}</small>
            </div>
            <button className="btn-close btn-close-white" onClick={onClose} disabled={saving} aria-label="Cerrar" />
          </div>

          <form onSubmit={handleSubmit}>
            <div className="modal-body px-4 py-4" style={{ background: '#fff' }}>
              <div className="row g-3">
                <div className="col-sm-6">
                  <label className="form-label fw-medium small">Nombre</label>
                  <input
                    className="form-control"
                    value={form.nombre}
                    onChange={(e) => setForm((p) => ({ ...p, nombre: e.target.value }))}
                    disabled={saving}
                    autoFocus
                  />
                </div>
                <div className="col-sm-6">
                  <label className="form-label fw-medium small">Apellido</label>
                  <input
                    className="form-control"
                    value={form.apellido}
                    onChange={(e) => setForm((p) => ({ ...p, apellido: e.target.value }))}
                    disabled={saving}
                  />
                </div>
                <div className="col-12">
                  <label className="form-label fw-medium small">Teléfono</label>
                  <input
                    type="tel"
                    className="form-control"
                    placeholder="+573001234567"
                    value={form.telefono}
                    onChange={(e) => setForm((p) => ({ ...p, telefono: e.target.value }))}
                    disabled={saving}
                  />
                </div>

                <div className="col-12">
                  <hr className="my-1" />
                  <label className="form-label fw-medium small">
                    Nueva contraseña <span className="text-muted fw-normal">(opcional)</span>
                  </label>
                  <div className="input-group">
                    <span className="input-group-text"><i className="bi bi-lock" /></span>
                    <input
                      type={showPass ? 'text' : 'password'}
                      className="form-control"
                      placeholder="Dejar en blanco para no cambiarla"
                      value={nuevaPassword}
                      onChange={(e) => setNuevaPassword(e.target.value)}
                      disabled={saving}
                      autoComplete="new-password"
                    />
                    <button
                      type="button"
                      className="input-group-text bg-white border-start-0"
                      onClick={() => setShowPass((v) => !v)}
                      tabIndex={-1}
                    >
                      <i className={`bi ${showPass ? 'bi-eye-slash' : 'bi-eye'}`} />
                    </button>
                  </div>
                  {nuevaPassword && <small className="text-muted">{PASSWORD_HINT}</small>}
                </div>
              </div>

              {error && (
                <div className="alert alert-danger py-2 small mt-3 mb-0" style={{ borderRadius: 8 }}>
                  <i className="bi bi-exclamation-circle-fill me-1" />{error}
                </div>
              )}
            </div>

            <div className="modal-footer px-4 py-3 gap-2" style={{ background: 'var(--ss-cream)', borderTop: '1px solid rgba(34,32,22,0.1)' }}>
              <button type="button" className="btn btn-outline-secondary" onClick={onClose} disabled={saving}>
                Cancelar
              </button>
              <button type="submit" className="btn btn-ss-dark fw-semibold" disabled={saving}>
                {saving
                  ? <><span className="spinner-border spinner-border-sm me-2" />Guardando...</>
                  : <><i className="bi bi-check-lg me-1" />Guardar cambios</>
                }
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}

export default function UsuariosAdmin() {
  const { user: currentUser } = useAuth();
  const [usuarios,   setUsuarios]   = useState([]);
  const [loading,    setLoading]    = useState(true);
  const [error,      setError]      = useState('');
  const [success,    setSuccess]    = useState('');

  const [q,          setQ]          = useState('');
  const [rolFiltro,  setRolFiltro]  = useState('TODOS');
  const [buscando,   setBuscando]   = useState(false); // true = resultados de /buscar (sin paginación)

  const [page,        setPage]        = useState(0);
  const [totalPages,  setTotalPages]  = useState(0);
  const [totalUsuarios, setTotalUsuarios] = useState(0);

  const [editando, setEditando] = useState(null);
  const [saving,   setSaving]   = useState(false);
  const [errorModal, setErrorModal] = useState('');
  const [deactivating, setDeactivating] = useState(null);

  const cargarPagina = useCallback(async (p) => {
    setLoading(true);
    setError('');
    try {
      const data = await getUsuarios({ page: p, size: 10, sort: 'id' });
      setUsuarios(data.content ?? []);
      setTotalPages(data.totalPages ?? 1);
      setTotalUsuarios(data.totalElements ?? data.content?.length ?? 0);
      setBuscando(false);
    } catch {
      setError('Error al cargar los usuarios.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { cargarPagina(page); }, [page, cargarPagina]);

  const handleBuscar = async () => {
    const rol = rolFiltro === 'TODOS' ? '' : rolFiltro;
    if (!q.trim() && !rol) {
      setPage(0);
      cargarPagina(0);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const data = await buscarUsuarios(q.trim(), rol);
      setUsuarios(Array.isArray(data) ? data : []);
      setBuscando(true);
    } catch {
      setError('Error al buscar usuarios.');
    } finally {
      setLoading(false);
    }
  };

  const handleLimpiar = () => {
    setQ('');
    setRolFiltro('TODOS');
    setPage(0);
    cargarPagina(0);
  };

  const handleGuardar = async (campos) => {
    setSaving(true);
    setErrorModal('');
    try {
      const actualizado = await updateUsuarioAdmin(editando.id, campos);
      setUsuarios((prev) => prev.map((u) => (u.id === editando.id ? { ...u, ...actualizado } : u)));
      setEditando(null);
      setSuccess(`Usuario "${actualizado.nombre ?? campos.nombre}" actualizado correctamente.`);
      setTimeout(() => setSuccess(''), 3500);
    } catch (err) {
      setErrorModal(err.response?.data?.message ?? 'No se pudo actualizar el usuario.');
    } finally {
      setSaving(false);
    }
  };

  const handleDesactivar = async (u) => {
    if (!window.confirm(`¿Desactivar la cuenta de ${u.nombre} ${u.apellido}? No podrá iniciar sesión hasta reactivarla.`)) return;
    setDeactivating(u.id);
    setError('');
    try {
      await desactivarUsuarioAdmin(u.id);
      setUsuarios((prev) => prev.map((x) => (x.id === u.id ? { ...x, activo: false } : x)));
      setSuccess(`Cuenta de "${u.nombre} ${u.apellido}" desactivada.`);
      setTimeout(() => setSuccess(''), 3500);
    } catch {
      setError('No se pudo desactivar el usuario.');
    } finally {
      setDeactivating(null);
    }
  };

  return (
    <div className="fade-in-up p-3 p-md-4">

      <div className="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-3">
        <div>
          <h2 className="fw-bold mb-0" style={{ color: 'var(--ss-dark)' }}>
            <i className="bi bi-person-badge me-2" style={{ color: 'var(--ss-gold)' }} />
            Usuarios
          </h2>
          <small className="text-muted">
            Gestión de cuentas del sistema (administradores, recepcionistas y huéspedes)
          </small>
        </div>
      </div>

      <AlertMessage message={error}   onClose={() => setError('')} />
      <AlertMessage type="success" message={success} onClose={() => setSuccess('')} />

      {editando && (
        <ModalEditarUsuario
          usuario={editando}
          saving={saving}
          error={errorModal}
          onClose={() => { setEditando(null); setErrorModal(''); }}
          onSave={handleGuardar}
        />
      )}

      {/* Búsqueda + filtro de rol */}
      <div className="card border-0 shadow-sm mb-3">
        <div className="card-body py-3">
          <div className="row g-2 align-items-center">
            <div className="col-md-6">
              <div className="input-group">
                <span className="input-group-text bg-white border-end-0">
                  <i className="bi bi-search text-muted" style={{ fontSize: '0.85rem' }} />
                </span>
                <input
                  className="form-control border-start-0"
                  placeholder="Buscar por nombre, apellido o email…"
                  value={q}
                  onChange={(e) => setQ(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && handleBuscar()}
                />
              </div>
            </div>
            <div className="col-md-3">
              <select
                className="form-select"
                value={rolFiltro}
                onChange={(e) => { setRolFiltro(e.target.value); }}
              >
                <option value="TODOS">Todos los roles</option>
                <option value="ADMIN">Administrador</option>
                <option value="RECEPCIONISTA">Recepcionista</option>
                <option value="HUESPED">Huésped</option>
              </select>
            </div>
            <div className="col-md-3 d-flex gap-2">
              <button className="btn btn-ss-dark flex-grow-1" onClick={handleBuscar} disabled={loading}>
                <i className="bi bi-search me-1" />Buscar
              </button>
              {(q || rolFiltro !== 'TODOS' || buscando) && (
                <button className="btn btn-outline-secondary" onClick={handleLimpiar} disabled={loading} title="Limpiar filtros">
                  <i className="bi bi-x-lg" />
                </button>
              )}
            </div>
          </div>
        </div>
      </div>

      {loading ? (
        <LoadingSpinner message="Cargando usuarios..." />
      ) : usuarios.length === 0 ? (
        <div className="text-center py-5 text-muted">
          <i className="bi bi-person-x fs-1 d-block mb-2" />
          No se encontraron usuarios{(q || rolFiltro !== 'TODOS') ? ' con esos criterios.' : '.'}
        </div>
      ) : (
        <div className="card border-0 shadow-sm" style={{ borderRadius: 12 }}>
          <div className="table-responsive">
            <table className="table table-hover align-middle mb-0">
              <thead style={{ background: 'var(--ss-cream)' }}>
                <tr>
                  <th className="ps-3 small text-muted fw-semibold">Usuario</th>
                  <th className="small text-muted fw-semibold">Email</th>
                  <th className="small text-muted fw-semibold">Teléfono</th>
                  <th className="small text-muted fw-semibold">Rol</th>
                  <th className="small text-muted fw-semibold">Estado</th>
                  <th className="small text-muted fw-semibold"></th>
                </tr>
              </thead>
              <tbody>
                {usuarios.map((u) => (
                  <tr key={u.id}>
                    <td className="ps-3">
                      <div className="d-flex align-items-center gap-2">
                        <div
                          className="rounded-circle d-flex align-items-center justify-content-center flex-shrink-0 fw-bold"
                          style={{ width: 36, height: 36, background: 'rgba(239,193,67,0.18)', color: 'var(--ss-dark)', fontSize: '0.85rem' }}
                        >
                          {(u.nombre?.[0] ?? '?').toUpperCase()}
                        </div>
                        <div>
                          <div className="fw-semibold" style={{ fontSize: '0.9rem', color: 'var(--ss-dark)' }}>
                            {u.nombre} {u.apellido}
                          </div>
                          <div className="text-muted" style={{ fontSize: '0.75rem' }}>ID #{u.id}</div>
                        </div>
                      </div>
                    </td>
                    <td className="small text-muted">{u.email}</td>
                    <td className="small text-muted">{u.telefono ?? '—'}</td>
                    <td><RolBadge rol={u.rol} /></td>
                    <td>
                      <span className={`badge ${u.activo !== false ? 'bg-success' : 'bg-secondary'}`}>
                        {u.activo !== false ? 'Activo' : 'Inactivo'}
                      </span>
                    </td>
                    <td>
                      <div className="d-flex gap-2">
                        <button className="btn btn-outline-secondary btn-sm" onClick={() => setEditando(u)}>
                          <i className="bi bi-pencil me-1" />Editar
                        </button>
                        {u.activo !== false && u.id !== currentUser?.userId && (
                          <button
                            className="btn btn-outline-danger btn-sm"
                            disabled={deactivating === u.id}
                            onClick={() => handleDesactivar(u)}
                            title="Desactivar cuenta"
                          >
                            {deactivating === u.id
                              ? <span className="spinner-border spinner-border-sm" />
                              : <i className="bi bi-person-dash" />
                            }
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Paginación (solo en modo "todos", no en búsqueda) */}
          {!buscando && totalPages > 1 && (
            <div className="d-flex justify-content-between align-items-center px-3 py-2 border-top" style={{ background: 'var(--ss-cream)', borderRadius: '0 0 12px 12px' }}>
              <small className="text-muted">
                Página {page + 1} de {totalPages} · {totalUsuarios} usuarios en total
              </small>
              <div className="d-flex gap-2">
                <button className="btn btn-sm btn-outline-secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                  <i className="bi bi-chevron-left" />
                </button>
                <button className="btn btn-sm btn-outline-secondary" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>
                  <i className="bi bi-chevron-right" />
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
