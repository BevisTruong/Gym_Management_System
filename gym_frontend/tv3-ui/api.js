(function (root) {
  'use strict';

  const TOKEN_KEY = 'gym_access_token';
  const API_BASE = root.GYM_API_BASE_URL
    || (location.port === '8080' ? `${location.origin}/api` : 'http://localhost:8080/api');

  class ApiError extends Error {
    constructor(message, status, payload) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
      this.payload = payload;
    }
  }

  function token() {
    return sessionStorage.getItem(TOKEN_KEY);
  }

  async function request(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (options.body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    if (token()) {
      headers.Authorization = `Bearer ${token()}`;
    }

    let response;
    try {
      response = await fetch(`${API_BASE}${path}`, {
        ...options,
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
      });
    } catch (error) {
      throw new ApiError('Không kết nối được backend. Hãy kiểm tra backend và địa chỉ API.', 0, error);
    }

    const payload = response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok || payload?.success === false) {
      if (response.status === 401) {
        sessionStorage.removeItem(TOKEN_KEY);
      }
      throw new ApiError(payload?.message || `Yêu cầu thất bại (${response.status})`, response.status, payload);
    }
    return Object.hasOwn(payload || {}, 'data') ? payload.data : payload;
  }

  const get = path => request(path);
  const post = (path, body) => request(path, { method: 'POST', body });
  const put = (path, body) => request(path, { method: 'PUT', body });
  const patch = (path, body) => request(path, { method: 'PATCH', body });
  const primaryKey = (rows, key) => (rows || []).map(row => ({ ...row, id: Number(row[key]) }));
  const normalizeSession = row => ({
    ...row,
    id: Number(row.session_id),
    start_time: String(row.start_time || '').slice(0, 5),
    end_time: String(row.end_time || '').slice(0, 5),
    note: row.note || '',
  });
  const normalizeMembership = row => ({
    ...row,
    id: Number(row.membership_id),
    member_id: Number(row.member_id),
    plan_id: Number(row.plan_id),
  });

  async function login(username, password) {
    const result = await post('/auth/login', { username, password });
    sessionStorage.setItem(TOKEN_KEY, result.accessToken);
    return result.user;
  }

  async function currentUser() {
    return get('/auth/me');
  }

  async function changePassword(currentPassword, newPassword, confirmPassword) {
    const result = await post('/auth/change-password', {
      currentPassword,
      newPassword,
      confirmPassword,
    });
    sessionStorage.setItem(TOKEN_KEY, result.accessToken);
    return result.user;
  }

  async function logout() {
    try {
      await post('/auth/logout', {});
    } finally {
      sessionStorage.removeItem(TOKEN_KEY);
    }
  }

  async function loadStaffState(user) {
    const [members, plans, trainers, sessions, expirations, accounts] = await Promise.all([
      get('/members?limit=200'),
      get('/plans'),
      get('/trainers?limit=200'),
      get('/sessions?limit=500'),
      get('/expirations'),
      user.role === 'Admin' ? get('/accounts') : Promise.resolve([]),
    ]);
    let memberships;
    try {
      memberships = await get('/memberships?limit=500');
    } catch (error) {
      if (error.status !== 404) throw error;
      const rowsByMember = await Promise.all(members.map(item => get(`/memberships/member/${item.member_id}`)));
      memberships = rowsByMember.flat();
    }
    return {
      members: primaryKey(members, 'member_id'),
      plans: primaryKey(plans, 'plan_id'),
      trainers: primaryKey(trainers, 'trainer_id'),
      sessions: (sessions || []).map(normalizeSession),
      memberships: (memberships || []).map(normalizeMembership),
      expirations: (expirations || []).map(normalizeMembership),
      accounts: primaryKey(accounts, 'account_id'),
    };
  }

  async function loadPortalState(user) {
    if (user.role === 'Member') {
      const portal = await get('/portal/member');
      const profile = { ...portal.profile, id: Number(portal.profile.member_id) };
      const memberships = (portal.memberships || []).map(normalizeMembership);
      const plans = [];
      memberships.forEach(row => {
        const nested = row.membership_plan;
        if (nested) {
          plans.push({ ...nested, id: Number(row.plan_id), plan_id: Number(row.plan_id) });
        }
      });
      const sessions = (portal.sessions || []).map(normalizeSession);
      const trainers = sessions
        .filter(row => row.trainer)
        .map(row => ({ ...row.trainer, id: Number(row.trainer_id), trainer_id: Number(row.trainer_id) }));
      return {
        members: [profile],
        plans: [...new Map(plans.map(row => [row.id, row])).values()],
        trainers: [...new Map(trainers.map(row => [row.id, row])).values()],
        sessions,
        memberships,
        expirations: [],
        accounts: [],
      };
    }

    const sessions = (await get('/portal/trainer/schedule')).map(normalizeSession);
    const members = sessions
      .filter(row => row.member)
      .map(row => ({ ...row.member, id: Number(row.member_id), member_id: Number(row.member_id) }));
    return {
      members: [...new Map(members.map(row => [row.id, row])).values()],
      plans: [],
      trainers: [{ id: Number(user.trainerId), trainer_id: Number(user.trainerId), full_name: user.fullName, status: 'Active' }],
      sessions,
      memberships: [],
      expirations: [],
      accounts: [],
    };
  }

  async function loadState(user) {
    return ['Admin', 'Staff'].includes(user.role) ? loadStaffState(user) : loadPortalState(user);
  }

  root.GymApi = {
    ApiError,
    request,
    get,
    post,
    put,
    patch,
    login,
    currentUser,
    changePassword,
    logout,
    availableTrainers: slot => post('/sessions/available-trainers', slot),
    loadState,
    clearToken: () => sessionStorage.removeItem(TOKEN_KEY),
    hasToken: () => Boolean(token()),
    getBaseUrl: () => API_BASE,
  };
})(window);
