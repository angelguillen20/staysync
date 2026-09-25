// Clima actual de la ciudad del hotel. Si no hay datos (sin API key o WeatherAPI caído)
// no se muestra nada, igual que el fallback silencioso del BFF.
export default function WidgetClima({ clima }) {
  if (!clima) return null;

  return (
    <div className="d-flex align-items-center gap-2 px-3 py-2" style={{ background: 'var(--ss-cream)', borderRadius: 10 }}>
      {clima.icono && <img src={clima.icono} alt={clima.condicion} width={32} height={32} />}
      <div>
        <div className="fw-bold" style={{ color: 'var(--ss-dark)', lineHeight: 1 }}>
          {Math.round(clima.temperaturaC)}°C
        </div>
        <small className="text-muted">{clima.ciudad} · {clima.condicion}</small>
      </div>
    </div>
  );
}
