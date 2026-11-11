import { useCallback, useEffect, useState } from 'react';
import { getCuenta, pagarSaldo } from '../../services/reservasService';
import Modal from '../common/Modal';
import AlertMessage from '../common/AlertMessage';
import LoadingSpinner from '../common/LoadingSpinner';

const clp = (v) => `$${Number(v ?? 0).toLocaleString('es-CL')}`;

const METODOS = [
  { value: 'EFECTIVO',        label: 'Efectivo' },
  { value: 'TARJETA_DEBITO',  label: 'Tarjeta de débito' },
  { value: 'TARJETA_CREDITO', label: 'Tarjeta de crédito' },
  { value: 'TRANSFERENCIA',   label: 'Transferencia' },
];

/*
 * Cuenta del huésped: estadía + servicios consumidos − pagos. Desde aquí se cobra el saldo y,
 * si se pasa onCheckout, se hace el check-out (el BFF no lo permite con saldo pendiente).
 */
/* puedeCobrar=false: vista del huésped, que paga el saldo en recepción */
export default function ModalCuenta({ reserva, onClose, onCheckout, puedeCobrar = true }) {
  const [cuenta,   setCuenta]   = useState(null);
  const [error,    setError]    = useState('');
  const [metodo,   setMetodo]   = useState('EFECTIVO');
  const [cobrando, setCobrando] = useState(false);
  const [saliendo, setSaliendo] = useState(false);

  const cargar = useCallback(() => {
    setError('');
    getCuenta(reserva.id)
      .then(setCuenta)
      .catch(err => setError(err.response?.data?.message ?? 'No se pudo cargar la cuenta.'));
  }, [reserva.id]);

  useEffect(() => { cargar(); }, [cargar]);

  const saldo = Number(cuenta?.saldo ?? 0);

  const cobrar = async () => {
    setCobrando(true); setError('');
    try {
      await pagarSaldo(reserva.id, metodo);
      cargar();
    } catch (err) {
      setError(err.response?.data?.message ?? 'No se pudo cobrar el saldo.');
    } finally {
      setCobrando(false);
    }
  };

  const checkout = async () => {
    setSaliendo(true); setError('');
    try {
      await onCheckout();
      onClose();
    } catch (err) {
      setError(err.response?.data?.message ?? 'No se pudo hacer el check-out.');
      setSaliendo(false);
    }
  };

  const pie = (
    <>
      <button type="button" className="btn btn-outline-secondary" onClick={onClose} disabled={cobrando || saliendo}>Cerrar</button>
      {onCheckout && (
        <button type="button" className="btn btn-ss-dark" onClick={checkout} disabled={!cuenta || saldo > 0 || saliendo}
                title={saldo > 0 ? 'Cobra el saldo antes del check-out' : undefined}>
          {saliendo ? <><span className="spinner-border spinner-border-sm me-1" />Haciendo check-out…</> : 'Hacer check-out'}
        </button>
      )}
    </>
  );

  return (
    <Modal titulo={`Cuenta de la reserva ${reserva.codigo ?? `#${reserva.id}`}`} onClose={onClose} pie={pie} ocupado={cobrando || saliendo}>
      <AlertMessage message={error} onClose={() => setError('')} />
      {!cuenta && !error && <LoadingSpinner message="Armando la cuenta…" />}
      {cuenta && (
        <>
          {cuenta.huespedNombre && <p className="mb-3">{cuenta.huespedNombre}</p>}

          <div className="cuenta-linea"><span>Estadía</span><span>{clp(cuenta.estadia)}</span></div>
          {cuenta.consumos.map(c => (
            <div key={c.id} className="cuenta-linea">
              <span>{c.servicioNombre}{c.cantidad > 1 ? ` × ${c.cantidad}` : ''}</span>
              <span>{clp(c.precioTotal)}</span>
            </div>
          ))}
          {cuenta.pagos.filter(p => p.estado === 'COMPLETADO').map(p => (
            <div key={p.id} className="cuenta-linea text-muted">
              <span>Pagado con {METODOS.find(m => m.value === p.metodoPago)?.label.toLowerCase() ?? 'tarjeta en línea'} (ref. {p.referencia})</span>
              <span>−{clp(p.monto)}</span>
            </div>
          ))}

          <div className="cuenta-total">
            <span className="fw-semibold">{saldo < 0 ? 'A favor del huésped' : 'Saldo'}</span>
            <span className="cifra" style={{ fontSize: '1.8rem' }}>{clp(Math.abs(saldo))}</span>
          </div>
          {saldo < 0 && <small className="text-muted">Devuélvelo desde Pagos con un reembolso.</small>}

          {saldo > 0 && !puedeCobrar && (
            <p className="small text-muted mt-3 mb-0">Este saldo se paga en recepción al hacer el check-out.</p>
          )}
          {saldo > 0 && puedeCobrar && (
            <div className="d-flex flex-wrap gap-2 align-items-end mt-3 pt-3 border-top">
              <div className="flex-grow-1">
                <label htmlFor="cuenta-metodo" className="form-label small fw-medium">Método de pago</label>
                <select id="cuenta-metodo" className="form-select" value={metodo} onChange={e => setMetodo(e.target.value)}>
                  {METODOS.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
                </select>
              </div>
              <button type="button" className="btn btn-ss-gold" onClick={cobrar} disabled={cobrando}>
                {cobrando ? <><span className="spinner-border spinner-border-sm me-1" />Cobrando…</> : `Cobrar ${clp(saldo)}`}
              </button>
            </div>
          )}
        </>
      )}
    </Modal>
  );
}
