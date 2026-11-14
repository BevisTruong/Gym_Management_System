const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'live-app.js'), 'utf8');

function appWith(apiOverrides = {}) {
  const app = { innerHTML: '', addEventListener() {} };
  const modal = {
    innerHTML: '', open: false,
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
    console,
  });
  vm.runInContext(source, context);
  return { context, app, modal, toast, api };
}

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
