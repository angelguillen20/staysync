import { useState } from 'react';
import Placa from './Placa';

/*
 * Foto de habitación o servicio. Sin foto (o si el enlace falla) muestra un respaldo de marca
 * en vez de un ícono roto. numero: placa de habitación superpuesta (opcional).
 */
export default function Foto({ src, alt, numero, className = '' }) {
  const [rota, setRota] = useState(false);
  const mostrar = src && !rota;
  return (
    <div className={`foto ${className}`}>
      {mostrar
        ? <img src={src} alt={alt} loading="lazy" onError={() => setRota(true)} />
        : (
          <div className="foto-respaldo" role="img" aria-label={`${alt} (sin foto)`}>
            <i className="bi bi-image fs-3" aria-hidden="true" />
            <span>Sin foto</span>
          </div>
        )}
      {numero != null && <Placa numero={numero} />}
    </div>
  );
}
