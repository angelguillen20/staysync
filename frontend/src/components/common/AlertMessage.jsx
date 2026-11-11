const ICONOS = {
  danger:  'bi-exclamation-triangle',
  success: 'bi-check-circle',
};

export default function AlertMessage({ type = 'danger', message, onClose }) {
  if (!message) return null;
  return (
    <div className={`alert alert-${type} alert-ss d-flex align-items-center gap-2`} role="alert">
      <i className={`bi ${ICONOS[type] ?? 'bi-info-circle'} flex-shrink-0`} />
      <span className="flex-grow-1">{message}</span>
      {onClose && (
        <button type="button" className="btn-close" onClick={onClose} aria-label="Cerrar" />
      )}
    </div>
  );
}
