const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'live-app.js'), 'utf8');

function appWith(apiOverrides = {}) {
  const app = { innerHTML: '', listeners: {}, addEventListener(type, handler) { this.listeners[type] = handler; } };
  const modal = {
    innerHTML: '', open: false, listeners: {},
    addEventListener(type, handler) { this.listeners[type] = handler; },
    showModal() { this.open = true; },
    close() { this.open = false; },
    querySelector() { return null; },
  };
  const toast = { textContent: '', style: {} };
  const api = {
    hasToken: () => false,
    getBaseUrl: () => 'http://localhost:8080/api',
    loadState: async () => ({ members: [], trainers: [], plans: [], sessions: [], memberships: [], accounts: [], expirations: [] }),
    ...apiOverrides,
  };
  const context = vm.createContext({
    GymApi: api,
    document: { querySelector: selector => ({ '#app': app, '#modal': modal, '#toast': toast })[selector] },
    setTimeout: () => 1,
    clearTimeout() {},
    FormData: class { constructor(form) { return Object.entries(form.values); } },
    console,
  });
  vm.runInContext(source, context);
  return { context, app, modal, toast, api };
}

test('dialog form submission calls the account API and prevents navigation', async () => {
  const calls = [];
  const state = appWith({
    post: async (url, body) => {
      calls.push({ url, body });
      return { temporaryPassword: 'test-temporary-password' };
    },
  });
  vm.runInContext("user = { role: 'Admin', fullName: 'Admin', mustChangePassword: false }", state.context);
  state.modal.open = true;
  let prevented = false;
  await state.modal.listeners.submit({
    target: { id: 'account-form', dataset: {}, matches: () => true,
      values: { full_name: 'Staff', username: 'staff', role: 'Staff' } },
    preventDefault() { prevented = true; },
  });
  assert.equal(prevented, true);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/accounts');
  assert.equal(calls[0].body.username, 'staff');
  assert.match(state.modal.innerHTML, /test-temporary-password/);
});

test('dialog close buttons close the modal', async () => {
  const state = appWith();
  state.modal.open = true;
  const button = { dataset: { action: 'close' } };
  await state.modal.listeners.click({ target: { closest: selector => selector === '[data-action]' ? button : null } });
  assert.equal(state.modal.open, false);
});

test('active filter excludes inactive members, trainers, and locked accounts', () => {
  const state = appWith();
  vm.runInContext(`
    user = { role: 'Admin', fullName: 'Admin', accountId: 99, mustChangePassword: false };
    filter = 'Active';
    data.members = [{ id: 1, full_name: 'Active member', status: 'Active' }, { id: 2, full_name: 'Inactive member', status: 'Inactive' }];
    data.trainers = [{ id: 1, full_name: 'Active trainer', status: 'Active' }, { id: 2, full_name: 'Inactive trainer', status: 'Inactive' }];
    data.accounts = [{ id: 1, full_name: 'Active account', status: 'Active' }, { id: 2, full_name: 'Locked account', status: 'Locked' }];
  `, state.context);
  for (const [page, active, inactive] of [
    ['memberPage()', 'Active member', 'Inactive member'],
    ['trainerPage()', 'Active trainer', 'Inactive trainer'],
    ['accountPage()', 'Active account', 'Locked account'],
  ]) {
    const html = vm.runInContext(page, state.context);
    assert.match(html, new RegExp(active));
    assert.doesNotMatch(html, new RegExp(inactive));
  }
});

test('membership filter continues to use membership status', () => {
  const state = appWith();
  vm.runInContext(`
    filter = 'Active';
    data.memberships = [
      { membership_id: 1, member_id: 1, plan_id: 1, membershipStatus: 'Active' },
      { membership_id: 2, member_id: 2, plan_id: 1, membershipStatus: 'Expired' }
    ];
  `, state.context);
  const html = vm.runInContext('membershipPage()', state.context);
  assert.match(html, /Member 1/);
  assert.doesNotMatch(html, /Member 2/);
});

function trainerScheduleFixture() {
  const state = appWith();
  state.context.Date = class extends Date {
    constructor(...args) { super(...(args.length ? args : [2026, 10, 18, 12, 0, 0])); }
    static now() { return new Date(2026, 10, 18, 12, 0, 0).getTime(); }
  };
  vm.runInContext(`
    user = { role: 'Trainer', fullName: 'Trainer', trainerId: 1, mustChangePassword: false };
    page = 'trainer';
    data.members = [
      { member_id: 1, full_name: 'Future One' }, { member_id: 2, full_name: 'Future Two' },
      { member_id: 3, full_name: 'Past Member' }, { member_id: 4, full_name: 'Cancelled Member' }
    ];
    data.sessions = [
      { member_id: 2, session_date: '2026-11-19', start_time: '10:00', end_time: '11:00', status: 'Scheduled', displayStatus: 'Scheduled' },
      { member_id: 1, session_date: '2026-11-19', start_time: '08:00', end_time: '09:00', status: 'Scheduled', displayStatus: 'Scheduled' },
      { member_id: 3, session_date: '2026-11-16', start_time: '08:00', end_time: '09:00', status: 'Scheduled', displayStatus: 'Completed' },
      { member_id: 4, session_date: '2026-11-20', start_time: '08:00', end_time: '09:00', status: 'Cancelled', displayStatus: 'Cancelled' }
    ];
  `, state.context);
  return state;
}

test('trainer schedule defaults to upcoming scheduled sessions grouped by date and time', () => {
  const state = trainerScheduleFixture();
  const html = vm.runInContext('trainerSchedulePage()', state.context);
  assert.match(html, /Future One/);
  assert.match(html, /Future Two/);
  assert.doesNotMatch(html, /Past Member|Cancelled Member/);
  assert.ok(html.indexOf('Future One') < html.indexOf('Future Two'));
  assert.equal((html.match(/class="card schedule-day"/g) || []).length, 1);
});

test('trainer can filter past or cancelled sessions by week, date, and status', () => {
  const state = trainerScheduleFixture();
  vm.runInContext("trainerScheduleRange = 'week'; trainerScheduleStatus = 'Completed'", state.context);
  let html = vm.runInContext('trainerSchedulePage()', state.context);
  assert.match(html, /Past Member/);
  assert.doesNotMatch(html, /Future One|Cancelled Member/);

  state.app.listeners.change({ target: { id: 'trainer-range', value: 'date' } });
  state.app.listeners.change({ target: { id: 'trainer-date', value: '2026-11-20' } });
  state.app.listeners.change({ target: { id: 'trainer-status', value: 'Cancelled' } });
  html = state.app.innerHTML;
  assert.match(html, /Cancelled Member/);
  assert.doesNotMatch(html, /Past Member|Future One/);
});

test('member schedule groups sessions and offers date and status filters without management buttons', () => {
  const state = trainerScheduleFixture();
  vm.runInContext(`
    user = { role: 'Member', fullName: 'Member', mustChangePassword: false };
    data.trainers = [{ trainer_id: 1, full_name: 'Coach Example' }];
    data.sessions.forEach(item => { item.trainer_id = 1; });
  `, state.context);
  const html = vm.runInContext('sessionPage(true)', state.context);
  assert.match(html, /My training schedule/);
  assert.match(html, /Coach Example/);
  assert.match(html, /trainer-range/);
  assert.match(html, /trainer-status/);
  assert.doesNotMatch(html, /Past Member|Cancelled Member|data-action="edit-session"|data-action="cancel-session"/);
  assert.equal((html.match(/class="card schedule-day"/g) || []).length, 1);
});

test('staff schedule retains creation, editing, cancellation, and search', () => {
  const state = trainerScheduleFixture();
  vm.runInContext(`
    user = { role: 'Staff', fullName: 'Staff', mustChangePassword: false };
    page = 'sessions';
    data.trainers = [{ trainer_id: 1, full_name: 'Coach Example' }];
    data.sessions.forEach((item, i) => { item.trainer_id = 1; item.id = i + 1; });
  `, state.context);
  let html = vm.runInContext('sessionPage()', state.context);
  assert.match(html, /data-action="new-session"/);
  assert.match(html, /data-action="edit-session"/);
  assert.match(html, /data-action="cancel-session"/);
  assert.match(html, /Coach Example/);
  vm.runInContext("query = 'Future One'", state.context);
  html = vm.runInContext('sessionPage()', state.context);
  assert.match(html, /Future One/);
  assert.doesNotMatch(html, /Future Two/);
  vm.runInContext("trainerScheduleRange = 'all'; trainerScheduleStatus = 'Completed'; query = ''", state.context);
  html = vm.runInContext('sessionPage()', state.context);
  assert.match(html, /Past Member/);
  assert.doesNotMatch(html, /data-action="edit-session"|data-action="cancel-session"/);
});

test('first login opens password page without requesting protected data', async () => {
  let loads = 0;
  const { context, app } = appWith({
    login: async () => ({ role: 'Member', fullName: 'Member', mustChangePassword: true }),
    loadState: async () => { loads++; throw new Error('unexpected load'); },
  });
  await vm.runInContext("signIn('member', 'temporary')", context);
  assert.equal(loads, 0);
  assert.match(app.innerHTML, /Set your password/);
});

test('restoring a temporary-password session does not request protected data', async () => {
  let loads = 0;
  const state = appWith({
    currentUser: async () => ({ role: 'Trainer', fullName: 'Trainer', mustChangePassword: true }),
    loadState: async () => { loads++; throw new Error('unexpected load'); },
  });
  state.api.hasToken = () => true;
  await vm.runInContext('restoreSession()', state.context);
  assert.equal(loads, 0);
  assert.match(state.app.innerHTML, /Set your password/);
});

test('created account password remains visible if status update and refresh fail', async () => {
  const state = appWith({ loadState: async () => { throw new Error('refresh failed'); } });
  vm.runInContext("user = { role: 'Admin', fullName: 'Admin', mustChangePassword: false }", state.context);
  state.modal.open = true;
  state.context.failStatus = async () => {
    assert.equal(state.modal.open, true);
    assert.match(state.modal.innerHTML, /test-temporary-password/);
    throw new Error('status failed');
  };
  await vm.runInContext("finishAccountCreation('Member account created', { temporaryPassword: 'test-temporary-password' }, 'members', failStatus)", state.context);
  assert.equal(state.modal.open, true);
  assert.match(state.modal.innerHTML, /test-temporary-password/);
  assert.match(state.toast.textContent, /status failed/);
  assert.match(state.toast.textContent, /refresh failed/);
});

test('created account password remains visible after a successful refresh', async () => {
  const state = appWith();
  vm.runInContext("user = { role: 'Admin', fullName: 'Admin', mustChangePassword: false }", state.context);
  state.modal.open = true;
  await vm.runInContext("finishAccountCreation('Account created', { temporaryPassword: 'test-temporary-password' }, 'accounts')", state.context);
  assert.equal(state.modal.open, true);
  assert.match(state.modal.innerHTML, /test-temporary-password/);
});
