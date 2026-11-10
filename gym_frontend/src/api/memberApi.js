import { apiRequest, queryString } from './apiClient.js';
const root = '/members';
export const memberApi = {
  list: (filters = {}) => apiRequest(`${root}${queryString(filters)}`),
  get: (id) => apiRequest(`${root}/${id}`),
  create: (member) => apiRequest(root, { method: 'POST', body: member }),
  update: (id, member) => apiRequest(`${root}/${id}`, { method: 'PUT', body: member }),
  setStatus: (id, status) => apiRequest(`${root}/${id}/status`, { method: 'PATCH', body: { status } }),
};
