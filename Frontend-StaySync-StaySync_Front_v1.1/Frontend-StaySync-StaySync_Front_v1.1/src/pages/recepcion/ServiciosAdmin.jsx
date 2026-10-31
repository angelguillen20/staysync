import { useCallback, useEffect, useState } from 'react';
import { getCatalogo, getCategorias, crearServicio, actualizarServicio } from '../../services/serviciosService';
import AlertMessage from '../../components/common/AlertMessage';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import Foto from '../../components/common/Foto';
import Modal from '../../components/common/Modal';
import SubirFoto from '../../components/common/SubirFoto';

const clp = (v) => `$${Number(v ?? 0).toLocaleString('es-CL')}`;
const VACIO = { nombre: '', categoriaId: '', precio: '', descripcion: '', imagenUrl: '', disponible: true, requiereReserva: false };

function ModalServicio({ servicio, categorias, onClose, onSaved }) {
  const [form, setForm] = useState(servicio
    ? { ...VACIO, ...servicio, categoriaId: String(servicio.categoriaId ?? ''), imagenUrl: servicio.imagenUrl ?? '', descripcion: servicio.descripcion ?? '' }
    : { ...VACIO, categoriaId: String(categorias[0]?.id ?? '') });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const cambiar = (e) => {
    const { name, value, type, checked } = e.target;
    setForm(p => ({ ...p, [name]: type === 'checkbox' ? checked : value }));
  };

  const guardar = async (e) => {
    e.preventDefault();
    setSaving(true); setError('');
    const body = { ...form, categoriaId: Number(form.categoriaId), precio: Number(form.precio), imagenUrl: form.imagenUrl || null };
    try {
      onSaved(servicio ? await actualizarServicio(servicio.id, body) : await crearServicio(body));
      onClose();
    } catch (err) {
      const r = err.response?.data;
      setError(r?.validationErrors ? Object.values(r.validationErrors).join(' ') : r?.message ?? 'No se pudo guardar el servicio.');
      setSaving(false);
    }
  };

  const pie = (
    <>
      <button type="button" className="btn btn-outline-secondary" onClick={onClose} disabled={saving}>Cancelar</button>
      <button type="submit" form="form-servicio" className="btn btn-ss-dark" disabled={saving}>
        {saving ? <><span className="spinner-border spinner-border-sm me-1" />Guardando…</> : servicio ? 'Guardar cambios' : 'Agregar servicio'}
      </button>
    </>
  );

  return (
    <Modal titulo={servicio ? `Editar ${servicio.nombre}` : 'Agregar servicio'} onClose={onClose} pie={pie} ocupado={saving} tamano="modal-lg">
      <AlertMessage message={error} onClose={() => setError('')} />
      <form id="form-servicio" onSubmit={guardar} className="row g-3">
        <div className="col-md-7">
          <label htmlFor="srv-nombre" className="form-label small fw-medium">Nombre</label>
          <input id="srv-nombre" name="nombre" className="form-control" required maxLength={150}
                 placeholder="Traslado al aeropuerto" value={form.nombre} onChange={cambiar} />
        </div>
        <div className="col-md-5">
          <label htmlFor="srv-cat" className="form-label small fw-medium">Categoría</label>
          <select id="srv-cat" name="categoriaId" className="form-select" required value={form.categoriaId} onChange={cambiar}>
            {categorias.map(c => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>
        </div>
        <div className="col-md-4">
          <label htmlFor="srv-precio" className="form-label small fw-medium">Precio (CLP)</label>
          <input id="srv-precio" name="precio" type="number" min="0" step="100" className="form-control" required
                 value={form.precio} onChange={cambiar} />
        </div>
        <div className="col-md-8 d-flex flex-wrap gap-4 align-items-end">
          <div className="form-check form-switch">
            <input id="srv-disp" name="disponible" type="checkbox" className="form-check-input" checked={form.disponible} onChange={cambiar} />
            <label htmlFor="srv-disp" className="form-check-label">Visible para los huéspedes</label>
          </div>
          <div className="form-check form-switch">
            <input id="srv-res" name="requiereReserva" type="checkbox" className="form-check-input" checked={form.requiereReserva} onChange={cambiar} />
            <label htmlFor="srv-res" className="form-check-label">Solo para huéspedes alojados</label>
          </div>
        </div>
        <div className="col-12">
          <label htmlFor="srv-desc" className="form-label small fw-medium">Descripción</label>
          <textarea id="srv-desc" name="descripcion" rows={2} maxLength={1000} className="form-control"
                    placeholder="Qué incluye, horarios, condiciones" value={form.descripcion} onChange={cambiar} />
        </div>
        <div className="col-12">
          <SubirFoto label="Foto del servicio" carpeta="servicios" value={form.imagenUrl}
                     onChange={url => setForm(p => ({ ...p, imagenUrl: url }))} />
        </div>
      </form>
    </Modal>
  );
}

export default function ServiciosAdmin() {
  const [servicios, setServicios] = useState([]);
  const [categorias, setCategorias] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [editando, setEditando] = useState(null); // null: cerrado, {}: nuevo, servicio: editar

  const cargar = useCallback(() => {
    setLoading(true);
    Promise.all([getCatalogo(), getCategorias()])
      .then(([s, c]) => { setServicios(s); setCategorias(c); })
      .catch(err => setError(err.response?.data?.message ?? 'No se pudo cargar el catálogo.'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { cargar(); }, [cargar]);

  const guardado = (s) => {
    setServicios(prev => prev.some(x => x.id === s.id) ? prev.map(x => x.id === s.id ? s : x) : [...prev, s]);
    setSuccess(`${s.nombre} guardado.`);
  };

  return (
    <div className="fade-in-up p-3 p-md-4">
      <div className="d-flex flex-wrap justify-content-between align-items-end gap-3 mb-4">
        <div>
          <h2 className="mb-1">Servicios del hotel</h2>
          <p className="text-muted mb-0">Lo que tus huéspedes pueden pedir durante su estadía. Se cobra en su cuenta.</p>
        </div>
        <button className="btn btn-ss-dark" onClick={() => setEditando({})} disabled={categorias.length === 0}>
          <i className="bi bi-plus-lg me-1" aria-hidden="true" />Agregar servicio
        </button>
      </div>

      <AlertMessage message={error} onClose={() => setError('')} />
      <AlertMessage type="success" message={success} onClose={() => setSuccess('')} />
      {loading && <LoadingSpinner message="Cargando catálogo…" />}

      {!loading && servicios.length === 0 && !error && (
        <div className="card shadow-sm"><div className="card-body text-center py-5">
          <p className="mb-3">Aún no ofreces servicios. Empieza por los que más se piden: desayuno, traslado o late check-out.</p>
          <button className="btn btn-ss-dark" onClick={() => setEditando({})}>Agregar el primero</button>
        </div></div>
      )}

      <div className="row g-3">
        {servicios.map(s => (
          <div key={s.id} className="col-sm-6 col-lg-4 col-xl-3">
            <div className="card shadow-sm h-100" style={{ opacity: s.disponible ? 1 : 0.65 }}>
              <Foto src={s.imagenUrl} alt={s.nombre} />
              <div className="card-body d-flex flex-column">
                <div className="d-flex justify-content-between gap-2">
                  <h5 className="mb-1">{s.nombre}</h5>
                  <span className="fw-semibold text-nowrap">{clp(s.precio)}</span>
                </div>
                <small className="text-muted mb-2">{s.categoriaNombre}{s.disponible ? '' : ', oculto a los huéspedes'}</small>
                {s.descripcion && <p className="small mb-3">{s.descripcion}</p>}
                <button className="btn btn-sm btn-outline-secondary mt-auto align-self-start" onClick={() => setEditando(s)}>Editar</button>
              </div>
            </div>
          </div>
        ))}
      </div>

      {editando && (
        <ModalServicio servicio={editando.id ? editando : null} categorias={categorias}
                       onClose={() => setEditando(null)} onSaved={guardado} />
      )}
    </div>
  );
}
