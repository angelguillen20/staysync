/*
 * Humo: cada pantalla se renderiza sin romperse (un error al renderizar deja la página en
 * blanco y el build no lo detecta). Los servicios devuelven datos mínimos de dos hoteles.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { userId: 7, rol: 'SUPER_ADMIN', nombreCompleto: 'Ana Rojas', tenantId: 'a' },
    hasRole: () => true,
  }),
}));
vi.mock('../context/ThemeContext', () => ({ useMarca: () => ({ marca: { nombre: 'Hotel Uno' }, setMarca: () => {} }) }));

const habitacion = { id: 1, numero: '101', estado: 'OCUPADA', tipoNombre: 'Doble', capacidad: 2, precioPorNoche: 50000, imagenes: [], hotelId: 'a', hotelNombre: 'Hotel Uno' };
const reserva = { id: 1, codigo: 'RES-1', habitacionId: 1, habitacionNumero: '101', usuarioId: 7, estado: 'CHECKIN',
  fechaEntrada: '2026-11-01', fechaSalida: '2026-11-03', numHuespedes: 2, precioTotal: 100000, huespedNombre: 'Ana Rojas', hotelId: 'a', hotelNombre: 'Hotel Uno' };

vi.mock('../services/habitacionesService', () => ({
  getHabitaciones: async () => [habitacion], getDisponibles: async () => [habitacion],
  getTiposHabitacion: async () => [], getAmenidades: async () => [], cambiarEstado: async () => ({}),
  crearHabitacion: async () => ({}), desactivarHabitacion: async () => ({}), agregarImagen: async () => ({}), eliminarImagen: async () => ({}),
}));
vi.mock('../services/reservasService', () => ({
  getReservas: async () => [reserva], getReservasRango: async () => [reserva], getMisReservas: async () => [reserva],
  getReservasHoy: async () => ({ pendientesCheckin: [], pendientesCheckout: [reserva] }),
  cambiarEstadoReserva: async () => ({}), getCuenta: async () => ({}), pagarSaldo: async () => ({}), modificarReserva: async () => ({}),
}));
vi.mock('../services/usuariosService', () => ({
  getHoteles: async () => [{ tenantId: 'a', nombre: 'Hotel Uno', slug: 'uno' }, { tenantId: 'b', nombre: 'Hotel Dos', slug: 'dos' }],
  getUsuarios: async () => ({ content: [], totalElements: 3 }), getHuespedes: async () => [], registrarHuesped: async () => ({}),
}));
vi.mock('../services/dashboardService', () => ({ getClima: async () => null, getDashboard: async () => ({}) }));
vi.mock('../services/serviciosService', () => ({ getCatalogo: async () => [], getCategorias: async () => [] }));

const montar = (Pantalla) => render(<MemoryRouter><Pantalla /></MemoryRouter>);

describe('pantallas', () => {
  it('panel de plataforma: una fila por hotel', async () => {
    const { default: PlataformaAdmin } = await import('../pages/recepcion/PlataformaAdmin');
    montar(PlataformaAdmin);
    expect(await screen.findByText('Hotel Dos')).toBeTruthy();
    expect(screen.getAllByText('Hotel Uno').length).toBeGreaterThan(0);
  });

  it('menú: la plataforma ve Usuarios, Marca del hotel y Hoteles', async () => {
    const { default: Sidebar } = await import('../components/common/Sidebar');
    montar(Sidebar);
    for (const t of ['Plataforma', 'Usuarios', 'Marca del hotel', 'Hoteles']) expect(screen.getByText(t)).toBeTruthy();
  });

  it.each([
    ['inicio del huésped', '../pages/huesped/DashboardHuesped', 'Hola, Ana'],
    ['reservas (con selector de hotel)', '../pages/recepcion/ReservasRecepcion', 'Todos los hoteles'],
    ['habitaciones (con selector de hotel)', '../pages/recepcion/HabitacionesRecepcion', 'Todos los hoteles'],
    ['calendario', '../pages/recepcion/CalendarioRecepcion', 'Calendario de ocupación'],
    ['reportes', '../pages/recepcion/ReportesRecepcion', 'Reportes'],
    ['check-in y check-out', '../pages/recepcion/CheckinRecepcion', 'Check-in / Check-out'],
    ['catálogo de servicios', '../pages/recepcion/ServiciosAdmin', 'Servicios del hotel'],
  ])('%s se renderiza', async (_nombre, ruta, texto) => {
    const { default: Pantalla } = await import(ruta);
    montar(Pantalla);
    expect((await screen.findAllByText(texto)).length).toBeGreaterThan(0);
  });
});
