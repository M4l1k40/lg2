import test from 'node:test';
import assert from 'node:assert/strict';
import {
  appointmentActions,
  buildCreatePayload,
  buildUpdatePayload,
  describeAppointmentError,
  emptyForm,
  formatDateTime,
  groupAppointments,
  nowAsInputValue,
  toApiDateTime,
  toInputValue,
  validateForm
} from './appointments.js';

// Heures locales sans fuseau : les tests ne dépendent pas de la machine.
const NOW = new Date('2026-10-18T10:00:00');

test('regroupement : à venir, à clôturer, terminés, annulés, triés', () => {
  const list = [
    { id: 'late', status: 'SCHEDULED', dateTime: '2026-10-30T09:00:00' },
    { id: 'soon', status: 'SCHEDULED', dateTime: '2026-10-19T09:00:00' },
    { id: 'forgot', status: 'SCHEDULED', dateTime: '2026-10-10T09:00:00' },
    { id: 'old-done', status: 'COMPLETED', dateTime: '2026-09-01T09:00:00' },
    { id: 'new-done', status: 'COMPLETED', dateTime: '2026-10-15T09:00:00' },
    { id: 'cancelled', status: 'CANCELLED', dateTime: '2026-10-25T09:00:00' }
  ];
  const groups = groupAppointments(list, NOW);
  assert.deepEqual(groups.upcoming.map((item) => item.id), ['soon', 'late']);
  assert.deepEqual(groups.toClose.map((item) => item.id), ['forgot']);
  assert.deepEqual(groups.completed.map((item) => item.id), ['new-done', 'old-done']);
  assert.deepEqual(groups.cancelled.map((item) => item.id), ['cancelled']);
});

test('regroupement : liste absente ou vide', () => {
  assert.deepEqual(groupAppointments(undefined, NOW), { upcoming: [], toClose: [], completed: [], cancelled: [] });
});

test('actions : modifier et annuler seulement si planifié, terminer seulement une fois l\'heure passée', () => {
  const future = { status: 'SCHEDULED', dateTime: '2026-10-20T09:00:00' };
  const past = { status: 'SCHEDULED', dateTime: '2026-10-10T09:00:00' };
  const done = { status: 'COMPLETED', dateTime: '2026-10-10T09:00:00' };
  assert.deepEqual(appointmentActions(future, { isOffice: false, now: NOW }),
    { canEdit: true, canCancel: true, canComplete: false, canDelete: false });
  assert.deepEqual(appointmentActions(past, { isOffice: true, now: NOW }),
    { canEdit: true, canCancel: true, canComplete: true, canDelete: true });
  assert.deepEqual(appointmentActions(done, { isOffice: false, now: NOW }),
    { canEdit: false, canCancel: false, canComplete: false, canDelete: false });
});

test('validation : champs obligatoires et date future', () => {
  const empty = validateForm(emptyForm(), { editing: false, isOffice: true, now: NOW });
  assert.deepEqual(Object.keys(empty).sort(), ['clientId', 'dateTime', 'lawyerId']);

  const past = { ...emptyForm(), clientId: 'c1', dateTime: '2026-10-18T09:00' };
  assert.equal(validateForm(past, { editing: false, isOffice: false, now: NOW }).dateTime, 'La date doit être dans le futur.');

  const valid = { ...emptyForm(), clientId: 'c1', dateTime: '2026-10-20T14:30' };
  assert.deepEqual(validateForm(valid, { editing: false, isOffice: false, now: NOW }), {});
});

test('validation : en modification le client et l\'avocat ne sont pas redemandés', () => {
  const form = { ...emptyForm(), dateTime: '2026-10-20T14:30' };
  assert.deepEqual(validateForm(form, { editing: true, isOffice: true, now: NOW }), {});
});

test('validation : longueurs maximales du lieu et des notes', () => {
  const form = { ...emptyForm(), clientId: 'c1', dateTime: '2026-10-20T14:30', location: 'x'.repeat(501), notes: 'y'.repeat(2001) };
  const errors = validateForm(form, { editing: false, isOffice: false, now: NOW });
  assert.ok(errors.location);
  assert.ok(errors.notes);
});

test('corps de création : date au format serveur, vides transformés en null, avocat fourni', () => {
  const form = { ...emptyForm(), clientId: 'c1', caseId: '', dateTime: '2026-10-20T14:30', location: '  Cabinet  ', notes: '   ' };
  assert.deepEqual(buildCreatePayload(form, 'lawyer-1'), {
    clientId: 'c1', caseId: null, lawyerId: 'lawyer-1', dateTime: '2026-10-20T14:30:00',
    type: 'CLIENT_MEETING', location: 'Cabinet', notes: null
  });
});

test('corps de modification : seulement les champs modifiables', () => {
  const form = { ...emptyForm(), clientId: 'c1', dateTime: '2026-10-21T08:00', location: 'Palais', notes: 'Pièces' };
  assert.deepEqual(buildUpdatePayload(form), {
    dateTime: '2026-10-21T08:00:00', type: 'CLIENT_MEETING', location: 'Palais', notes: 'Pièces'
  });
});

test('formats de date', () => {
  assert.equal(toInputValue('2026-10-20T14:30:00'), '2026-10-20T14:30');
  assert.equal(toApiDateTime('2026-10-20T14:30'), '2026-10-20T14:30:00');
  assert.equal(nowAsInputValue(NOW), '2026-10-18T10:00');
  assert.equal(formatDateTime('2026-10-20T14:30:00'), 'mardi 20 octobre 2026 à 14h30');
  assert.equal(formatDateTime(null), '—');
});

test('messages d\'erreur compréhensibles selon le statut HTTP', () => {
  assert.match(describeAppointmentError({ status: 403 }), /pas le droit/);
  assert.match(describeAppointmentError({ status: 404 }), /introuvable/);
  assert.match(describeAppointmentError({ status: 409 }), /ne peut plus être modifié/);
  assert.match(describeAppointmentError({ status: 503 }), /indisponible/);
  assert.equal(describeAppointmentError({ status: 418, message: 'Autre' }), 'Autre');
});
