import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildPayload,
  describeDelay,
  describeInvoiceError,
  emptyForm,
  formatDay,
  formatMoney,
  groupInvoices,
  invoiceActions,
  invoiceTotals,
  isLate,
  parseAmount,
  suggestInvoiceNumber,
  validateForm
} from './invoices.js';

// Heures locales sans fuseau : les tests ne dépendent pas de la machine.
const NOW = new Date('2026-10-18T10:00:00');

test('retard : une facture envoyée dont l\'échéance est passée est en retard sans attendre le serveur', () => {
  assert.equal(isLate({ status: 'SENT', dueDate: '2026-10-17' }, NOW), true);
  assert.equal(isLate({ status: 'SENT', dueDate: '2026-10-18' }, NOW), false);
  assert.equal(isLate({ status: 'OVERDUE', dueDate: '2026-12-31' }, NOW), true);
  assert.equal(isLate({ status: 'PAID', dueDate: '2026-01-01' }, NOW), false);
  assert.equal(isLate({ status: 'DRAFT', dueDate: '2026-01-01' }, NOW), false);
});

test('regroupement : à régler, en retard, payées, annulées, brouillons', () => {
  const list = [
    { id: 'far', status: 'SENT', dueDate: '2026-12-01' },
    { id: 'near', status: 'SENT', dueDate: '2026-10-20' },
    { id: 'missed', status: 'SENT', dueDate: '2026-10-01' },
    { id: 'marked', status: 'OVERDUE', dueDate: '2026-09-15' },
    { id: 'p1', status: 'PAID', dueDate: '2026-08-01' },
    { id: 'p2', status: 'PAID', dueDate: '2026-09-01' },
    { id: 'x', status: 'CANCELLED', dueDate: '2026-09-10' },
    { id: 'd', status: 'DRAFT', dueDate: '2026-11-01' }
  ];
  const groups = groupInvoices(list, NOW);
  assert.deepEqual(groups.open.map((item) => item.id), ['near', 'far']);
  assert.deepEqual(groups.late.map((item) => item.id), ['marked', 'missed']);
  assert.deepEqual(groups.paid.map((item) => item.id), ['p2', 'p1']);
  assert.deepEqual(groups.cancelled.map((item) => item.id), ['x']);
  assert.deepEqual(groups.draft.map((item) => item.id), ['d']);
  assert.deepEqual(groupInvoices(undefined, NOW), { open: [], late: [], paid: [], cancelled: [], draft: [] });
});

test('délai : aujourd\'hui, demain, dans N jours, en retard de N jours', () => {
  assert.equal(describeDelay({ status: 'SENT', dueDate: '2026-10-18' }, NOW), "Échéance aujourd'hui");
  assert.equal(describeDelay({ status: 'SENT', dueDate: '2026-10-19' }, NOW), 'Échéance demain');
  assert.equal(describeDelay({ status: 'SENT', dueDate: '2026-10-25' }, NOW), 'Dans 7 jours');
  assert.equal(describeDelay({ status: 'OVERDUE', dueDate: '2026-10-17' }, NOW), 'En retard de 1 jour');
  assert.equal(describeDelay({ status: 'OVERDUE', dueDate: '2026-10-13' }, NOW), 'En retard de 5 jours');
  assert.equal(describeDelay({ status: 'PAID', dueDate: '2026-10-13' }, NOW), '');
});

test('totaux : à encaisser, en retard, encaissé, sans erreur d\'arrondi', () => {
  const list = [
    { status: 'SENT', amount: 0.1, dueDate: '2026-12-01' },
    { status: 'SENT', amount: 0.2, dueDate: '2026-10-01' },
    { status: 'OVERDUE', amount: 1000, dueDate: '2026-09-01' },
    { status: 'PAID', amount: 500.5, dueDate: '2026-08-01' },
    { status: 'CANCELLED', amount: 9999, dueDate: '2026-08-01' },
    { status: 'DRAFT', amount: 7777, dueDate: '2026-12-01' }
  ];
  assert.deepEqual(invoiceTotals(list, NOW), {
    toCollect: { count: 3, amount: 1000.3 },
    late: { count: 2, amount: 1000.2 },
    collected: { count: 1, amount: 500.5 }
  });
});

test('actions : brouillon, envoyée, en retard, payée, annulée ; suppression réservée à l\'administrateur', () => {
  assert.deepEqual(invoiceActions({ status: 'DRAFT' }, { isAdmin: false }),
    { canEdit: true, canSend: true, canPay: false, canCancel: true, canDelete: false });
  assert.deepEqual(invoiceActions({ status: 'SENT' }, { isAdmin: true }),
    { canEdit: true, canSend: false, canPay: true, canCancel: true, canDelete: true });
  assert.deepEqual(invoiceActions({ status: 'OVERDUE' }, { isAdmin: false }),
    { canEdit: true, canSend: false, canPay: true, canCancel: true, canDelete: false });
  assert.deepEqual(invoiceActions({ status: 'PAID' }, { isAdmin: false }),
    { canEdit: false, canSend: false, canPay: false, canCancel: false, canDelete: false });
  assert.deepEqual(invoiceActions({ status: 'CANCELLED' }, { isAdmin: true }),
    { canEdit: false, canSend: false, canPay: false, canCancel: false, canDelete: true });
});

test('numéro suggéré : suite du plus grand numéro de l\'année, ignore les autres formats', () => {
  const list = [{ invoiceNumber: 'F-2026-0007' }, { invoiceNumber: 'F-2026-0012' }, { invoiceNumber: 'F-2025-0099' }, { invoiceNumber: 'LIBRE-1' }];
  assert.equal(suggestInvoiceNumber(list, NOW), 'F-2026-0013');
  assert.equal(suggestInvoiceNumber([], NOW), 'F-2026-0001');
});

test('montants : virgule ou point, espaces, valeurs invalides', () => {
  assert.equal(parseAmount('1250,5'), 1250.5);
  assert.equal(parseAmount(' 1 250.75 '), 1250.75);
  assert.ok(Number.isNaN(parseAmount('')));
  assert.ok(Number.isNaN(parseAmount('abc')));
});

test('validation : champs obligatoires, montant, décimales', () => {
  const empty = validateForm({ ...emptyForm(), invoiceNumber: '' }, { editing: false });
  assert.deepEqual(Object.keys(empty).sort(), ['amount', 'caseId', 'description', 'dueDate', 'invoiceNumber']);

  const valid = { caseId: 'c1', invoiceNumber: 'F-2026-0001', description: 'Honoraires', amount: '1250,50', dueDate: '2026-11-15' };
  assert.deepEqual(validateForm(valid, { editing: false }), {});
  assert.deepEqual(validateForm({ ...valid, caseId: '' }, { editing: true }), {});

  assert.ok(validateForm({ ...valid, amount: '0' }, { editing: false }).amount);
  assert.ok(validateForm({ ...valid, amount: '-5' }, { editing: false }).amount);
  assert.ok(validateForm({ ...valid, amount: '10,555' }, { editing: false }).amount);
  assert.ok(validateForm({ ...valid, description: 'x'.repeat(256) }, { editing: false }).description);
  assert.ok(validateForm({ ...valid, invoiceNumber: 'x'.repeat(51) }, { editing: false }).invoiceNumber);
});

test('corps de requête : cabinet et client fournis par l\'appelant, montant arrondi, textes nettoyés', () => {
  const form = { caseId: 'c1', invoiceNumber: '  F-2026-0001 ', description: ' Honoraires ', amount: '1250,5', dueDate: '2026-11-15' };
  assert.deepEqual(buildPayload(form, { lawFirmId: 'firm', caseId: 'c1', clientId: 'cl1' }), {
    lawFirmId: 'firm', caseId: 'c1', clientId: 'cl1', invoiceNumber: 'F-2026-0001',
    description: 'Honoraires', amount: 1250.5, dueDate: '2026-11-15'
  });
});

test('formats : montant et date', () => {
  assert.equal(formatMoney(1250.5).replace(/\s/g, ' '), '1 250,50 €');
  assert.equal(formatMoney(null).replace(/\s/g, ' '), '0,00 €');
  assert.equal(formatDay('2026-11-02'), '2 novembre 2026');
  assert.equal(formatDay(null), '—');
});

test('messages d\'erreur compréhensibles', () => {
  assert.match(describeInvoiceError({ status: 409, message: 'Invoice number already exists in this law firm.' }), /numéro de facture existe déjà/);
  assert.match(describeInvoiceError({ status: 409, message: 'A paid or cancelled invoice can no longer be modified' }), /payée ou annulée/);
  assert.match(describeInvoiceError({ status: 409, message: 'Invalid transition' }), /changement de statut/);
  assert.match(describeInvoiceError({ status: 403 }), /pas le droit/);
  assert.match(describeInvoiceError({ status: 500 }, { saving: true }), /déjà utilisé/);
  assert.match(describeInvoiceError({ status: 503 }), /indisponible/);
});
