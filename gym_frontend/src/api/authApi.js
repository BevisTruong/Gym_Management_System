import { apiRequest, tokenStore } from './apiClient.js';

export const authApi = {
  async login(credentials) {
    const result = await apiRequest('/auth/login', { method: 'POST', body: credentials });
    tokenStore.set(result.accessToken);
    return result;
  },
  async bootstrap({ username, password, fullName, bootstrapToken }) {
    const result = await apiRequest('/auth/bootstrap', {
      method: 'POST', body: { username, password, fullName },
      headers: { 'X-Bootstrap-Token': bootstrapToken },
    });
    tokenStore.set(result.accessToken);
    return result;
  },
  me: () => apiRequest('/auth/me'),
  async changePassword(currentPassword, newPassword, confirmPassword) {
    const result = await apiRequest('/auth/change-password', {
      method: 'POST', body: { currentPassword, newPassword, confirmPassword },
    });
    tokenStore.set(result.accessToken);
    return result;
  },
  async logout() {
    try { await apiRequest('/auth/logout', { method: 'POST' }); } finally { tokenStore.clear(); }
  },
};
