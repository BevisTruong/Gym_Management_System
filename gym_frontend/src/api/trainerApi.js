import { apiRequest, queryString } from './apiClient.js';
const root = '/trainers';
export const trainerApi = {
  list: (filters = {}) => apiRequest(`${root}${queryString(filters)}`),
  get: (id) => apiRequest(`${root}/${id}`),
  create: (trainer) => apiRequest(root, { method: 'POST', body: trainer }),
  update: (id, trainer) => apiRequest(`${root}/${id}`, { method: 'PUT', body: trainer }),
  setStatus: (id, status) => apiRequest(`${root}/${id}/status`, { method: 'PATCH', body: { status } }),
};
