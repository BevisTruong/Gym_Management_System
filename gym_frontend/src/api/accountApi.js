import { apiRequest, queryString } from './apiClient.js';
const root = '/accounts';
export const accountApi = {
  list: (filters = {}) => apiRequest(`${root}${queryString(filters)}`),
  create: (account) => apiRequest(root, { method: 'POST', body: account }),
  setStatus: (id, status) => apiRequest(`${root}/${id}/status`, { method: 'PATCH', body: { status } }),
  resetPassword: (id) => apiRequest(`${root}/${id}/reset-password`, { method: 'POST' }),
};
