import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { confirmarPagoStripe } from '../../services/pagosService';

export default function PagoExitoso() {
  const [params]   = useSearchParams();
  const navigate   = useNavigate();
  const { user }   = useAuth();

  const sessionId  = params.get('session_id');
  const aprobado   = !!sessionId;

  const [confirmando, setConfirmando] = useState(aprobado);
  const [confirmado,  setConfirmado]  = useState(false);
  const [referencia,  setReferencia]  = useState(null);
  const [errConfirm,  setErrConfirm]  = useState('');

  useEffect(() => {
    if (!aprobado) return;

    confirmarPagoStripe({ sessionId, usuarioId: user?.userId })
      .then(pago => {
        setReferencia(pago.referencia);
        setConfirmado(true);
      })
      .catch(err => {
        const msg = err?.response?.data?.message ?? 'No se pudo registrar el pago.';
        setErrConfirm(msg);
        setConfirmado(true);
      })
      .finally(() => setConfirmando(false));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="fade-in-up p-3 p-md-4 d-flex justify-content-center align-items-start">
      <div style={{ maxWidth: 480, width: '100%', marginTop: 32 }}>
        <div className="card border-0 text-center p-4" style={{ borderRadius: 16, boxShadow: '0 8px 40px rgba(34,32,22,0.15)' }}>

          {confirmando ? (
            <>
              <div className="spinner-border mx-auto mb-3" style={{ color: 'var(--ss-gold)', width: 48, height: 48 }} />
              <h5 className="fw-semibold" style={{ color: 'var(--ss-dark)' }}>Confirmando tu pago…</h5>
              <p className="text-muted small">Un momento, estamos registrando tu reserva.</p>
            </>
          ) : (
            <>
              <div
                className="mx-auto mb-3 d-flex align-items-center justify-content-center rounded-circle"
                style={{
                  width: 72, height: 72,
                  background: aprobado ? 'rgba(40,167,69,0.12)' : 'rgba(220,53,69,0.12)',
                }}
              >
                <i
                  className={`bi ${aprobado ? 'bi-check-circle-fill' : 'bi-x-circle-fill'} fs-1`}
                  style={{ color: aprobado ? 'var(--ss-success)' : '#dc3545' }}
                />
              </div>

              <h4 className="fw-bold mb-1" style={{ color: 'var(--ss-dark)' }}>
                {aprobado ? '¡Pago aprobado!' : 'Pago no completado'}
              </h4>
              <p className="text-muted small mb-3">
                {aprobado
                  ? 'Tu reserva ha sido confirmada. El comprobante llegará a tu correo.'
                  : 'El pago fue cancelado o no se completó. Puedes intentarlo nuevamente.'}
              </p>

              {aprobado && confirmado && (
                <div className="p-3 rounded mb-4 text-start" style={{ background: 'var(--ss-cream)', border: '1px solid rgba(239,193,67,0.4)' }}>
                  {referencia ? (
                    <div className="d-flex justify-content-between small">
                      <span className="text-muted">Referencia de pago</span>
                      <span className="fw-semibold font-monospace" style={{ fontSize: '0.75rem' }}>{referencia}</span>
                    </div>
                  ) : errConfirm ? (
                    <p className="small text-danger mb-0">
                      <i className="bi bi-exclamation-circle me-1" />
                      {errConfirm}
                    </p>
                  ) : null}
                </div>
              )}

              {aprobado ? (
                <button className="btn btn-ss-dark w-100" onClick={() => navigate('/huesped/reservas')}>
                  <i className="bi bi-calendar-check me-2" />
                  Ver mis reservas
                </button>
              ) : (
                <div className="d-flex flex-column gap-2">
                  <button className="btn btn-ss-dark w-100" onClick={() => navigate(-1)}>
                    <i className="bi bi-arrow-repeat me-2" />
                    Intentar nuevamente
                  </button>
                  <button className="btn btn-outline-secondary w-100" onClick={() => navigate('/huesped/reservas')}>
                    Ir a mis reservas
                  </button>
                </div>
              )}
            </>
          )}
        </div>

        <p className="text-center text-muted mt-3" style={{ fontSize: '0.72rem' }}>
          <i className="bi bi-shield-check me-1" style={{ color: 'var(--ss-gold)' }} />
          Pago seguro procesado por Stripe — modo de prueba activo
        </p>
      </div>
    </div>
  );
}
