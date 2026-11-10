import { apiRequest } from './apiClient.js';
export const membershipApi = {
  forMember: (memberId) => apiRequest(`/memberships/member/${memberId}`),
  register: (membership) => apiRequest('/memberships/register', { method: 'POST', body: membership }),
  renew: (membership) => apiRequest('/memberships/renew', { method: 'POST', body: membership }),
  expiring: () => apiRequest('/expirations'),
  myMemberships: async () => (await apiRequest('/portal/member')).memberships,
};
