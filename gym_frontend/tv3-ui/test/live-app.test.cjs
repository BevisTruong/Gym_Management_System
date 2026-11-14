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
