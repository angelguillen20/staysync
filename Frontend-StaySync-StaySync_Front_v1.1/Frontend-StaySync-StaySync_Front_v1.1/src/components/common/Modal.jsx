import { useEffect, useId } from 'react';
import ModalBackdrop from './ModalBackdrop';

/* Marco común de los modales: Escape y el fondo cierran, salvo mientras se guarda (ocupado). */
export default function Modal({ titulo, onClose, children, pie, tamano = '', ocupado = false }) {
  const id = useId();

  useEffect(() => {
    const alTeclear = (e) => { if (e.key === 'Escape' && !ocupado) onClose(); };
    window.addEventListener('keydown', alTeclear);
    return () => window.removeEventListener('keydown', alTeclear);
  }, [onClose, ocupado]);

  return (
    <div className="modal show d-block" role="dialog" aria-modal="true" aria-labelledby={id}
         style={{ background: 'rgba(34,32,22,0.55)' }}>
      <ModalBackdrop onClose={onClose} disabled={ocupado} />
      <div className={`modal-dialog modal-dialog-centered modal-dialog-scrollable ${tamano}`} style={{ position: 'relative' }}>
        <div className="modal-content">
          <div className="modal-header">
            <h5 className="modal-title" id={id}>{titulo}</h5>
            <button type="button" className="btn-close" onClick={onClose} aria-label="Cerrar" disabled={ocupado} />
          </div>
          <div className="modal-body">{children}</div>
          {pie && <div className="modal-footer">{pie}</div>}
        </div>
      </div>
    </div>
  );
}
