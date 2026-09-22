export default function AlertMessage({ type = 'danger', message, onClose }) {
  if (!message) return null;
  return (
    <div className={`alert alert-${type} alert-ss d-flex align-items-center gap-2`} role="alert">
      <i className={`bi ${type === 'danger' ? 'bi-exclamation-triangle' : type === 'success' ? 'bi-check-circle' : 'bi-info-circle'} flex-shrink-0`} />
      <span className="flex-grow-1">{message}</span>
      {onClose && (
        <button type="button" className="btn-close" onClick={onClose} aria-label="Cerrar" />
      )}
    </div>
  );
}
