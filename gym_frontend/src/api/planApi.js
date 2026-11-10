import { apiRequest, queryString } from './apiClient.js';
const root = '/plans';
export const planApi = {
  list: (filters = {}) => apiRequest(`${root}${queryString(filters)}`),
  get: (id) => apiRequest(`${root}/${id}`),
  create: (plan) => apiRequest(root, { method: 'POST', body: plan }),
  update: (id, plan) => apiRequest(`${root}/${id}`, { method: 'PUT', body: plan }),
  setStatus: (id, status) => apiRequest(`${root}/${id}/status`, { method: 'PATCH', body: { status } }),
};
