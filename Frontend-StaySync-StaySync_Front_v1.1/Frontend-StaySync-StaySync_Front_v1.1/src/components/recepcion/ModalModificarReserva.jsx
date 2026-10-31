import { useEffect, useState } from 'react';
import { getHabitaciones } from '../../services/habitacionesService';
import { modificarReserva } from '../../services/reservasService';
import Modal from '../common/Modal';
import AlertMessage from '../common/AlertMessage';
import { hoyHotel } from '../../utils/fechas';

const NO_RESERVABLE = ['MANTENIMIENTO', 'FUERA_DE_SERVICIO'];

/* Cambiar fechas y/o habitación de una reserva pendiente o confirmada. */
export default function ModalModificarReserva({ reserva, onClose, onSaved }) {
  const [form, setForm] = useState({
    fechaEntrada: reserva.fechaEntrada, fechaSalida: reserva.fechaSalida, habitacionId: String(reserva.habitacionId),
  });
  const [rooms,  setRooms]  = useState([]);
  const [saving, setSaving] = useState(false);
  const [error,  setError]  = useState('');

  useEffect(() => {
    getHabitaciones().then(d => setRooms(d.filter(h => !NO_RESERVABLE.includes(h.estado)))).catch(() => setRooms([]));
  }, []);

  const cambiar = (e) => setForm(p => ({ ...p, [e.target.name]: e.target.value }));

  const guardar = async (e) => {
    e.preventDefault();
    setSaving(true); setError('');
    try {
      onSaved(await modificarReserva(reserva.id, { ...form, habitacionId: Number(form.habitacionId) }));
      onClose();
    } catch (err) {
      setError(err.response?.data?.message ?? 'No se pudo modificar la reserva.');
      setSaving(false);
    }
  };

  const pie = (
    <>
      <button type="button" className="btn btn-outline-secondary" onClick={onClose} disabled={saving}>Cancelar</button>
      <button type="submit" form="form-modificar" className="btn btn-ss-dark" disabled={saving}>
        {saving ? <><span className="spinner-border spinner-border-sm me-1" />Guardando…</> : 'Guardar cambios'}
      </button>
    </>
  );

  return (
    <Modal titulo={`Modificar reserva ${reserva.codigo ?? `#${reserva.id}`}`} onClose={onClose} pie={pie} ocupado={saving}>
      <AlertMessage message={error} onClose={() => setError('')} />
      <form id="form-modificar" onSubmit={guardar} className="row g-3">
        <div className="col-6">
          <label htmlFor="mod-entrada" className="form-label small fw-medium">Entrada</label>
          <input id="mod-entrada" type="date" name="fechaEntrada" className="form-control" required
                 min={hoyHotel()} value={form.fechaEntrada} onChange={cambiar} />
        </div>
        <div className="col-6">
          <label htmlFor="mod-salida" className="form-label small fw-medium">Salida</label>
          <input id="mod-salida" type="date" name="fechaSalida" className="form-control" required
                 min={form.fechaEntrada || hoyHotel()} value={form.fechaSalida} onChange={cambiar} />
        </div>
        <div className="col-12">
          <label htmlFor="mod-hab" className="form-label small fw-medium">Habitación</label>
          <select id="mod-hab" name="habitacionId" className="form-select" value={form.habitacionId} onChange={cambiar}>
            {rooms.map(h => (
              <option key={h.id} value={h.id}>
                Hab. {h.numero} — {h.tipoNombre ?? 'Estándar'} — ${h.precioPorNoche?.toLocaleString('es-CL')}/noche
              </option>
            ))}
          </select>
        </div>
        <p className="col-12 small text-muted mb-0">
          El precio se recalcula con la tarifa de la habitación. Si ya estaba pagada, la diferencia queda en la cuenta del huésped.
        </p>
      </form>
    </Modal>
  );
}
