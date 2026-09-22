import { useState, useEffect } from 'react';
import { getHabitaciones } from '../../services/habitacionesService';
import { crearReserva }    from '../../services/reservasService';
import AlertMessage        from '../common/AlertMessage';

const EMPTY = {
  habitacionId: '',
  fechaEntrada: '',
  fechaSalida: '',
  numHuespedes: 1,
  nombreContacto: '',
  emailContacto: '',
  telefonoContacto: '',
  observaciones: '',
};

export default function ModalReserva({ show, onClose, onCreated }) {
  const [form,   setForm]   = useState(EMPTY);
  const [rooms,  setRooms]  = useState([]);
  const [saving, setSaving] = useState(false);
  const [error,  setError]  = useState('');

  useEffect(() => {
    if (show) {
      setForm(EMPTY);
      setError('');
      getHabitaciones()
        .then(data => setRooms(data.filter(h => h.estado === 'DISPONIBLE')))
        .catch(() => setRooms([]));
    }
  }, [show]);

  const handleChange = e => {
    const { name, value } = e.target;
    setForm(p => ({ ...p, [name]: value }));
  };

  const handleSubmit = async e => {
    e.preventDefault();
    setError(''); setSaving(true);
    try {
      await crearReserva({ ...form, fuente: 'DIRECTA', numHuespedes: Number(form.numHuespedes) });
      onCreated?.();
      onClose();
    } catch (err) {
      setError(err.response?.data?.message ?? 'No se pudo crear la reserva.');
    } finally {
      setSaving(false);
    }
  };

  if (!show) return null;

  return (
    <div className="modal fade show d-block" tabIndex="-1" style={{ background: 'rgba(0,0,0,0.5)' }}>
      <div className="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
        <div className="modal-content">
          <div className="modal-header" style={{ borderBottom: '2px solid var(--ss-gold)' }}>
            <h5 className="modal-title fw-bold" style={{ color: 'var(--ss-dark)' }}>
              <i className="bi bi-calendar-plus me-2" style={{ color: 'var(--ss-gold)' }} />
              Nueva reserva manual
            </h5>
            <button className="btn-close" onClick={onClose} />
          </div>

          <div className="modal-body">
            <AlertMessage message={error} onClose={() => setError('')} />

            <form id="form-reserva" onSubmit={handleSubmit} noValidate>
              <div className="row g-3">
                {/* Habitación */}
                <div className="col-12">
                  <label className="form-label fw-medium small">Habitación disponible</label>
                  <select
                    className="form-select"
                    name="habitacionId"
                    value={form.habitacionId}
                    onChange={handleChange}
                    required
                  >
                    <option value="">— Selecciona una habitación —</option>
                    {rooms.map(h => (
                      <option key={h.id} value={h.id}>
                        Hab. {h.numero} — {h.tipo} — ${h.precioPorNoche?.toLocaleString()}/noche
                      </option>
                    ))}
                  </select>
                  {rooms.length === 0 && (
                    <small className="text-muted">No hay habitaciones disponibles en este momento.</small>
                  )}
                </div>

                {/* Fechas */}
                <div className="col-md-6">
                  <label className="form-label fw-medium small">Fecha de entrada</label>
                  <input
                    type="date"
                    className="form-control"
                    name="fechaEntrada"
                    value={form.fechaEntrada}
                    onChange={handleChange}
                    min={new Date().toISOString().split('T')[0]}
                    required
                  />
                </div>
                <div className="col-md-6">
                  <label className="form-label fw-medium small">Fecha de salida</label>
                  <input
                    type="date"
                    className="form-control"
                    name="fechaSalida"
                    value={form.fechaSalida}
                    onChange={handleChange}
                    min={form.fechaEntrada || new Date().toISOString().split('T')[0]}
                    required
                  />
                </div>

                {/* N° huéspedes */}
                <div className="col-md-4">
                  <label className="form-label fw-medium small">N° huéspedes</label>
                  <input
                    type="number"
                    className="form-control"
                    name="numHuespedes"
                    value={form.numHuespedes}
                    onChange={handleChange}
                    min="1" max="10"
                    required
                  />
                </div>

                {/* Contacto */}
                <div className="col-md-8">
                  <label className="form-label fw-medium small">Nombre del contacto</label>
                  <input
                    className="form-control"
                    name="nombreContacto"
                    placeholder="Juan García"
                    value={form.nombreContacto}
                    onChange={handleChange}
                    required
                  />
                </div>
                <div className="col-md-6">
                  <label className="form-label fw-medium small">Email de contacto</label>
                  <input
                    type="email"
                    className="form-control"
                    name="emailContacto"
                    placeholder="juan@email.com"
                    value={form.emailContacto}
                    onChange={handleChange}
                  />
                </div>
                <div className="col-md-6">
                  <label className="form-label fw-medium small">Teléfono</label>
                  <input
                    type="tel"
                    className="form-control"
                    name="telefonoContacto"
                    placeholder="+573001234567"
                    value={form.telefonoContacto}
                    onChange={handleChange}
                  />
                </div>
                <div className="col-12">
                  <label className="form-label fw-medium small">Observaciones</label>
                  <textarea
                    className="form-control"
                    name="observaciones"
                    rows={2}
                    placeholder="Solicitudes especiales, alergias, etc."
                    value={form.observaciones}
                    onChange={handleChange}
                  />
                </div>
              </div>
            </form>
          </div>

          <div className="modal-footer">
            <button className="btn btn-outline-secondary" onClick={onClose} disabled={saving}>
              Cancelar
            </button>
            <button
              type="submit"
              form="form-reserva"
              className="btn btn-ss-dark px-4"
              disabled={saving || rooms.length === 0}
            >
              {saving
                ? <><span className="spinner-border spinner-border-sm me-2" />Guardando...</>
                : <><i className="bi bi-check-lg me-2" />Crear reserva</>
              }
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
