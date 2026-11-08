import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildPayload,
  deadlineActions,
  describeDeadlineError,
  describeDelay,
  emptyForm,
  filterByPriority,
  formatDueDate,
  groupDeadlines,
  isLate,
  validateForm
} from './deadlines.js';

// Heures locales sans fuseau : les tests ne dépendent pas de la machine.
const NOW = new Date('2026-10-18T10:00:00');

test('une échéance à faire dont la date est passée est en retard, même si le serveur ne l\'a pas encore marquée', () => {
  assert.equal(isLate({ status: 'PENDING', dueDate: '2026-10-17T09:00:00' }, NOW), true);
  assert.equal(isLate({ status: 'OVERDUE', dueDate: '2026-12-01T09:00:00' }, NOW), true);
  assert.equal(isLate({ status: 'PENDING', dueDate: '2026-10-19T09:00:00' }, NOW), false);
  assert.equal(isLate({ status: 'COMPLETED', dueDate: '2026-10-01T09:00:00' }, NOW), false);
});

test('regroupement : à venir, en retard, terminées, annulées, triées', () => {
  const list = [
    { id: 'far', status: 'PENDING', dueDate: '2026-11-30T09:00:00' },
    { id: 'near', status: 'PENDING', dueDate: '2026-10-20T09:00:00' },
    { id: 'missed', status: 'PENDING', dueDate: '2026-10-10T09:00:00' },
    { id: 'marked', status: 'OVERDUE', dueDate: '2026-10-05T09:00:00' },
    { id: 'done-old', status: 'COMPLETED', dueDate: '2026-09-01T09:00:00' },
    { id: 'done-new', status: 'COMPLETED', dueDate: '2026-10-15T09:00:00' },
    { id: 'off', status: 'CANCELLED', dueDate: '2026-10-25T09:00:00' }
  ];
  const groups = groupDeadlines(list, NOW);
  assert.deepEqual(groups.upcoming.map((item) => item.id), ['near', 'far']);
  assert.deepEqual(groups.late.map((item) => item.id), ['marked', 'missed']);
  assert.deepEqual(groups.completed.map((item) => item.id), ['done-new', 'done-old']);
  assert.deepEqual(groups.cancelled.map((item) => item.id), ['off']);
});

test('regroupement : liste absente', () => {
  assert.deepEqual(groupDeadlines(undefined, NOW), { upcoming: [], late: [], completed: [], cancelled: [] });
});

test('délai : aujourd\'hui, demain, dans N jours, en retard de N jours', () => {
  assert.equal(describeDelay({ status: 'PENDING', dueDate: '2026-10-18T18:00:00' }, NOW), "Aujourd'hui");
  assert.equal(describeDelay({ status: 'PENDING', dueDate: '2026-10-19T08:00:00' }, NOW), 'Demain');
  assert.equal(describeDelay({ status: 'PENDING', dueDate: '2026-10-25T08:00:00' }, NOW), 'Dans 7 jours');
  assert.equal(describeDelay({ status: 'PENDING', dueDate: '2026-10-18T08:00:00' }, NOW), "En retard (aujourd'hui)");
  assert.equal(describeDelay({ status: 'OVERDUE', dueDate: '2026-10-17T08:00:00' }, NOW), 'En retard de 1 jour');
  assert.equal(describeDelay({ status: 'OVERDUE', dueDate: '2026-10-13T08:00:00' }, NOW), 'En retard de 5 jours');
  assert.equal(describeDelay({ status: 'COMPLETED', dueDate: '2026-10-13T08:00:00' }, NOW), '');
});

test('actions : modifier seulement si à faire et pas en retard (le serveur exige une date future)', () => {
  assert.deepEqual(deadlineActions({ status: 'PENDING', dueDate: '2026-10-20T09:00:00' }, NOW),
    { canEdit: true, canComplete: true, canCancel: true, canDelete: true });
  assert.deepEqual(deadlineActions({ status: 'OVERDUE', dueDate: '2026-10-01T09:00:00' }, NOW),
    { canEdit: false, canComplete: true, canCancel: true, canDelete: true });
  assert.deepEqual(deadlineActions({ status: 'PENDING', dueDate: '2026-10-01T09:00:00' }, NOW),
    { canEdit: false, canComplete: true, canCancel: true, canDelete: true });
  assert.deepEqual(deadlineActions({ status: 'COMPLETED', dueDate: '2026-10-01T09:00:00' }, NOW),
    { canEdit: false, canComplete: false, canCancel: false, canDelete: true });
});

test('filtre par priorité', () => {
  const list = [{ priority: 'LOW' }, { priority: 'URGENT' }, { priority: 'URGENT' }];
  assert.equal(filterByPriority(list, 'URGENT').length, 2);
  assert.equal(filterByPriority(list, '').length, 3);
});

test('validation : champs obligatoires, date future, longueurs', () => {
  const empty = validateForm({ ...emptyForm(), priority: '' }, NOW);
  assert.deepEqual(Object.keys(empty).sort(), ['caseId', 'dueDate', 'priority', 'title']);

  const past = { ...emptyForm(), caseId: 'c1', title: 'Conclusions', dueDate: '2026-10-18T09:00' };
  assert.equal(validateForm(past, NOW).dueDate, 'La date limite doit être dans le futur.');

  const valid = { ...emptyForm(), caseId: 'c1', title: 'Conclusions', dueDate: '2026-10-25T09:00' };
  assert.deepEqual(validateForm(valid, NOW), {});

  const long = { ...valid, title: 'x'.repeat(256), description: 'y'.repeat(2001) };
  const errors = validateForm(long, NOW);
  assert.ok(errors.title);
  assert.ok(errors.description);

  assert.ok(validateForm({ ...valid, title: '   ' }, NOW).title);
});

test('corps de requête : date au format serveur, texte nettoyé, description vide transformée en null', () => {
  const form = { caseId: 'c1', title: '  Conclusions  ', description: '   ', dueDate: '2026-10-25T09:00', priority: 'HIGH' };
  assert.deepEqual(buildPayload(form), {
    caseId: 'c1', title: 'Conclusions', description: null, dueDate: '2026-10-25T09:00:00', priority: 'HIGH'
  });
});

test('format de la date limite et messages d\'erreur', () => {
  assert.equal(formatDueDate('2026-10-20T14:30:00'), 'mar. 20 octobre 2026 à 14h30');
  assert.equal(formatDueDate(null), '—');
  assert.match(describeDeadlineError({ status: 403 }), /pas le droit/);
  assert.match(describeDeadlineError({ status: 404 }), /introuvable/);
  assert.match(describeDeadlineError({ status: 409 }), /ne peut pas passer/);
  assert.match(describeDeadlineError({ status: 503 }), /indisponible/);
});
