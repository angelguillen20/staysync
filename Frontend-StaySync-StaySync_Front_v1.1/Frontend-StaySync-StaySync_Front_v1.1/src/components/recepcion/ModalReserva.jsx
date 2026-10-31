import { useState, useEffect } from 'react';
import { getHabitaciones } from '../../services/habitacionesService';
import { crearReserva }    from '../../services/reservasService';
import { getHuespedes }    from '../../services/usuariosService';
import AlertMessage        from '../common/AlertMessage';
import { hoyHotel } from '../../utils/fechas';

const EMPTY = {
  usuarioId: '',
  habitacionId: '',
  fechaEntrada: '',
  fechaSalida: '',
  numHuespedes: 1,
  notas: '',
};

/* El estado de la habitación es el de hoy (OCUPADA, EN_LIMPIEZA...): no impide reservarla para otras
   fechas. Los cruces de fechas los rechaza reservas-service; aquí solo se excluye lo que no se vende. */
const NO_RESERVABLE = ['MANTENIMIENTO', 'FUERA_DE_SERVICIO'];

export default function ModalReserva({ show, onClose, onCreated }) {
  const [form,      setForm]      = useState(EMPTY);
  const [rooms,     setRooms]     = useState([]);
  const [huespedes, setHuespedes] = useState([]);
  const [saving,    setSaving]    = useState(false);
  const [error,     setError]     = useState('');

  useEffect(() => {
    if (show) {
      setForm(EMPTY);
      setError('');
      getHabitaciones()
        .then(data => setRooms(data.filter(h => !NO_RESERVABLE.includes(h.estado))))
        .catch(() => setRooms([]));
      getHuespedes()
        .then(data => setHuespedes((Array.isArray(data) ? data : []).filter(h => h.activo !== false)))
        .catch(() => setHuespedes([]));
    }
  }, [show]);

  const completo = form.usuarioId && form.habitacionId && form.fechaEntrada && form.fechaSalida;

  const handleChange = e => {
    const { name, value } = e.target;
    setForm(p => ({ ...p, [name]: value }));
  };

  const handleSubmit = async e => {
    e.preventDefault();
    setError(''); setSaving(true);
    try {
      // El BFF completa nombre y email del huésped desde su perfil
      await crearReserva({
        ...form,
        usuarioId:    Number(form.usuarioId),
        habitacionId: Number(form.habitacionId),
        numHuespedes: Number(form.numHuespedes),
        fuente:       'DIRECTO',
      });
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
              <i className="bi bi-calendar-plus me-2" style={{ color: 'var(--ss-gold)' }} />Nueva reserva manual
            </h5>
            <button className="btn-close" onClick={onClose} aria-label="Cerrar" />
          </div>

          <div className="modal-body">
            <AlertMessage message={error} onClose={() => setError('')} />

            <form id="form-reserva" onSubmit={handleSubmit} noValidate>
              <div className="row g-3">
                {/* Huésped */}
                <div className="col-12">
                  <label htmlFor="reserva-huesped" className="form-label fw-medium small">Huésped</label>
                  <select
                    id="reserva-huesped"
                    className="form-select"
                    name="usuarioId"
                    value={form.usuarioId}
                    onChange={handleChange}
                    required
                  >
                    <option value="">— Selecciona el huésped —</option>
                    {huespedes.map(h => (
                      <option key={h.id} value={h.id}>
                        {h.nombre} {h.apellido} — {h.email}
                      </option>
                    ))}
                  </select>
                  <small className="text-muted">
                    ¿No aparece? El huésped crea su cuenta desde el portal; le llegará el correo de la reserva.
                  </small>
                </div>

                {/* Habitación */}
                <div className="col-12">
                  <label htmlFor="reserva-habitacion" className="form-label fw-medium small">Habitación</label>
                  <select
                    id="reserva-habitacion"
                    className="form-select"
                    name="habitacionId"
                    value={form.habitacionId}
                    onChange={handleChange}
                    required
                  >
                    <option value="">— Selecciona una habitación —</option>
                    {rooms.map(h => (
                      <option key={h.id} value={h.id}>
                        Hab. {h.numero} — {h.tipoNombre ?? 'Estándar'} — ${h.precioPorNoche?.toLocaleString('es-CL')}/noche · {h.capacidad} pers.
                      </option>
                    ))}
                  </select>
                  {rooms.length === 0 && (
                    <small className="text-muted">No hay habitaciones habilitadas para reservar.</small>
                  )}
                </div>

                {/* Fechas */}
                <div className="col-md-6">
                  <label htmlFor="reserva-entrada" className="form-label fw-medium small">Fecha de entrada</label>
                  <input
                    id="reserva-entrada"
                    type="date"
                    className="form-control"
                    name="fechaEntrada"
                    value={form.fechaEntrada}
                    onChange={handleChange}
                    min={hoyHotel()}
                    required
                  />
                </div>
                <div className="col-md-6">
                  <label htmlFor="reserva-salida" className="form-label fw-medium small">Fecha de salida</label>
                  <input
                    id="reserva-salida"
                    type="date"
                    className="form-control"
                    name="fechaSalida"
                    value={form.fechaSalida}
                    onChange={handleChange}
                    min={form.fechaEntrada || hoyHotel()}
                    required
                  />
                </div>

                {/* N° huéspedes */}
                <div className="col-md-4">
                  <label htmlFor="reserva-huespedes" className="form-label fw-medium small">N° huéspedes</label>
                  <input
                    id="reserva-huespedes"
                    type="number"
                    className="form-control"
                    name="numHuespedes"
                    value={form.numHuespedes}
                    onChange={handleChange}
                    min="1" max="10"
                    required
                  />
                </div>

                <div className="col-md-8 d-flex align-items-end">
                  <small className="text-muted">
                    <i className="bi bi-info-circle me-1" />
                    Queda pendiente de pago: regístralo en Pagos para confirmarla. Sin pago se libera a las 24 h.
                  </small>
                </div>

                <div className="col-12">
                  <label htmlFor="reserva-notas" className="form-label fw-medium small">Observaciones</label>
                  <textarea
                    id="reserva-notas"
                    className="form-control"
                    name="notas"
                    rows={2}
                    maxLength={500}
                    placeholder="Solicitudes especiales, alergias, etc."
                    value={form.notas}
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
              disabled={saving || !completo}
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
