import { useEffect, useMemo, useState } from 'react';
import { getHoteles } from '../../services/usuariosService';

/*
 * Identidad de cada hotel en las vistas de plataforma: un punto de color + su nombre.
 * Paleta categórica validada con la skill dataviz contra el fondo crema (pasa la separación
 * para daltonismo; tres colores tienen poco contraste, por eso el nombre va siempre al lado).
 */
export const COLORES_HOTEL = ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6', '#e34948'];

function hash(tenantId) {
  let h = 2166136261;
  for (const c of String(tenantId ?? '')) h = Math.imul(h ^ c.charCodeAt(0), 16777619);
  return (h >>> 0) % COLORES_HOTEL.length;
}

/*
 * El color sale del id del hotel, no de su posición en la lista (que va por nombre). Si su color
 * ya lo tiene otro hotel, toma el siguiente libre, recorriendo siempre por id: los hoteles nunca
 * comparten color y crear uno nuevo no repinta a los demás salvo que choque con alguno.
 * shortcut: desde el 9.º hotel los colores se repiten (el nombre los distingue); si la cadena
 * crece, guardar el color en el hotel.
 */
export function asignarColores(tenantIds) {
  const usados = new Set();
  const colores = {};
  for (const id of [...tenantIds].sort()) {
    let i = hash(id);
    for (let n = 0; n < COLORES_HOTEL.length && usados.has(i); n++) i = (i + 1) % COLORES_HOTEL.length;
    usados.add(i);
    colores[id] = COLORES_HOTEL[i];
    if (usados.size === COLORES_HOTEL.length) usados.clear();
  }
  return colores;
}

let colores = {};
export const colorHotel = (tenantId) => colores[tenantId] ?? COLORES_HOTEL[hash(tenantId)];

let pedido = null;
/* Hoteles de la plataforma (solo SUPER_ADMIN), pedidos una vez por sesión. */
export function useHotelesPlataforma(activo = true) {
  const [hoteles, setHoteles] = useState([]);
  useEffect(() => {
    if (!activo) return;
    pedido ??= getHoteles().catch(() => { pedido = null; return []; });
    pedido.then(l => {
      const lista = Array.isArray(l) ? l : [];
      colores = asignarColores(lista.map(h => h.tenantId));
      setHoteles(lista);
    });
  }, [activo]);
  return useMemo(() => hoteles.map(h => ({ ...h, color: colorHotel(h.tenantId) })), [hoteles]);
}
/* Tras crear un hotel, la próxima pantalla vuelve a pedir la lista. */
export const olvidarHoteles = () => { pedido = null; };

export default function EtiquetaHotel({ tenantId, nombre, activa, onClick }) {
  const contenido = (
    <>
      <span className="etiqueta-punto" style={{ background: colorHotel(tenantId) }} aria-hidden="true" />
      {nombre ?? tenantId}
    </>
  );
  if (!onClick) return <span className="etiqueta-hotel">{contenido}</span>;
  return (
    <button type="button" className={`etiqueta-hotel etiqueta-filtro ${activa ? 'activa' : ''}`}
            aria-pressed={!!activa} onClick={onClick}>
      {contenido}
    </button>
  );
}
