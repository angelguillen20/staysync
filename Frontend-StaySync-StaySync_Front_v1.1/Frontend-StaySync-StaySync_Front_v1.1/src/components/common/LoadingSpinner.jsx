export default function LoadingSpinner({ message = 'Cargando...' }) {
  return (
    <div className="d-flex flex-column align-items-center justify-content-center py-5 gap-3">
      <div
        className="spinner-border"
        style={{ color: 'var(--ss-gold)', width: '3rem', height: '3rem' }}
        role="status"
      />
      <p className="text-muted mb-0">{message}</p>
    </div>
  );
}
