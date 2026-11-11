import { useState } from 'react';
import { agregarImagen, eliminarImagen } from '../../services/habitacionesService';
import Modal from '../common/Modal';
import AlertMessage from '../common/AlertMessage';
import SubirFoto from '../common/SubirFoto';

/* Fotos de una habitación: la portada es la que ven los huéspedes al buscar. */
export default function ModalFotosHabitacion({ habitacion, onClose, onChange }) {
  const [hab, setHab] = useState(habitacion);
  const [nueva, setNueva] = useState('');
  const [portada, setPortada] = useState(false);
  const [ocupado, setOcupado] = useState(false);
  const [error, setError] = useState('');

  const aplicar = async (accion) => {
    setOcupado(true); setError('');
    try {
      const actualizada = await accion();
      setHab(actualizada);
      onChange(actualizada);
      return true;
    } catch (err) {
      setError(err.response?.data?.message ?? 'No se pudo actualizar las fotos.');
      return false;
    } finally {
      setOcupado(false);
    }
  };

  const agregar = async () => {
    if (await aplicar(() => agregarImagen(hab.id, { url: nueva, esPrincipal: portada }))) {
      setNueva(''); setPortada(false);
    }
  };

  const fotos = hab.imagenes ?? [];

  return (
    <Modal titulo={`Fotos de la habitación ${hab.numero}`} onClose={onClose} tamano="modal-lg" ocupado={ocupado}
           pie={<button type="button" className="btn btn-ss-dark" onClick={onClose} disabled={ocupado}>Listo</button>}>
      <AlertMessage message={error} onClose={() => setError('')} />

      {fotos.length === 0 && <p className="text-muted">Sin fotos todavía. La primera que agregues será la portada.</p>}
      <div className="row g-2 mb-3">
        {fotos.map(f => (
          <div key={f.id} className="col-6 col-md-4">
            <div className="position-relative">
              <img src={f.url} alt={`Foto de la habitación ${hab.numero}`} className="w-100 rounded"
                   style={{ aspectRatio: '4 / 3', objectFit: 'cover' }} />
              {f.esPrincipal && <span className="badge bg-dark position-absolute top-0 start-0 m-2">Portada</span>}
              <button type="button" className="btn btn-sm btn-light position-absolute top-0 end-0 m-2"
                      aria-label="Quitar foto" disabled={ocupado}
                      onClick={() => aplicar(() => eliminarImagen(hab.id, f.id))}>
                <i className="bi bi-trash3" aria-hidden="true" />
              </button>
            </div>
          </div>
        ))}
      </div>

      {fotos.length < 10 && (
        <>
          <SubirFoto label="Agregar foto" carpeta="habitaciones" value={nueva} onChange={setNueva} />
          <div className="d-flex justify-content-between align-items-center mt-2">
            <div className="form-check">
              <input id="foto-portada" type="checkbox" className="form-check-input" checked={portada}
                     onChange={e => setPortada(e.target.checked)} disabled={fotos.length === 0} />
              <label htmlFor="foto-portada" className="form-check-label small">Usarla de portada</label>
            </div>
            <button type="button" className="btn btn-ss-dark btn-sm" onClick={agregar} disabled={!nueva || ocupado}>
              Agregar a la galería
            </button>
          </div>
        </>
      )}
    </Modal>
  );
}
