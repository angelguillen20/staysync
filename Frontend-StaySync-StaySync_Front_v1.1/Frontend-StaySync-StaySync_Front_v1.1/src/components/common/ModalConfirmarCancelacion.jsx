import { useState, useEffect } from 'react';

const PALABRA_CLAVE = 'confirmar';

/**
 * Modal de confirmación de cancelación.
 * @param {string|null} deadline - Fecha límite formateada para mostrar la política de cancelación.
 *                                 Si se pasa, se muestra un aviso informativo con el plazo.
 */
export default function ModalConfirmarCancelacion({ show, onClose, onConfirm, loading = false, deadline = null }) {
  const [texto, setTexto] = useState('');

  useEffect(() => {
    if (show) setTexto('');
  }, [show]);

  if (!show) return null;

  const valido = texto.trim().toLowerCase() === PALABRA_CLAVE;

  const handleConfirm = () => {
    if (valido) onConfirm();
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 1055,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '1rem',
      }}
      onClick={e => { if (e.target === e.currentTarget && !loading) onClose(); }}
    >
      <div style={{ width: '100%', maxWidth: 440 }}>
        <div className="modal-content border-0" style={{ borderRadius: 14, overflow: 'hidden', boxShadow: '0 8px 40px rgba(34,32,22,0.35), 0 2px 12px rgba(34,32,22,0.18)' }}>

          {/* Header */}
          <div
            className="modal-header"
            style={{
              background: 'var(--ss-dark)',
              borderBottom: '3px solid var(--ss-danger)',
              padding: '1.1rem 1.5rem',
            }}
          >
            <div className="d-flex align-items-center gap-2">
              <i className="bi bi-exclamation-triangle-fill fs-5" style={{ color: 'var(--ss-danger)' }} />
              <h5 className="modal-title mb-0 fw-bold" style={{ color: '#ffffff' }}>
                Cancelar reserva
              </h5>
            </div>
            <button
              className="btn-close btn-close-white"
              onClick={onClose}
              disabled={loading}
              aria-label="Cerrar"
            />
          </div>

          {/* Body */}
          <div className="modal-body px-4 py-4" style={{ background: '#ffffff' }}>

            {/* Aviso de política de cancelación (solo si hay deadline) */}
            {deadline && (
              <div
                className="d-flex align-items-start gap-2 mb-3 p-3 rounded"
                style={{ background: 'rgba(239,193,67,0.12)', border: '1px solid rgba(239,193,67,0.45)' }}
              >
                <i className="bi bi-clock-history mt-1 flex-shrink-0" style={{ color: 'var(--ss-gold)' }} />
                <p className="mb-0 small" style={{ color: 'var(--ss-dark)', lineHeight: 1.55 }}>
                  <strong>Política de cancelación:</strong> Puedes cancelar sin costo hasta el{' '}
                  <strong>{deadline}</strong>. Después de esa fecha el sistema bloqueará la cancelación.
                </p>
              </div>
            )}

            <div
              className="d-flex align-items-start gap-3 mb-3 p-3 rounded"
              style={{ background: 'rgba(220,53,69,0.07)', border: '1px solid rgba(220,53,69,0.2)' }}
            >
              <i className="bi bi-info-circle-fill mt-1" style={{ color: 'var(--ss-danger)', flexShrink: 0 }} />
              <p className="mb-0 small" style={{ color: 'var(--ss-dark)', lineHeight: 1.55 }}>
                Esta acción <strong>no se puede deshacer</strong>. La reserva quedará en estado
                {' '}<strong>CANCELADA</strong> de forma permanente.
              </p>
            </div>

            <label className="form-label fw-semibold small mb-1" style={{ color: 'var(--ss-dark)' }}>
              Para confirmar, escribe{' '}
              <span
                className="px-2 py-1 rounded"
                style={{
                  background: 'var(--ss-cream)',
                  border: '1px solid var(--ss-gold)',
                  fontFamily: 'monospace',
                  color: 'var(--ss-dark)',
                  letterSpacing: '0.05em',
                }}
              >
                confirmar
              </span>
            </label>

            <input
              type="text"
              className="form-control mt-2"
              placeholder="Escribe confirmar…"
              value={texto}
              onChange={e => setTexto(e.target.value)}
              disabled={loading}
              autoComplete="off"
              style={{
                borderColor: texto.length === 0
                  ? undefined
                  : valido
                    ? 'var(--ss-success)'
                    : 'var(--ss-danger)',
                boxShadow: texto.length === 0
                  ? undefined
                  : valido
                    ? '0 0 0 0.2rem rgba(40,167,69,0.2)'
                    : '0 0 0 0.2rem rgba(220,53,69,0.15)',
                transition: 'border-color 0.2s, box-shadow 0.2s',
              }}
            />
            {texto.length > 0 && !valido && (
              <p className="small mt-1 mb-0" style={{ color: 'var(--ss-danger)' }}>
                <i className="bi bi-x-circle me-1" />
                Debes escribir exactamente <em>confirmar</em>
              </p>
            )}
            {valido && (
              <p className="small mt-1 mb-0" style={{ color: 'var(--ss-success)' }}>
                <i className="bi bi-check-circle me-1" />
                Listo, puedes confirmar la cancelación
              </p>
            )}
          </div>

          {/* Footer */}
          <div
            className="modal-footer px-4 py-3 gap-2"
            style={{ background: 'var(--ss-cream)', borderTop: '1px solid rgba(34,32,22,0.1)' }}
          >
            <button
              className="btn btn-outline-secondary"
              onClick={onClose}
              disabled={loading}
            >
              <i className="bi bi-arrow-left me-1" />
              Volver
            </button>
            <button
              className="btn text-white fw-semibold"
              style={{
                background: valido && !loading ? 'var(--ss-danger)' : '#c8b8b8',
                border: 'none',
                cursor: valido && !loading ? 'pointer' : 'not-allowed',
                transition: 'background 0.2s',
              }}
              disabled={!valido || loading}
              onClick={handleConfirm}
            >
              {loading
                ? <><span className="spinner-border spinner-border-sm me-2" />Cancelando...</>
                : <><i className="bi bi-x-circle me-1" />Confirmar cancelación</>
              }
            </button>
          </div>

        </div>
      </div>
    </div>
  );
}
