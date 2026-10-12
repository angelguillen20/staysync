// Fondo clicable de los modales: un <button> nativo que cubre el overlay y queda detrás
// del contenido (que debe llevar position: relative). Fuera del orden de tabulación porque
// el modal ya tiene su propio botón "Cerrar" accesible por teclado.
export default function ModalBackdrop({ onClose, disabled }) {
  return (
    <button
      type="button"
      aria-label="Cerrar"
      tabIndex={-1}
      onClick={onClose}
      disabled={disabled}
      style={{ position: 'absolute', inset: 0, border: 0, padding: 0, background: 'transparent', cursor: 'default' }}
    />
  );
}
