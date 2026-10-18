import test from 'node:test';
import assert from 'node:assert/strict';
import {
  activeUsersMetric,
  agendaItems,
  appointmentsTodayMetric,
  billingMetric,
  consultationsToAssignText,
  disabledAccountsText,
  draftInvoicesText,
  modifiedCasesMetric,
  newClientsMetric,
  newClientsText,
  overdueInvoicesText,
  pendingPaymentsMetric,
  readMetric,
  recentActivity,
  recentDocumentsMetric,
  roleUsersMetric
} from './officeDashboardMetrics.js';

const NOW = new Date('2026-10-18T10:00:00');
const money = (value) => `${value} EUR`;

test('rendez-vous du jour : ignore les annulés et les autres jours, compte les réunions internes', () => {
  const appointments = [
    { id: 'a', status: 'SCHEDULED', type: 'INTERNAL_MEETING', dateTime: '2026-10-18T09:30:00' },
    { id: 'b', status: 'SCHEDULED', type: 'CLIENT_MEETING', dateTime: '2026-10-18T14:00:00' },
    { id: 'c', status: 'CANCELLED', type: 'CLIENT_MEETING', dateTime: '2026-10-18T15:00:00' },
    { id: 'd', status: 'SCHEDULED', type: 'CLIENT_MEETING', dateTime: '2026-10-19T09:00:00' }
  ];
  assert.deepEqual(appointmentsTodayMetric(appointments, NOW), { value: '2', trend: '1 interne(s)' });
});

test('agenda : triée, nom du client, et statut selon l\'heure', () => {
  const appointments = [
    { id: 'late', status: 'SCHEDULED', type: 'CLIENT_MEETING', clientId: 'c1', dateTime: '2026-10-18T14:00:00', location: 'Bureau 2' },
    { id: 'past', status: 'SCHEDULED', type: 'INTERNAL_MEETING', dateTime: '2026-10-18T09:30:00' },
    { id: 'done', status: 'COMPLETED', type: 'CONSULTATION', dateTime: '2026-10-18T08:00:00' }
  ];
  const clients = [{ id: 'c1', firstName: 'Anne', lastName: 'Martin' }];
  const agenda = agendaItems(appointments, clients, NOW);
  assert.deepEqual(agenda.map((item) => item.key), ['done', 'past', 'late']);
  assert.deepEqual(agenda[0], { key: 'done', name: 'Consultation', type: '08h00', status: 'Terminé', tone: 'emerald' });
  assert.equal(agenda[1].status, 'À clôturer');
  assert.deepEqual(agenda[2], { key: 'late', name: 'Rendez-vous client — Anne Martin', type: '14h00 · Bureau 2', status: 'À venir', tone: 'amber' });
});

test('nouveaux clients : 7 jours glissants et aujourd\'hui', () => {
  const clients = [
    { createdAt: '2026-10-18T08:00:00' },
    { createdAt: '2026-10-14T08:00:00' },
    { createdAt: '2026-10-01T08:00:00' }
  ];
  assert.deepEqual(newClientsMetric(clients, NOW), { value: '2', trend: "1 aujourd'hui" });
  assert.equal(newClientsText(clients, NOW), '2 nouveaux clients cette semaine');
  assert.equal(newClientsText([], NOW), '0 nouveau client cette semaine');
});

test('dossiers modifiés : seulement ceux modifiés aujourd\'hui', () => {
  const cases = [{ updatedAt: '2026-10-18T07:00:00' }, { updatedAt: '2026-10-17T23:59:00' }, { updatedAt: null }];
  assert.deepEqual(modifiedCasesMetric(cases, NOW), { value: '1', trend: "Aujourd'hui" });
});

test('paiements en attente et factures en brouillon', () => {
  const invoices = [{ status: 'SENT' }, { status: 'OVERDUE' }, { status: 'OVERDUE' }, { status: 'DRAFT' }, { status: 'PAID' }];
  assert.deepEqual(pendingPaymentsMetric(invoices), { value: '3', trend: '2 en retard' });
  assert.equal(draftInvoicesText(invoices), '1 facture en attente d\'envoi');
  assert.equal(overdueInvoicesText(invoices), '2 factures en retard');
  assert.equal(overdueInvoicesText([]), 'Aucune facture en retard');
});

test('documents récents : valeurs du serveur', () => {
  assert.deepEqual(recentDocumentsMetric({ total: 40, lastSevenDays: 6, today: 2 }), { value: '6', trend: "2 aujourd'hui" });
  assert.deepEqual(recentDocumentsMetric(undefined), { value: '0', trend: "0 aujourd'hui" });
});

test('utilisateurs : actifs, par rôle, désactivés', () => {
  const users = [
    { role: 'LAWYER', enabled: true },
    { role: 'LAWYER', enabled: false },
    { role: 'SECRETARY', enabled: true },
    { role: 'CLIENT', enabled: true },
    { role: 'CLIENT', enabled: false }
  ];
  assert.deepEqual(activeUsersMetric(users), { value: '3', trend: '2 désactivé(s)' });
  assert.deepEqual(roleUsersMetric(users, 'LAWYER'), { value: '1', trend: '1 désactivé(s)' });
  assert.deepEqual(roleUsersMetric(users, 'SECRETARY'), { value: '1', trend: '0 désactivé(s)' });
  assert.equal(disabledAccountsText(users), '2 comptes désactivés à vérifier');
  assert.equal(disabledAccountsText([{ enabled: true }]), 'Aucun compte désactivé');
});

test('facturation : seulement les factures émises du mois en cours', () => {
  const invoices = [
    { status: 'SENT', amount: 1000, createdAt: '2026-10-03T09:00:00' },
    { status: 'PAID', amount: 500.5, createdAt: '2026-10-10T09:00:00' },
    { status: 'DRAFT', amount: 9999, createdAt: '2026-10-10T09:00:00' },
    { status: 'CANCELLED', amount: 700, createdAt: '2026-10-10T09:00:00' },
    { status: 'SENT', amount: 300, createdAt: '2026-09-28T09:00:00' }
  ];
  assert.deepEqual(billingMetric(invoices, NOW, money), { value: '1500.5 EUR', trend: '2 factures ce mois' });
  assert.deepEqual(billingMetric([], NOW, money), { value: '0 EUR', trend: '0 facture ce mois' });
});

test('activité récente : mélange triée par date, limitée, et tolère une source absente', () => {
  const activity = recentActivity({
    cases: [{ id: '1', title: 'Divorce', createdAt: '2026-10-10T09:00:00' }],
    consultations: [{ id: '2', subject: 'Bail', createdAt: '2026-10-17T16:45:00' }],
    invoices: undefined
  });
  assert.deepEqual(activity.map((item) => item.key), ['consultation-2', 'case-1']);
  assert.equal(activity[0].name, 'Consultation : Bail');
  assert.equal(activity[0].type, 'Créé le 17 octobre à 16h45');
  assert.equal(recentActivity({ cases: Array.from({ length: 9 }, (_, i) => ({ id: String(i), title: 't', createdAt: '2026-10-01T09:00:00' })) }).length, 5);
});

test('consultations à assigner', () => {
  assert.equal(consultationsToAssignText([{ status: 'PENDING' }, { status: 'ASSIGNED' }, { status: 'PENDING' }]), '2 consultations à assigner');
  assert.equal(consultationsToAssignText([]), 'Aucune consultation à assigner');
});

test('readMetric : chargement, indisponible, disponible', () => {
  assert.deepEqual(readMetric(true, null, 'x', () => ({})), { value: '—', trend: 'Chargement…' });
  assert.deepEqual(readMetric(false, { x: { ok: false } }, 'x', () => ({})), { value: '—', trend: 'Indisponible' });
  assert.deepEqual(readMetric(false, { x: { ok: true, value: 5 } }, 'x', (value) => ({ value: String(value), trend: '' })), { value: '5', trend: '' });
});
