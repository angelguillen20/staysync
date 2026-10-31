import { useState } from 'react';
import { registrarHuesped } from '../../services/usuariosService';
import Modal from '../common/Modal';
import AlertMessage from '../common/AlertMessage';

const VACIO = { nombre: '', apellido: '', email: '', telefono: '' };

/*
 * Huésped que llega sin cuenta. Se crea con una contraseña temporal que se muestra una sola
 * vez para entregársela; el huésped la cambia desde su perfil.
 */
export default function ModalHuespedNuevo({ onClose, onCreated }) {
  const [form,    setForm]    = useState(VACIO);
  const [saving,  setSaving]  = useState(false);
  const [error,   setError]   = useState('');
  const [creado,  setCreado]  = useState(null);
  const [copiado, setCopiado] = useState(false);

  const cambiar = (e) => setForm(p => ({ ...p, [e.target.name]: e.target.value }));

  const registrar = async (e) => {
    e.preventDefault();
    setSaving(true); setError('');
    try {
      setCreado(await registrarHuesped(form));
    } catch (err) {
      const r = err.response?.data;
      setError(r?.validationErrors ? Object.values(r.validationErrors).join(' ') : r?.message ?? 'No se pudo registrar al huésped.');
    } finally {
      setSaving(false);
    }
  };

  const copiar = async () => {
    try { await navigator.clipboard.writeText(creado.passwordTemporal); setCopiado(true); } catch { setCopiado(false); }
  };

  const terminar = () => { onCreated?.(creado.usuario); onClose(); };

  if (creado) {
    return (
      <Modal titulo="Huésped registrado" onClose={terminar}
             pie={<button type="button" className="btn btn-ss-dark" onClick={terminar}>Listo</button>}>
        <p>
          {creado.usuario?.nombre} {creado.usuario?.apellido} ya puede reservar. Entrégale su contraseña temporal;
          no se volverá a mostrar.
        </p>
        <div className="d-flex align-items-center gap-2 p-3 rounded" style={{ background: 'var(--ss-cream)' }}>
          <code className="fs-5 flex-grow-1" style={{ color: 'var(--ss-dark)' }}>{creado.passwordTemporal}</code>
          <button type="button" className="btn btn-sm btn-outline-secondary" onClick={copiar}>
            {copiado ? 'Copiada' : 'Copiar'}
          </button>
        </div>
        <small className="text-muted d-block mt-2">Inicia sesión con {creado.usuario?.email} y la cambia en Mi perfil.</small>
      </Modal>
    );
  }

  const pie = (
    <>
      <button type="button" className="btn btn-outline-secondary" onClick={onClose} disabled={saving}>Cancelar</button>
      <button type="submit" form="form-huesped" className="btn btn-ss-dark" disabled={saving}>
        {saving ? <><span className="spinner-border spinner-border-sm me-1" />Registrando…</> : 'Registrar huésped'}
      </button>
    </>
  );

  return (
    <Modal titulo="Registrar huésped" onClose={onClose} pie={pie} ocupado={saving}>
      <AlertMessage message={error} onClose={() => setError('')} />
      <form id="form-huesped" onSubmit={registrar} className="row g-3">
        <div className="col-sm-6">
          <label htmlFor="hn-nombre" className="form-label small fw-medium">Nombre</label>
          <input id="hn-nombre" name="nombre" className="form-control" required minLength={2} value={form.nombre} onChange={cambiar} autoComplete="off" />
        </div>
        <div className="col-sm-6">
          <label htmlFor="hn-apellido" className="form-label small fw-medium">Apellido</label>
          <input id="hn-apellido" name="apellido" className="form-control" required minLength={2} value={form.apellido} onChange={cambiar} autoComplete="off" />
        </div>
        <div className="col-sm-7">
          <label htmlFor="hn-email" className="form-label small fw-medium">Email</label>
          <input id="hn-email" name="email" type="email" className="form-control" required value={form.email} onChange={cambiar} autoComplete="off" />
        </div>
        <div className="col-sm-5">
          <label htmlFor="hn-tel" className="form-label small fw-medium">Teléfono (opcional)</label>
          <input id="hn-tel" name="telefono" type="tel" className="form-control" placeholder="+56912345678" value={form.telefono} onChange={cambiar} />
        </div>
      </form>
    </Modal>
  );
}
