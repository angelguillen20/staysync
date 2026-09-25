import apiClient from './apiClient';

export const getDashboard = () => apiClient.get('/dashboard').then(r => r.data);
export const getClima     = () => apiClient.get('/clima').then(r => r.data || null);
