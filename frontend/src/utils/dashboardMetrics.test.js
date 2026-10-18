import test from 'node:test';
import assert from 'node:assert/strict';
import {
  casesMetric,
  documentsMetric,
  hearingMetric,
  latestDocumentText,
  nextAppointmentText,
  notificationsMetric,
  paymentMetric,
  pendingInvoiceText,
  relativeDay
} from './dashboardMetrics.js';

// Heures locales sans fuseau : les tests ne dépendent pas de la machine.
const NOW = new Date('2026-10-18T10:00:00');
const money = (value) => `${value} EUR`;

test('dossiers actifs : exclut clôturés et archivés, compte les ouvertures du mois', () => {
  const cases = [
    { status: 'OPEN', createdAt: '2026-10-02T09:00:00' },
    { status: 'IN_PROGRESS', createdAt: '2026-09-20T09:00:00' },
    { status: 'CLOSED', createdAt: '2026-10-05T09:00:00' },
    { status: 'ARCHIVED', createdAt: '2026-01-05T09:00:00' }
  ];
  assert.deepEqual(casesMetric(cases, NOW), { value: '2', trend: '2 ouvert(s) ce mois' });
});

test('documents : total et récents sur 7 jours', () => {
  const documents = [
    { createdAt: '2026-10-17T09:00:00' },
    { createdAt: '2026-10-12T09:00:00' },
    { createdAt: '2026-10-01T09:00:00' }
  ];
  assert.deepEqual(documentsMetric(documents, NOW), { value: '3', trend: '2 récent(s) (7 j)' });
});

test('prochaine audience : la plus proche, planifiée et à venir seulement', () => {
  const hearings = [
    { status: 'SCHEDULED', dateTime: '2026-10-30T09:00:00' },
    { status: 'SCHEDULED', dateTime: '2026-10-22T14:30:00' },
    { status: 'POSTPONED', dateTime: '2026-10-19T09:00:00' },
    { status: 'CANCELLED', dateTime: '2026-10-19T10:00:00' },
    { status: 'SCHEDULED', dateTime: '2026-10-01T09:00:00' }
  ];
  assert.deepEqual(hearingMetric(hearings, NOW), { value: '22 octobre', trend: 'Dans 4 jours' });
});

test('prochaine audience : message explicite quand il n\'y en a aucune', () => {
  assert.deepEqual(hearingMetric([], NOW), { value: 'Aucune', trend: 'Rien de planifié' });
});

test('libellés relatifs : aujourd\'hui, demain, dans N jours', () => {
  assert.equal(relativeDay('2026-10-18T18:00:00', NOW), "Aujourd'hui");
  assert.equal(relativeDay('2026-10-19T08:00:00', NOW), 'Demain');
  assert.equal(relativeDay('2026-10-25T08:00:00', NOW), 'Dans 7 jours');
});

test('paiement : somme des factures envoyées et en retard uniquement', () => {
  const invoices = [
    { status: 'SENT', amount: 1000 },
    { status: 'OVERDUE', amount: 240.5 },
    { status: 'DRAFT', amount: 5000 },
    { status: 'PAID', amount: 300 },
    { status: 'CANCELLED', amount: 700 }
  ];
  assert.deepEqual(paymentMetric(invoices, money), { value: '1240.5 EUR', trend: '2 factures dont 1 en retard' });
});

test('paiement : à jour quand rien n\'est dû', () => {
  assert.deepEqual(paymentMetric([{ status: 'PAID', amount: 10 }], money), { value: '0 EUR', trend: 'À jour' });
});

test('notifications : total et non lues', () => {
  const notifications = [{ read: false }, { read: true }, { read: false }];
  assert.deepEqual(notificationsMetric(notifications), { value: '3', trend: '2 non lue(s)' });
});

test('à retenir : prochain rendez-vous planifié et à venir', () => {
  const appointments = [
    { status: 'CANCELLED', dateTime: '2026-10-19T09:00:00' },
    { status: 'SCHEDULED', dateTime: '2026-10-21T14:30:00' },
    { status: 'SCHEDULED', dateTime: '2026-10-10T14:30:00' }
  ];
  assert.equal(nextAppointmentText(appointments, NOW), 'Prochain rendez-vous : 21 octobre à 14h30');
  assert.equal(nextAppointmentText([], NOW), 'Aucun rendez-vous à venir');
});

test('à retenir : dernier document et prochaine facture à régler', () => {
  const documents = [
    { fileName: 'ancien.pdf', createdAt: '2026-09-01T09:00:00' },
    { fileName: 'decision.pdf', createdAt: '2026-10-15T09:00:00' }
  ];
  assert.equal(latestDocumentText(documents), 'Dernier document : decision.pdf');
  assert.equal(latestDocumentText([]), 'Aucun document partagé');

  const invoices = [
    { status: 'SENT', invoiceNumber: 'F-2', dueDate: '2026-11-20' },
    { status: 'OVERDUE', invoiceNumber: 'F-1', dueDate: '2026-10-02' },
    { status: 'PAID', invoiceNumber: 'F-0', dueDate: '2026-09-01' }
  ];
  assert.equal(pendingInvoiceText(invoices), 'Paiement en attente : facture F-1 (échéance 2 octobre)');
  assert.equal(pendingInvoiceText([]), 'Aucun paiement en attente');
});

test('données absentes : aucune erreur, valeurs explicites', () => {
  assert.deepEqual(casesMetric(undefined, NOW), { value: '0', trend: '0 ouvert(s) ce mois' });
  assert.deepEqual(notificationsMetric(null), { value: '0', trend: '0 non lue(s)' });
});
