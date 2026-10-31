import { useId, useState } from 'react';
import { subirFoto } from '../../services/mediaService';

const MAX_BYTES = 5 * 1024 * 1024;
const TIPOS = 'image/jpeg,image/png,image/webp';

/*
 * Elegir una foto: subir un archivo o pegar un enlace https. El enlace sirve también si la
 * subida no está disponible. onChange recibe la URL final (o '' al quitarla).
 * carpeta: 'hoteles' | 'habitaciones' | 'servicios'
 */
export default function SubirFoto({ value, onChange, carpeta, label = 'Foto' }) {
  const id = useId();
  const [subiendo, setSubiendo] = useState(false);
  const [error, setError] = useState('');

  const elegirArchivo = async (e) => {
    const archivo = e.target.files?.[0];
    e.target.value = '';
    if (!archivo) return;
    if (archivo.size > MAX_BYTES) { setError('La foto debe pesar hasta 5 MB.'); return; }
    setError(''); setSubiendo(true);
    try {
      onChange(await subirFoto(archivo, carpeta));
    } catch (err) {
      setError(err.response?.data?.message ?? 'No se pudo subir la foto. Pega un enlace https a la imagen.');
    } finally {
      setSubiendo(false);
    }
  };

  return (
    <div className="subir-foto">
      <label className="form-label fw-medium small mb-2" htmlFor={`${id}-archivo`}>{label}</label>
      <div className="d-flex gap-3 align-items-start">
        {value
          ? <img src={value} alt="" className="subir-foto-preview" />
          : <div className="subir-foto-preview d-flex align-items-center justify-content-center text-muted"><i className="bi bi-image" aria-hidden="true" /></div>}
        <div className="flex-grow-1 d-flex flex-column gap-2">
          <div className="d-flex flex-wrap gap-2 align-items-center">
            <label className={`btn btn-sm btn-ss-dark mb-0 ${subiendo ? 'disabled' : ''}`} htmlFor={`${id}-archivo`}>
              {subiendo
                ? <><span className="spinner-border spinner-border-sm me-1" />Subiendo…</>
                : <><i className="bi bi-upload me-1" aria-hidden="true" />{value ? 'Cambiar foto' : 'Subir foto'}</>}
            </label>
            <input id={`${id}-archivo`} type="file" accept={TIPOS} className="visually-hidden" onChange={elegirArchivo} disabled={subiendo} />
            {value && (
              <button type="button" className="btn btn-sm btn-outline-secondary" onClick={() => onChange('')}>Quitar</button>
            )}
            <small className="text-muted">JPG, PNG o WebP hasta 5 MB</small>
          </div>
          <input
            type="url"
            className="form-control form-control-sm"
            placeholder="o pega un enlace https://…"
            aria-label={`${label}: enlace a la imagen`}
            value={value ?? ''}
            onChange={e => onChange(e.target.value.trim())}
          />
          {error && <small className="text-danger" role="alert">{error}</small>}
        </div>
      </div>
    </div>
  );
}
