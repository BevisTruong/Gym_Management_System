import { apiRequest, queryString } from './apiClient.js';
const root = '/sessions';
export const sessionApi = {
  list: (filters = {}) => apiRequest(`${root}${queryString(filters)}`),
  create: (session) => apiRequest(root, { method: 'POST', body: session }),
  update: (id, session) => apiRequest(`${root}/${id}`, { method: 'PUT', body: session }),
  cancel: (id) => apiRequest(`${root}/${id}/cancel`, { method: 'PATCH' }),
  availableTrainers: (slot) => apiRequest(`${root}/available-trainers`, { method: 'POST', body: slot }),
  myTrainerSchedule: () => apiRequest('/portal/trainer/schedule'),
  myMemberSchedule: async () => (await apiRequest('/portal/member')).sessions,
};
