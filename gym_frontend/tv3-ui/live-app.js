'use strict';

const API = GymApi;
const app = document.querySelector('#app');
const modal = document.querySelector('#modal');
const toastNode = document.querySelector('#toast');
const emptyState = () => ({ members: [], plans: [], trainers: [], memberships: [], sessions: [], accounts: [], expirations: [] });
let data = emptyState();
let user = null;
let page = 'home';
let query = '';
let filter = '';
let toastTimer;

const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
const idKey = { members: 'member_id', plans: 'plan_id', trainers: 'trainer_id', memberships: 'membership_id', sessions: 'session_id', accounts: 'account_id' };
const keyed = (rows, key) => (rows || []).map(row => ({ ...row, id: Number(row[key]) }));
const money = value => new Intl.NumberFormat('en-US', { maximumFractionDigits: 0 }).format(Number(value || 0)) + ' VND';
const date = value => value ? new Date(`${value}T00:00:00`).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) : '—';
const today = () => { const d = new Date(); return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10); };
const tomorrow = () => { const d = new Date(); d.setDate(d.getDate() + 1); return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10); };
const row = (name, id) => data[name].find(item => Number(item[idKey[name]] || item.id) === Number(id));
const member = id => row('members', id);
const trainer = id => row('trainers', id);
const plan = id => row('plans', id);
const statusOfMembership = item => item.membershipStatus || (today() < item.start_date ? 'Upcoming' : today() > item.end_date ? 'Expired' : 'Active');

function toast(message) {
  toastNode.textContent = message;
  toastNode.style.display = 'block';
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { toastNode.style.display = 'none'; }, 4000);
}

function accountFromUser(value) {
  return {
    id: Number(value.accountId),
    account_id: Number(value.accountId),
    username: value.username,
    full_name: value.fullName,
    role: value.role,
    must_change_password: Boolean(value.mustChangePassword),
    member_id: value.memberId == null ? null : Number(value.memberId),
    trainer_id: value.trainerId == null ? null : Number(value.trainerId),
  };
}

async function loadData() {
  data = await API.loadState(user);
  data.accounts = keyed(data.accounts, 'account_id');
  data.memberships = (data.memberships || []).map(item => ({ ...item, id: Number(item.membership_id), member_id: Number(item.member_id), plan_id: Number(item.plan_id) }));
  data.expirations = (data.expirations || []).map(item => ({ ...item, id: Number(item.membership_id), member_id: Number(item.member_id), plan_id: Number(item.plan_id) }));
  data.sessions = (data.sessions || []).map(item => ({ ...item, id: Number(item.session_id), member_id: Number(item.member_id), trainer_id: Number(item.trainer_id), start_time: String(item.start_time || '').slice(0, 5), end_time: String(item.end_time || '').slice(0, 5) }));
}

async function signIn(username, password) {
  user = await API.login(username, password);
  data = emptyState();
  if (!user.mustChangePassword) await loadData();
  page = user.mustChangePassword ? 'password' : user.role === 'Member' ? 'member' : user.role === 'Trainer' ? 'trainer' : 'home';
  render();
}

async function restoreSession() {
  if (!API.hasToken()) return render();
  try {
    user = await API.currentUser();
    data = emptyState();
    if (!user.mustChangePassword) await loadData();
    page = user.mustChangePassword ? 'password' : user.role === 'Member' ? 'member' : user.role === 'Trainer' ? 'trainer' : 'home';
  } catch (error) {
    API.clearToken();
    user = null;
    toast(error.message);
  }
  render();
}

function isStaff() { return ['Admin', 'Staff'].includes(user?.role); }
function isAdmin() { return user?.role === 'Admin'; }
function allowed(target) {
  if (user?.mustChangePassword) return target === 'password';
  if (target === 'home' || target === 'password') return true;
  if (isStaff()) return ['members', 'plans', 'memberships', 'trainers', 'sessions', 'expirations'].includes(target) || (isAdmin() && target === 'accounts');
  return user?.role === 'Member' ? target === 'member' : user?.role === 'Trainer' && target === 'trainer';
}

function button(label, action, id = '', primary = false) {
  return `<button class="btn ${primary ? 'primary' : 'small'}" data-action="${action}" ${id === '' ? '' : `data-id="${esc(id)}"`}>${label}</button>`;
}

function badge(value) {
  const tone = ['Expired', 'Locked', 'Cancelled'].includes(value) ? 'red' : ['Inactive', 'Unavailable'].includes(value) ? 'gray' : value === 'Upcoming' ? 'blue' : '';
  return `<span class="badge ${tone}">${esc(value || '—')}</span>`;
}

function heading(title, description = '', action = '') {
  return `<div class="page-heading"><div><h1>${esc(title)}</h1><p class="subtext">${esc(description)}</p></div>${action}</div>`;
}

function formField(label, name, value = '', type = 'text', required = false, options = null) {
  const control = options
    ? `<select id="${name}" name="${name}" ${required ? 'required' : ''}>${options.map(([key, text]) => `<option value="${esc(key)}" ${String(value) === String(key) ? 'selected' : ''}>${esc(text)}</option>`).join('')}</select>`
    : type === 'textarea'
      ? `<textarea id="${name}" name="${name}" ${required ? 'required' : ''}>${esc(value)}</textarea>`
      : `<input id="${name}" name="${name}" type="${type}" value="${esc(value)}" ${required ? 'required' : ''}>`;
  return `<div class="field"><label for="${name}">${esc(label)}${required ? ' *' : ''}</label>${control}</div>`;
}

function recordsTable(headers, records, cells, emptyText = 'No records found.') {
  return `<div class="table-wrap"><table><thead><tr>${headers.map(item => `<th scope="col">${esc(item)}</th>`).join('')}</tr></thead><tbody>${records.length ? records.map(cells).join('') : `<tr><td colspan="${headers.length}">${esc(emptyText)}</td></tr>`}</tbody></table></div><div class="table-foot">${records.length} record(s)</div>`;
}

function filtered(list, field = 'full_name') {
  return list.filter(item => {
    const text = [item[field], item.phone, item.email, item.id, item.username].join(' ').toLowerCase();
    return (!query || text.includes(query.toLowerCase())) && (!filter || item.status === filter);
  });
}

function searchControls(statusOptions = []) {
  return `<div class="toolbar filters"><input id="search-records" class="search" type="search" placeholder="Search" value="${esc(query)}" aria-label="Search records">${statusOptions.length ? `<select id="filter-status" aria-label="Filter by status"><option value="">All statuses</option>${statusOptions.map(value => `<option value="${value}" ${filter === value ? 'selected' : ''}>${value}</option>`).join('')}</select>` : ''}</div>`;
}

function memberPage() {
  const records = filtered(data.members);
  return heading('Members', 'Manage member profiles and account status.', button('Add member', 'new-member', '', true)) + `<section class="card">${searchControls(['Active', 'Inactive'])}${recordsTable(['Member', 'Phone / Email', 'Status', ''], records, item => `<tr><td><strong>${esc(item.full_name)}</strong><small>M-${item.id}</small></td><td>${esc(item.phone)}<small>${esc(item.email || '—')}</small></td><td>${badge(item.status)}</td><td>${button('Edit', 'edit-member', item.id)}</td></tr>`)}</section>`;
}

function planPage() {
  return heading('Membership plans', 'Plans and prices are loaded from Supabase through the backend.', isAdmin() ? button('Add plan', 'new-plan', '', true) : '') + `<div class="plan-grid">${data.plans.map(item => `<section class="card plan"><div class="eyebrow">${item.duration_months} month(s)</div><h2>${esc(item.name)}</h2><div class="price">${money(item.price)}</div><div class="benefits">${esc(item.benefits)}</div><div class="plan-foot">${badge(item.status)}${isAdmin() ? button('Edit', 'edit-plan', item.id) : ''}</div></section>`).join('') || '<section class="card card-pad">No plans found.</section>'}</div>`;
}

function membershipPage() {
  const records = [...data.memberships].filter(item => (!query || `${member(item.member_id)?.full_name || ''} ${member(item.member_id)?.phone || ''}`.toLowerCase().includes(query.toLowerCase())) && (!filter || statusOfMembership(item) === filter));
  return heading('Registrations', 'Review membership periods and register or renew.', button('Register / renew', 'new-membership', '', true)) + `<section class="card">${searchControls(['Upcoming', 'Active', 'Expired'])}${recordsTable(['Member / Plan', 'Period', 'Price', 'Status'], records, item => `<tr><td>${esc(member(item.member_id)?.full_name || `Member ${item.member_id}`)}<small>${esc(plan(item.plan_id)?.name || `Plan ${item.plan_id}`)} · MS-${item.id}</small></td><td>${date(item.start_date)}<small>to ${date(item.end_date)}</small></td><td>${money(item.price_at_registration)}</td><td>${badge(statusOfMembership(item))}</td></tr>`)}</section>`;
}

function trainerPage() {
  const records = filtered(data.trainers);
  return heading('Trainers', 'Manage trainer profiles and linked accounts.', button('Add trainer', 'new-trainer', '', true)) + `<section class="card">${searchControls(['Active', 'Inactive'])}${recordsTable(['Trainer', 'Specialization', 'Contact', 'Status', ''], records, item => `<tr><td><strong>${esc(item.full_name)}</strong><small>T-${item.id}</small></td><td>${esc(item.specialization || '—')}<small>${item.years_of_experience ?? '—'} years</small></td><td>${esc(item.phone)}<small>${esc(item.email)}</small></td><td>${badge(item.status)}</td><td>${button('Edit', 'edit-trainer', item.id)}</td></tr>`)}</section>`;
}

function sessionPage(portal = false) {
  const records = (portal ? data.sessions : filtered(data.sessions)).slice().sort((a, b) => `${a.session_date}${a.start_time}`.localeCompare(`${b.session_date}${b.start_time}`));
  const add = !portal ? button('Create session', 'new-session', '', true) : '';
  const cells = item => `<tr><td><strong>${date(item.session_date)}</strong><small>${item.start_time}–${item.end_time}</small></td><td>${esc(member(item.member_id)?.full_name || `Member ${item.member_id}`)}</td><td>${esc(trainer(item.trainer_id)?.full_name || `Trainer ${item.trainer_id}`)}</td><td>${esc(item.note || '—')}</td><td>${badge(item.displayStatus || item.status)}</td>${portal ? '' : `<td>${(item.displayStatus || item.status) === 'Scheduled' ? button('Edit', 'edit-session', item.id) + button('Cancel', 'cancel-session', item.id) : '—'}</td>`}</tr>`;
  return heading(portal ? 'My assigned schedule' : 'Training sessions', portal ? 'Sessions assigned to your trainer account.' : 'Create sessions and let the backend enforce scheduling rules.', add) + `<section class="card">${!portal ? searchControls() : ''}${recordsTable(['Date / time', 'Member', 'Trainer', 'Note', 'Status', ...(portal ? [] : [''])], records, cells)}</section>`;
}

function expirationPage() {
  return heading('Expiring memberships', 'Active within 7 days or expired within the last 30 days.') + `<section class="card">${recordsTable(['Member', 'Plan', 'End date', 'Days remaining', 'Status'], data.expirations, item => `<tr><td>${esc(item.member?.full_name || member(item.member_id)?.full_name || `Member ${item.member_id}`)}<small>${esc(item.member?.phone || member(item.member_id)?.phone || '')}</small></td><td>${esc(item.membership_plan?.name || plan(item.plan_id)?.name || '—')}</td><td>${date(item.end_date)}</td><td>${item.daysRemaining ?? '—'}</td><td>${badge(item.membershipStatus || statusOfMembership(item))}</td></tr>`)}</section>`;
}

function accountPage() {
  const records = filtered(data.accounts);
  return heading('Accounts', 'Admin-only account management.', button('Add account', 'new-account', '', true)) + `<section class="card">${searchControls(['Active', 'Locked'])}${recordsTable(['Account', 'Role', 'Linked profile', 'Status', ''], records, item => `<tr><td><strong>${esc(item.full_name)}</strong><small>${esc(item.username)}</small></td><td>${esc(item.role)}</td><td>${item.member_id ? `Member ${item.member_id}` : item.trainer_id ? `Trainer ${item.trainer_id}` : '—'}</td><td>${badge(item.status)}</td><td>${item.id === user.accountId ? 'Current account' : button(item.status === 'Active' ? 'Lock' : 'Unlock', 'toggle-account', item.id) + button('Reset password', 'reset-password', item.id)}</td></tr>`)}</section>`;
}

function memberPortal() {
  const profile = data.members[0];
  if (!profile) return heading('My membership', 'No linked member profile was returned by the backend.') + '<section class="card card-pad">Contact an administrator to link your account.</section>';
  return heading('My membership', 'Your profile, memberships and training sessions.') + `<section class="card card-pad"><h2>${esc(profile.full_name)}</h2><p>${esc(profile.phone)} · ${esc(profile.email || '—')}</p><p>${esc(profile.training_objective || 'No training objective')}</p></section><section class="card section-gap"><div class="card-head"><h2>Memberships</h2></div>${recordsTable(['Plan', 'Period', 'Price', 'Status'], data.memberships, item => `<tr><td>${esc(plan(item.plan_id)?.name || item.membership_plan?.name || 'Membership')}</td><td>${date(item.start_date)} to ${date(item.end_date)}</td><td>${money(item.price_at_registration)}</td><td>${badge(statusOfMembership(item))}</td></tr>`)}</section><section class="card section-gap">${sessionPage(true)}</section>`;
}

function homePage() {
  if (user.role === 'Member') return memberPortal();
  if (user.role === 'Trainer') return sessionPage(true);
  const upcoming = data.sessions.filter(item => (item.displayStatus || item.status) === 'Scheduled').slice(0, 5);
  return heading('Overview', 'Live data from the gym backend.') + `<div class="quick-grid"><section class="card card-pad"><h2>${data.members.length}</h2><p>Members</p></section><section class="card card-pad"><h2>${data.trainers.length}</h2><p>Trainers</p></section><section class="card card-pad"><h2>${data.expirations.length}</h2><p>Memberships to follow up</p></section></div><section class="card section-gap">${recordsTable(['Date / time', 'Member', 'Trainer', 'Note', 'Status'], upcoming, item => `<tr><td>${date(item.session_date)} ${item.start_time}</td><td>${esc(member(item.member_id)?.full_name || '—')}</td><td>${esc(trainer(item.trainer_id)?.full_name || '—')}</td><td>${esc(item.note || '—')}</td><td>${badge(item.displayStatus || item.status)}</td>`)}</section>`;
}

function pageContent() {
  if (user.mustChangePassword) page = 'password';
  if (!allowed(page)) page = 'home';
  switch (page) {
    case 'home': return homePage();
    case 'members': return memberPage();
    case 'plans': return planPage();
    case 'memberships': return membershipPage();
    case 'trainers': return trainerPage();
    case 'sessions': return sessionPage();
    case 'expirations': return expirationPage();
    case 'accounts': return accountPage();
    case 'member': return memberPortal();
    case 'trainer': return sessionPage(true);
    case 'password': return passwordPage();
    default: return homePage();
  }
}

function navigationButton(target, label) {
  if (!allowed(target)) return '';
  return `<button class="nav ${page === target ? 'active' : ''}" data-nav="${target}" ${page === target ? 'aria-current="page"' : ''}>${esc(label)}</button>`;
}

function renderLogin() {
  app.innerHTML = `<div class="login"><section class="login-art"><div class="brand"><span class="brand-mark">G</span><div>GYM<small>MANAGEMENT SYSTEM</small></div></div><div class="login-identity"><div class="circle" aria-hidden="true">G</div><h1>Gym management,<br>made simple.</h1></div></section><section class="login-panel"><h1>Welcome back</h1><p class="subtext">Sign in with your gym account.</p><form id="login-form"><div class="form-error" role="alert"></div>${formField('Username', 'username', '', 'text', true)}${formField('Password', 'password', '', 'password', true)}<button type="submit" class="btn primary">Sign in</button></form><p class="note">API: ${esc(API.getBaseUrl())}</p></section></div>`;
}

function render() {
  if (!user) return renderLogin();
  const links = ['home', 'members', 'plans', 'memberships', 'trainers', 'sessions', 'expirations', 'accounts', 'member', 'trainer', 'password'];
  const labels = { home: 'Overview', members: 'Members', plans: 'Membership plans', memberships: 'Registrations', trainers: 'Trainers', sessions: 'Training sessions', expirations: 'Expiring memberships', accounts: 'Accounts', member: 'My membership', trainer: 'My schedule', password: 'Change password' };
  app.innerHTML = `<div class="shell"><aside class="sidebar"><div class="brand"><span class="brand-mark">G</span><div>GYM<small>MANAGEMENT SYSTEM</small></div></div><nav aria-label="Main navigation"><div class="nav-label">Workspace</div>${links.map(target => navigationButton(target, labels[target])).join('')}<button class="nav" data-action="logout">Sign out</button></nav></aside><div class="workspace"><header class="topbar"><div class="breadcrumb">Workspace / <b>${esc(labels[page] || 'Overview')}</b></div><div class="user"><div class="username">${esc(user.fullName)}<small>${esc(user.role)}</small></div></div></header><main id="main" tabindex="-1">${pageContent()}</main></div></div>`;
}

function showDialog(title, content) {
  modal.innerHTML = `<div class="modal-top"><h2 id="modal-title">${esc(title)}</h2><button type="button" class="close" data-action="close" aria-label="Close">×</button></div>${content}`;
  modal.showModal();
  modal.querySelector('input,select,textarea,button')?.focus();
}

function showTemporaryPassword(title, password) {
  showDialog('Temporary password', `<div class="help-content"><p>${esc(title)}. Copy this password and give it to the user through a private channel. It will not be shown again.</p>${formField('Temporary password', 'temporary-password', password, 'text', false)}<div class="form-actions"><button class="btn primary" data-action="close">Done</button></div></div>`);
}

async function finishAccountCreation(title, result, targetPage, updateStatus) {
  modal.close();
  page = targetPage;
  render();
  showTemporaryPassword(title, result.temporaryPassword);

  const warnings = [];
  if (updateStatus) {
    try {
      await updateStatus();
    } catch (error) {
      warnings.push(`Account created, but status was not updated: ${error.message}`);
    }
  }
  try {
    await loadData();
    render();
  } catch (error) {
    warnings.push(`Account created, but data could not be refreshed: ${error.message}`);
  }
  if (warnings.length) toast(warnings.join(' '));
}

function formDialog(title, formId, fields, submitLabel = 'Save') {
  showDialog(title, `<form id="${formId}"><div class="form-error" role="alert"></div><div class="form-grid">${fields}</div><div class="form-actions"><button type="button" class="btn" data-action="close">Cancel</button><button type="submit" class="btn primary">${submitLabel}</button></div></form>`);
}

function memberDialog(item = {}) {
  const fields = formField('Full name', 'full_name', item.full_name, 'text', true)
    + formField('Gender', 'gender', item.gender || 'Female', 'text', true, [['Female', 'Female'], ['Male', 'Male'], ['Other', 'Other']])
    + formField('Date of birth', 'date_of_birth', item.date_of_birth, 'date', true)
    + formField('Phone', 'phone', item.phone, 'tel', true)
    + formField('Email', 'email', item.email, 'email')
    + formField('Address', 'address', item.address)
    + formField('Training objective', 'training_objective', item.training_objective)
    + formField('Internal note', 'internal_note', item.internal_note, 'textarea')
    + formField('Status', 'status', item.status || 'Active', 'text', true, [['Active', 'Active'], ['Inactive', 'Inactive']]);
  formDialog(item.id ? 'Edit member' : 'Create member', 'member-form', fields);
  if (item.id) modal.querySelector('form').dataset.id = item.id;
}

function trainerDialog(item = {}) {
  const fields = formField('Full name', 'full_name', item.full_name, 'text', true)
    + formField('Phone', 'phone', item.phone, 'tel', true)
    + formField('Email', 'email', item.email, 'email', true)
    + formField('Specialization', 'specialization', item.specialization)
    + formField('Years of experience', 'years_of_experience', item.years_of_experience ?? 0, 'number')
    + formField('Status', 'status', item.status || 'Active', 'text', true, [['Active', 'Active'], ['Inactive', 'Inactive']]);
  formDialog(item.id ? 'Edit trainer' : 'Create trainer', 'trainer-form', fields);
  if (item.id) modal.querySelector('form').dataset.id = item.id;
}

function planDialog(item = {}) {
  const fields = formField('Plan name', 'name', item.name, 'text', true)
    + formField('Duration in months', 'duration_months', item.duration_months || 1, 'number', true)
    + formField('Price in VND', 'price', item.price || '', 'number', true)
    + formField('Benefits', 'benefits', item.benefits, 'textarea', true)
    + formField('Status', 'status', item.status || 'Available', 'text', true, [['Available', 'Available'], ['Unavailable', 'Unavailable']]);
  formDialog(item.id ? 'Edit plan' : 'Create plan', 'plan-form', fields);
  if (item.id) modal.querySelector('form').dataset.id = item.id;
}

function membershipDialog(memberId = '') {
  const members = data.members.filter(item => item.status === 'Active').map(item => [item.id, `${item.full_name} · ${item.phone}`]);
  const plans = data.plans.filter(item => item.status === 'Available').map(item => [item.id, `${item.name} · ${item.duration_months} month(s)`]);
  const fields = formField('Member', 'member_id', memberId || members[0]?.[0], 'text', true, members)
    + formField('Available plan', 'plan_id', plans[0]?.[0], 'text', true, plans)
    + formField('Start date', 'start_date', today(), 'date', true);
  formDialog('Register or renew membership', 'membership-form', fields, 'Confirm');
}

function sessionDialog(item = {}) {
  const members = data.members.filter(row => row.status === 'Active').map(row => [row.id, `${row.full_name} · ${row.phone}`]);
  const trainers = data.trainers.filter(row => row.status === 'Active').map(row => [row.id, row.full_name]);
  const fields = formField('Member', 'member_id', item.member_id || members[0]?.[0], 'text', true, members)
    + formField('Trainer', 'trainer_id', item.trainer_id || trainers[0]?.[0], 'text', true, trainers)
    + formField('Session date', 'session_date', item.session_date || tomorrow(), 'date', true)
    + formField('Start time', 'start_time', item.start_time || '10:00', 'time', true)
    + formField('End time', 'end_time', item.end_time || '11:00', 'time', true)
    + formField('Note', 'note', item.note, 'textarea');
  formDialog(item.id ? 'Edit training session' : 'Create training session', 'session-form', fields);
  if (item.id) modal.querySelector('form').dataset.id = item.id;
  const note = document.createElement('p');
  note.className = 'hint-rule';
  note.textContent = 'The backend validates membership coverage, working hours and schedule conflicts when saving.';
  modal.querySelector('form .form-grid').after(note);
}

function accountDialog() {
  const fields = formField('Full name', 'full_name', '', 'text', true)
    + formField('Username', 'username', '', 'text', true)
    + formField('Role', 'role', 'Staff', 'text', true, [['Staff', 'Staff'], ['Admin', 'Admin']]);
  formDialog('Create account', 'account-form', fields);
}

function passwordPage() {
  return heading(user.mustChangePassword ? 'Set your password' : 'Change password', 'Verify your current password before saving a new one.') + `<section class="card card-pad account-warning"><form id="password-form"><div class="form-error" role="alert"></div><div class="form-grid">${formField('Current password', 'currentPassword', '', 'password', true)}${formField('New password', 'newPassword', '', 'password', true)}${formField('Confirm new password', 'confirmPassword', '', 'password', true)}</div><div class="form-actions"><button class="btn primary" type="submit">Save password</button></div></form></section>`;
}

function refreshCurrentPage() { render(); }

async function handleClick(event) {
  const nav = event.target.closest('[data-nav]');
  if (nav) { page = nav.dataset.nav; query = ''; filter = ''; render(); return; }
  const actionButton = event.target.closest('[data-action]');
  if (!actionButton) return;
  const action = actionButton.dataset.action;
  const id = Number(actionButton.dataset.id);
  try {
    if (action === 'close') { modal.close(); return; }
    if (action === 'logout') { await API.logout(); user = null; data = emptyState(); page = 'home'; render(); return; }
    if (action === 'new-member') return memberDialog();
    if (action === 'edit-member') return memberDialog(member(id));
    if (action === 'new-trainer') return trainerDialog();
    if (action === 'edit-trainer') return trainerDialog(trainer(id));
    if (action === 'new-plan') return planDialog();
    if (action === 'edit-plan') return planDialog(plan(id));
    if (action === 'new-membership') return membershipDialog();
    if (action === 'register-member') return membershipDialog(id);
    if (action === 'new-session') return sessionDialog();
    if (action === 'edit-session') return sessionDialog(data.sessions.find(item => item.id === id));
    if (action === 'new-account') return accountDialog();
    if (action === 'cancel-session' && confirm('Cancel this scheduled session?')) {
      await API.patch(`/sessions/${id}/cancel`);
      await loadData();
      refreshCurrentPage();
      toast('Session cancelled.');
    }
    if (action === 'toggle-account') {
      const target = data.accounts.find(item => item.id === id);
      await API.patch(`/accounts/${id}/status`, { status: target.status === 'Active' ? 'Locked' : 'Active' });
      await loadData();
      refreshCurrentPage();
      toast('Account status updated.');
    }
    if (action === 'reset-password') {
      const result = await API.post(`/accounts/${id}/reset-password`, {});
      showTemporaryPassword('The account was reset', result.temporaryPassword);
    }
  } catch (error) { showError(error); }
}

app.addEventListener('click', handleClick);
modal.addEventListener('click', handleClick);

app.addEventListener('input', event => {
  if (event.target.id === 'search-records') { query = event.target.value; render(); const input = document.querySelector('#search-records'); input?.focus(); input?.setSelectionRange(query.length, query.length); }
});
app.addEventListener('change', event => { if (event.target.id === 'filter-status') { filter = event.target.value; render(); } });

async function handleSubmit(event) {
  const form = event.target;
  if (!form.matches('form')) return;
  event.preventDefault();
  const values = Object.fromEntries(new FormData(form));
  const existingId = Number(form.dataset.id || 0);
  try {
    if (form.id === 'login-form') {
      const submit = form.querySelector('[type="submit"]');
      submit.disabled = true;
      try { await signIn(values.username.trim(), values.password); }
      finally { submit.disabled = false; }
      return;
    }
    if (form.id === 'member-form') {
      const payload = { fullName: values.full_name, gender: values.gender, dateOfBirth: values.date_of_birth, phone: values.phone, email: values.email || '', address: values.address || '', trainingObjective: values.training_objective || '', internalNote: values.internal_note || '' };
      let result;
      try { result = existingId ? await API.put(`/members/${existingId}`, payload) : await API.post('/members', payload); }
      catch (error) {
        if (!existingId && error.status === 409 && String(error.message).toLowerCase().includes('trùng') && confirm('A similar member exists. Create this profile anyway?')) result = await API.post('/members', { ...payload, confirmPossibleDuplicate: true });
        else throw error;
      }
      if (!existingId) {
        await finishAccountCreation('Member account created', result, 'members', values.status === 'Inactive'
          ? () => API.patch(`/members/${result.member_id}/status`, { status: 'Inactive' }) : null);
        return;
      }
      if (existingId) {
        const previous = member(existingId);
        if (previous.status !== values.status) await API.patch(`/members/${existingId}/status`, { status: values.status });
      }
      await loadData(); modal.close(); page = 'members'; render();
      toast('Member saved.');
      return;
    }
    if (form.id === 'trainer-form') {
      const payload = { fullName: values.full_name, phone: values.phone, email: values.email, specialization: values.specialization || '', yearsOfExperience: Number(values.years_of_experience || 0) };
      const result = existingId ? await API.put(`/trainers/${existingId}`, payload) : await API.post('/trainers', payload);
      if (!existingId) {
        await finishAccountCreation('Trainer account created', result, 'trainers', values.status === 'Inactive'
          ? () => API.patch(`/trainers/${result.trainer_id}/status`, { status: 'Inactive' }) : null);
        return;
      }
      if (trainer(existingId).status !== values.status) await API.patch(`/trainers/${existingId}/status`, { status: values.status });
      await loadData(); modal.close(); render();
      toast('Trainer saved.');
      return;
    }
    if (form.id === 'plan-form') {
      const payload = { name: values.name, durationMonths: Number(values.duration_months), price: Number(values.price), benefits: values.benefits };
      const result = existingId ? await API.put(`/plans/${existingId}`, payload) : await API.post('/plans', payload);
      const savedId = Number(result.plan_id || existingId);
      const previous = existingId ? plan(existingId) : null;
      if ((previous && previous.status !== values.status) || (!previous && values.status === 'Unavailable')) await API.patch(`/plans/${savedId}/status`, { status: values.status });
      await loadData(); modal.close(); render(); toast('Plan saved.'); return;
    }
    if (form.id === 'membership-form') {
      const last = data.memberships.filter(item => item.member_id === Number(values.member_id)).sort((a, b) => b.end_date.localeCompare(a.end_date))[0];
      const shouldRenew = last && (statusOfMembership(last) === 'Expired' || statusOfMembership(last) === 'Active' && Math.ceil((new Date(`${last.end_date}T00:00:00`) - new Date(`${today()}T00:00:00`)) / 86400000) <= 7);
      await API.post(shouldRenew ? '/memberships/renew' : '/memberships/register', { memberId: Number(values.member_id), planId: Number(values.plan_id), startDate: values.start_date });
      await loadData(); modal.close(); page = 'memberships'; render(); toast(shouldRenew ? 'Membership renewed.' : 'Membership registered.'); return;
    }
    if (form.id === 'session-form') {
      const payload = { memberId: Number(values.member_id), trainerId: Number(values.trainer_id), sessionDate: values.session_date, startTime: values.start_time, endTime: values.end_time, note: values.note || '' };
      if (existingId) await API.put(`/sessions/${existingId}`, payload); else await API.post('/sessions', payload);
      await loadData(); modal.close(); render(); toast('Session saved.'); return;
    }
    if (form.id === 'account-form') {
      const result = await API.post('/accounts', { fullName: values.full_name, username: values.username, role: values.role });
      await finishAccountCreation('Account created', result, 'accounts'); return;
    }
    if (form.id === 'password-form') {
      const result = await API.changePassword(values.currentPassword, values.newPassword, values.confirmPassword);
      user = result;
      page = user.role === 'Member' ? 'member' : user.role === 'Trainer' ? 'trainer' : 'home';
      render();
      try {
        await loadData();
        render();
        toast('Password updated.');
      } catch (error) {
        toast(`Password updated, but data could not be refreshed: ${error.message}`);
      }
    }
  } catch (error) { showError(error, form); }
}

app.addEventListener('submit', handleSubmit);
modal.addEventListener('submit', handleSubmit);

function showError(error, form = null) {
  if (error.status === 401 && user) {
    user = null;
    data = emptyState();
    render();
  }
  const message = error.message || 'Unexpected error';
  const target = form?.querySelector('.form-error');
  if (target) target.innerHTML = `<div class="error">${esc(message)}</div>`;
  else toast(message);
}

function renderLoginError(message) {
  const target = document.querySelector('#login-form .form-error');
  if (target) target.innerHTML = `<div class="error">${esc(message)}</div>`;
}

render();
restoreSession();
