import { formatShortDate, formatTime, pendingInvoices, plural, startOfDay, validDate } from './dashboardMetrics.js';

// Chiffres des tableaux de bord de la secrétaire et de l'administrateur. Fonctions pures : « now » est fourni.

const DAY_MS = 24 * 60 * 60 * 1000;
const WEEK_DAYS = 7;
const BILLED_STATUSES = ['SENT', 'PAID', 'OVERDUE'];

const asList = (value) => (Array.isArray(value) ? value : []);

function isSameDay(a, b) {
  return startOfDay(a).getTime() === startOfDay(b).getTime();
}

function dayOf(value) {
  return validDate(value);
}

/** Valeur d'une carte : « Chargement… » pendant le chargement, « Indisponible » si la source a échoué. */
export function readMetric(loading, sources, name, compute) {
  if (loading) return { value: '—', trend: 'Chargement…' };
  const source = sources?.[name];
  return source?.ok ? compute(source.value) : { value: '—', trend: 'Indisponible' };
}

// ---------- Secrétaire ----------

export function todaysAppointments(appointments, now) {
  return asList(appointments)
    .filter((item) => item.status !== 'CANCELLED')
    .map((item) => ({ item, date: dayOf(item.dateTime) }))
    .filter(({ date }) => date && isSameDay(date, now))
    .sort((a, b) => a.date - b.date)
    .map(({ item }) => item);
}

export function appointmentsTodayMetric(appointments, now) {
  const today = todaysAppointments(appointments, now);
  const internal = today.filter((item) => item.type === 'INTERNAL_MEETING').length;
  return { value: String(today.length), trend: `${internal} interne(s)` };
}

function newClientsSince(clients, now) {
  const since = new Date(now.getTime() - WEEK_DAYS * DAY_MS);
  return asList(clients).filter((item) => {
    const created = dayOf(item.createdAt);
    return created && created >= since;
  });
}

export function newClientsMetric(clients, now) {
  const today = asList(clients).filter((item) => {
    const created = dayOf(item.createdAt);
    return created && isSameDay(created, now);
  });
  return { value: String(newClientsSince(clients, now).length), trend: `${today.length} aujourd'hui` };
}

function casesModifiedToday(cases, now) {
  return asList(cases).filter((item) => {
    const updated = dayOf(item.updatedAt);
    return updated && isSameDay(updated, now);
  });
}

export function modifiedCasesMetric(cases, now) {
  return { value: String(casesModifiedToday(cases, now).length), trend: "Aujourd'hui" };
}

export function pendingPaymentsMetric(invoices) {
  const pending = pendingInvoices(invoices);
  const overdue = pending.filter((item) => item.status === 'OVERDUE').length;
  return { value: String(pending.length), trend: `${overdue} en retard` };
}

export function recentDocumentsMetric(stats) {
  return { value: String(stats?.lastSevenDays ?? 0), trend: `${stats?.today ?? 0} aujourd'hui` };
}

const APPOINTMENT_TYPE_LABELS = {
  CLIENT_MEETING: 'Rendez-vous client',
  COURT_HEARING: 'Audience',
  CONSULTATION: 'Consultation',
  INTERNAL_MEETING: 'Réunion interne'
};

export function agendaItems(appointments, clients, now) {
  const names = new Map(asList(clients).map((item) => [item.id, `${item.firstName ?? ''} ${item.lastName ?? ''}`.trim()]));
  return todaysAppointments(appointments, now).map((item) => {
    const date = dayOf(item.dateTime);
    const label = APPOINTMENT_TYPE_LABELS[item.type] || item.type;
    const clientName = names.get(item.clientId);
    let status = 'À venir';
    let tone = 'amber';
    if (item.status === 'COMPLETED') {
      status = 'Terminé';
      tone = 'emerald';
    } else if (date < now) {
      // Planifié mais l'heure est passée sans avoir été marqué comme terminé : la secrétaire doit le clôturer.
      status = 'À clôturer';
      tone = 'rose';
    }
    return {
      key: item.id,
      name: clientName ? `${label} — ${clientName}` : label,
      type: `${formatTime(item.dateTime)}${item.location ? ` · ${item.location}` : ''}`,
      status,
      tone
    };
  });
}

export function newClientsText(clients, now) {
  return `${plural(newClientsSince(clients, now).length, 'nouveau client', 'nouveaux clients')} cette semaine`;
}

export function modifiedCasesText(cases, now) {
  return `${plural(casesModifiedToday(cases, now).length, 'dossier modifié', 'dossiers modifiés')} aujourd'hui`;
}

export function draftInvoicesText(invoices) {
  const drafts = asList(invoices).filter((item) => item.status === 'DRAFT').length;
  return `${plural(drafts, 'facture', 'factures')} en attente d'envoi`;
}

// ---------- Administrateur ----------

export function activeUsersMetric(users) {
  const all = asList(users);
  const active = all.filter((item) => item.enabled).length;
  return { value: String(active), trend: `${all.length - active} désactivé(s)` };
}

export function roleUsersMetric(users, role) {
  const group = asList(users).filter((item) => item.role === role);
  const active = group.filter((item) => item.enabled).length;
  return { value: String(active), trend: `${group.length - active} désactivé(s)` };
}

export function billingMetric(invoices, now, formatAmount) {
  const billed = asList(invoices).filter((item) => {
    const created = dayOf(item.createdAt);
    return BILLED_STATUSES.includes(item.status) && created
      && created.getFullYear() === now.getFullYear() && created.getMonth() === now.getMonth();
  });
  const total = billed.reduce((sum, item) => sum + Number(item.amount || 0), 0);
  return { value: formatAmount(total), trend: `${plural(billed.length, 'facture', 'factures')} ce mois` };
}

/** Derniers éléments créés, tous services confondus. Une source indisponible est simplement absente. */
export function recentActivity({ cases, consultations, invoices }, limit = 5) {
  const entries = [
    ...asList(cases).map((item) => ({
      key: `case-${item.id}`, created: item.createdAt, name: `Dossier : ${item.title}`, status: 'Dossier', tone: 'blue'
    })),
    ...asList(consultations).map((item) => ({
      key: `consultation-${item.id}`, created: item.createdAt, name: `Consultation : ${item.subject}`, status: 'Consultation', tone: 'violet'
    })),
    ...asList(invoices).map((item) => ({
      key: `invoice-${item.id}`, created: item.createdAt, name: `Facture ${item.invoiceNumber}`, status: 'Facture', tone: 'amber'
    }))
  ];
  return entries
    .map((entry) => ({ ...entry, date: dayOf(entry.created) }))
    .filter((entry) => entry.date)
    .sort((a, b) => b.date - a.date)
    .slice(0, limit)
    .map(({ key, name, status, tone, date }) => ({ key, name, type: `Créé le ${formatShortDate(date)} à ${formatTime(date)}`, status, tone }));
}

export function consultationsToAssignText(consultations) {
  const pending = asList(consultations).filter((item) => item.status === 'PENDING').length;
  return pending === 0 ? 'Aucune consultation à assigner' : `${plural(pending, 'consultation', 'consultations')} à assigner`;
}

export function overdueInvoicesText(invoices) {
  const overdue = asList(invoices).filter((item) => item.status === 'OVERDUE').length;
  return overdue === 0 ? 'Aucune facture en retard' : `${plural(overdue, 'facture', 'factures')} en retard`;
}

export function disabledAccountsText(users) {
  const disabled = asList(users).filter((item) => !item.enabled).length;
  return disabled === 0 ? 'Aucun compte désactivé' : `${plural(disabled, 'compte désactivé', 'comptes désactivés')} à vérifier`;
}
