/* Número de habitación como placa de puerta. tamano: 'sm' | 'lg' | undefined */
export default function Placa({ numero, tamano }) {
  return (
    <span className={`placa ${tamano ? `placa-${tamano}` : ''}`} aria-label={`Habitación ${numero ?? ''}`}>
      {numero ?? '—'}
    </span>
  );
}
