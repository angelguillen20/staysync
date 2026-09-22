import { useState } from 'react';
import { getPagosPorReserva, procesarPago, solicitarReembolso } from '../../services/pagosService';
import AlertMessage   from '../../components/common/AlertMessage';
import LoadingSpinner from '../../components/common/LoadingSpinner';

const METODOS = ['TARJETA_CREDITO', 'TARJETA_DEBITO', 'EFECTIVO', 'TRANSFERENCIA'];

const ESTADO_BADGE = {
  PENDIENTE:  'bg-warning text-dark',
  COMPLETADO: 'bg-success',
  FALLIDO:    'bg-danger',
  REEMBOLSADO:'bg-secondary',
};

export default function PagosRecepcion() {
  const [reservaId, setReservaId] = useState('');
  const [pagos,     setPagos]     = useState([]);
  const [loading,   setLoading]   = useState(false);
  const [error,     setError]     = useState('');
  const [success,   setSuccess]   = useState('');
  const [buscado,   setBuscado]   = useState(false);

  /* ── Nuevo pago ── */
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ reservaId: '', monto: '', metodoPago: 'TARJETA_CREDITO', descripcion: '' });
  const [saving, setSaving] = useState(false);

  const buscarPagos = async () => {
    if (!reservaId) return;
    setLoading(true); setError(''); setPagos([]); setBuscado(true);
    try {
      const data = await getPagosPorReserva(reservaId);
      setPagos(Array.isArray(data) ? data : []);
    } catch {
      setError('No se encontraron pagos para esa reserva o la reserva no existe.');
    } finally {
      setLoading(false);
    }
  };

  const handleProcesar = async e => {
    e.preventDefault();
    setSaving(true); setError('');
    try {
      await procesarPago({ ...form, reservaId: Number(form.reservaId), monto: Number(form.monto) });
      setSuccess('Pago procesado correctamente.');
      setShowForm(false);
      setForm({ reservaId: '', monto: '', metodoPago: 'TARJETA_CREDITO', descripcion: '' });
      if (form.reservaId === reservaId) buscarPagos();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Error al procesar el pago.');
    } finally {
      setSaving(false);
    }
  };

  const handleReembolso = async (pagoId) => {
    if (!window.confirm('¿Confirmas solicitar el reembolso de este pago?')) return;
    setError('');
    try {
      await solicitarReembolso(pagoId, { motivo: 'Solicitud desde recepción' });
      setSuccess('Reembolso solicitado correctamente.');
      buscarPagos();
    } catch {
      setError('No se pudo solicitar el reembolso.');
    }
  };

  return (
    <div className="fade-in-up p-3 p-md-4">
      <div className="d-flex flex-wrap justify-content-between align-items-center mb-4 gap-3">
        <div>
          <h2 className="fw-bold mb-0" style={{ color: 'var(--ss-dark)' }}>
            <i className="bi bi-credit-card me-2" style={{ color: 'var(--ss-gold)' }} />
            Pagos
          </h2>
          <small className="text-muted">Procesamiento de pagos — pagos-service</small>
        </div>
        <button className="btn btn-ss-dark" onClick={() => setShowForm(p => !p)}>
          <i className="bi bi-plus-lg me-2" />Nuevo pago
        </button>
      </div>

      <AlertMessage message={error}   onClose={() => setError('')} />
      <AlertMessage type="success" message={success} onClose={() => setSuccess('')} />

      {/* ── Formulario nuevo pago ── */}
      {showForm && (
        <div className="card border-0 shadow-sm mb-4">
          <div className="card-header fw-semibold" style={{ background: 'var(--ss-cream)', borderBottom: '2px solid var(--ss-gold)' }}>
            <i className="bi bi-cash-coin me-2" style={{ color: 'var(--ss-gold)' }} />
            Procesar pago
          </div>
          <div className="card-body">
            <form onSubmit={handleProcesar} noValidate>
              <div className="row g-3">
                <div className="col-md-4">
                  <label className="form-label fw-medium small">ID de Reserva</label>
                  <input
                    type="number"
                    className="form-control"
                    placeholder="Ej: 42"
                    value={form.reservaId}
                    onChange={e => setForm(p => ({ ...p, reservaId: e.target.value }))}
                    required
                  />
                </div>
                <div className="col-md-4">
                  <label className="form-label fw-medium small">Monto ($)</label>
                  <input
                    type="number"
                    className="form-control"
                    placeholder="0.00"
                    step="0.01"
                    min="0"
                    value={form.monto}
                    onChange={e => setForm(p => ({ ...p, monto: e.target.value }))}
                    required
                  />
                </div>
                <div className="col-md-4">
                  <label className="form-label fw-medium small">Método de pago</label>
                  <select
                    className="form-select"
                    value={form.metodoPago}
                    onChange={e => setForm(p => ({ ...p, metodoPago: e.target.value }))}
                  >
                    {METODOS.map(m => <option key={m} value={m}>{m.replace(/_/g, ' ')}</option>)}
                  </select>
                </div>
                <div className="col-12">
                  <label className="form-label fw-medium small">Descripción (opcional)</label>
                  <input
                    className="form-control"
                    placeholder="Ej: Pago total estadía"
                    value={form.descripcion}
                    onChange={e => setForm(p => ({ ...p, descripcion: e.target.value }))}
                  />
                </div>
              </div>
              <div className="d-flex gap-2 mt-3">
                <button type="submit" className="btn btn-ss-dark" disabled={saving}>
                  {saving
                    ? <><span className="spinner-border spinner-border-sm me-2" />Procesando...</>
                    : <><i className="bi bi-check-lg me-2" />Confirmar pago</>
                  }
                </button>
                <button type="button" className="btn btn-outline-secondary" onClick={() => setShowForm(false)}>
                  Cancelar
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Buscar por reserva ── */}
      <div className="card border-0 shadow-sm">
        <div className="card-header fw-semibold" style={{ background: 'var(--ss-cream)', borderBottom: '2px solid var(--ss-gold)' }}>
          <i className="bi bi-search me-2" style={{ color: 'var(--ss-gold)' }} />
          Buscar pagos por reserva
        </div>
        <div className="card-body">
          <div className="d-flex gap-2 mb-4" style={{ maxWidth: 400 }}>
            <input
              type="number"
              className="form-control"
              placeholder="ID de reserva..."
              value={reservaId}
              onChange={e => setReservaId(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && buscarPagos()}
            />
            <button className="btn btn-ss-dark px-3" onClick={buscarPagos} disabled={loading || !reservaId}>
              {loading ? <span className="spinner-border spinner-border-sm" /> : <i className="bi bi-search" />}
            </button>
          </div>

          {loading && <LoadingSpinner message="Buscando pagos..." />}

          {!loading && buscado && pagos.length === 0 && (
            <div className="text-center py-4 text-muted">
              <i className="bi bi-inbox fs-1 d-block mb-2" />
              No hay pagos registrados para la reserva #{reservaId}.
            </div>
          )}

          {pagos.length > 0 && (
            <div className="table-responsive">
              <table className="table table-hover align-middle">
                <thead className="table-light">
                  <tr>
                    <th>#</th>
                    <th>Monto</th>
                    <th>Método</th>
                    <th>Estado</th>
                    <th>Fecha</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {pagos.map(p => (
                    <tr key={p.id}>
                      <td className="text-muted small">#{p.id}</td>
                      <td className="fw-bold">${Number(p.monto ?? 0).toLocaleString()}</td>
                      <td className="text-muted small">{p.metodoPago?.replace(/_/g, ' ')}</td>
                      <td>
                        <span className={`badge ${ESTADO_BADGE[p.estado] ?? 'bg-secondary'}`}>
                          {p.estado}
                        </span>
                      </td>
                      <td className="text-muted small">
                        {p.fechaPago ? new Date(p.fechaPago).toLocaleString('es-CO') : '—'}
                      </td>
                      <td>
                        {p.estado === 'COMPLETADO' && (
                          <button
                            className="btn btn-outline-warning btn-sm"
                            onClick={() => handleReembolso(p.id)}
                          >
                            <i className="bi bi-arrow-counterclockwise me-1" />Reembolso
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
