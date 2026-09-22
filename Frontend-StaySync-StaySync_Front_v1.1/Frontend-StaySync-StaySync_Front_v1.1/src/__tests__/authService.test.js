import { describe, it, expect, vi, beforeEach } from 'vitest';
import { login, register } from '../services/authService';

/* ── Mock the axios client ──────────────────────────────────── */
vi.mock('../services/apiClient', () => ({
  default: {
    post: vi.fn(),
    get:  vi.fn(),
  },
}));

import apiClient from '../services/apiClient';

const MOCK_AUTH_RESPONSE = {
  accessToken:   'eyJ.test.jwt',
  refreshToken:  'refresh-token-abc',
  tokenType:     'Bearer',
  expiresIn:     86400000,
  userId:        1,
  nombreCompleto:'Juan García',
  email:         'juan@staysync.com',
  rol:           'RECEPCIONISTA',
};

describe('authService', () => {
  beforeEach(() => vi.clearAllMocks());

  /* ── login ───────────────────────────────────────────────── */
  describe('login()', () => {
    it('retorna el payload de autenticación en una respuesta exitosa', async () => {
      apiClient.post.mockResolvedValue({ data: MOCK_AUTH_RESPONSE });

      const result = await login('juan@staysync.com', 'Pass@123');

      expect(apiClient.post).toHaveBeenCalledOnce();
      expect(apiClient.post).toHaveBeenCalledWith('/auth/login', {
        email:    'juan@staysync.com',
        password: 'Pass@123',
      });

      expect(result.accessToken).toBe('eyJ.test.jwt');
      expect(result.rol).toBe('RECEPCIONISTA');
      expect(result.email).toBe('juan@staysync.com');
    });

    it('lanza el error recibido del servidor cuando las credenciales son inválidas', async () => {
      const serverError = {
        response: {
          status: 401,
          data:   { message: 'Credenciales incorrectas' },
        },
      };
      apiClient.post.mockRejectedValue(serverError);

      await expect(login('mal@email.com', 'wrongpass')).rejects.toEqual(serverError);
      expect(apiClient.post).toHaveBeenCalledOnce();
    });

    it('lanza error de red cuando el BFF no está disponible', async () => {
      const networkError = new Error('Network Error');
      apiClient.post.mockRejectedValue(networkError);

      await expect(login('test@test.com', 'pass')).rejects.toThrow('Network Error');
    });
  });

  /* ── register ────────────────────────────────────────────── */
  describe('register()', () => {
    it('llama al endpoint correcto con todos los campos requeridos', async () => {
      const mockUser = { id: 2, email: 'nuevo@test.com', rol: 'HUESPED' };
      apiClient.post.mockResolvedValue({ data: mockUser });

      const payload = {
        nombre:   'María',
        apellido: 'López',
        email:    'nuevo@test.com',
        password: 'Secure@Pass1',
        telefono: '+573001234567',
      };

      const result = await register(payload);

      expect(apiClient.post).toHaveBeenCalledWith('/auth/registro', {
        ...payload,
        rol: 'HUESPED',
      });
      expect(result.email).toBe('nuevo@test.com');
    });
  });
});
