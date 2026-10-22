import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { cambiarHotel } from '../../services/authService';
import { getHotelesPublicos } from '../../services/hotelesService';
import { esColorValido, esLogoValido, guardarSlug } from '../../theme/hotelTheme';
import LoadingSpinner from '../../components/common/LoadingSpinner';
import AlertMessage from '../../components/common/AlertMessage';

const COLORES_STAYSYNC = { primary: '#222016', secondary: '#efc143', background: '#fef8e1' };

/** Colores de la tarjeta: los del hotel si son válidos, si no los de StaySync. */
function coloresDe(theme) {
  return Object.fromEntries(Object.entries(COLORES_STAYSYNC)
    .map(([clave, defecto]) => [clave, esColorValido(theme?.[clave]) ? theme[clave] : defecto]));
}

function TarjetaHotel({ hotel, actual, entrando, onEntrar }) {
  const c = coloresDe(hotel.theme);
  return (
    <div className="card border-0 shadow-sm h-100 overflow-hidden" style={{ borderRadius: 14 }}>
      <div
        className="d-flex align-items-center justify-content-center"
        style={{ background: c.primary, color: c.secondary, height: 120, borderBottom: `4px solid ${c.secondary}` }}
      >
        {esLogoValido(hotel.logoUrl)
          ? <img src={hotel.logoUrl} alt={hotel.nombre} style={{ maxHeight: 72, maxWidth: '70%' }} />
          : <i className="bi bi-building" style={{ fontSize: '3rem' }} />}
      </div>
      <div className="card-body d-flex flex-column" style={{ background: c.background }}>
        <h5 className="fw-bold mb-1" style={{ color: c.primary }}>{hotel.nombre}</h5>
        <small className="text-muted mb-3">Portal: {hotel.slug}</small>
        <div className="mt-auto">
          {actual ? (
            <span className="badge w-100 py-2" style={{ background: c.secondary, color: c.primary }}>
              <i className="bi bi-check-circle me-1" />Estás en este hotel
            </span>
          ) : (
            <button
              type="button"
              className="btn w-100 fw-semibold"
              style={{ background: c.primary, color: '#fff' }}
              disabled={entrando}
              onClick={() => onEntrar(hotel)}
            >
              {entrando
                ? <span className="spinner-border spinner-border-sm" />
                : <><i className="bi bi-box-arrow-in-right me-1" />Entrar</>}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * Selector de portales: un huésped tiene una sola cuenta para todos los hoteles de la cadena.
 * Al elegir uno, la sesión pasa a ese hotel (marca, habitaciones y reservas de ese hotel)
 * sin volver a escribir la contraseña.
 */
export default function PortalesHuesped() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const [hoteles, setHoteles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [entrando, setEntrando] = useState(null);

  useEffect(() => {
    getHotelesPublicos()
      .then((data) => setHoteles(Array.isArray(data) ? data : []))
      .catch(() => setError('No se pudieron cargar los hoteles.'))
      .finally(() => setLoading(false));
  }, []);

  const entrar = async (hotel) => {
    setEntrando(hotel.slug);
    setError('');
    try {
      const data = await cambiarHotel(hotel.slug);
      guardarSlug(hotel.slug);
      login(data);
      navigate('/huesped', { replace: true });
    } catch {
      setError(`No se pudo entrar a ${hotel.nombre}. Intenta nuevamente.`);
    } finally {
      setEntrando(null);
    }
  };

  return (
    <div className="fade-in-up p-3 p-md-4">
      <div className="mb-4">
        <h2 className="fw-bold mb-0" style={{ color: 'var(--ss-dark)' }}>
          <i className="bi bi-buildings me-2" style={{ color: 'var(--ss-gold)' }} />Nuestros hoteles
        </h2>
        <small className="text-muted">
          Tu cuenta sirve en todos. Elige un hotel para ver sus habitaciones y tus reservas en él.
        </small>
      </div>

      <AlertMessage message={error} onClose={() => setError('')} />
      {loading && <LoadingSpinner message="Cargando hoteles..." />}
      {!loading && hoteles.length === 0 && !error && (
        <div className="text-center py-5 text-muted">
          <i className="bi bi-building-x fs-1 d-block mb-2" />No hay hoteles disponibles.
        </div>
      )}

      <div className="row g-4">
        {hoteles.map((h) => (
          <div key={h.tenantId} className="col-sm-6 col-lg-4">
            <TarjetaHotel
              hotel={h}
              actual={h.tenantId === user?.tenantId}
              entrando={entrando === h.slug}
              onEntrar={entrar}
            />
          </div>
        ))}
      </div>
    </div>
  );
}
