import { isActiveCase } from '../constants/caseStatus.js';

// Calculs purs des chiffres des tableaux de bord : aucune donnée n'est inventée, tout vient des API.
// Chaque fonction reçoit « now » pour être testable.

const DAY_MS = 24 * 60 * 60 * 1000;
const RECENT_DAYS = 7;
// Factures que le client doit encore régler (un brouillon n'est pas encore émis ; payée ou annulée = rien à payer).
const PENDING_INVOICE_STATUSES = ['SENT', 'OVERDUE'];

export function startOfDay(date) {
  const copy = new Date(date);
  copy.setHours(0, 0, 0, 0);
  return copy;
}

// « 2026-11-02 » (date seule) doit rester le 2 novembre local, pas être décalé par le fuseau horaire.
function parseDate(value) {
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split('-').map(Number);
    return new Date(year, month - 1, day);
  }
  return new Date(value);
}

export function validDate(value) {
  if (!value) return null;
  const date = parseDate(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatShortDate(value) {
  const date = validDate(value);
  return date ? date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long' }) : '—';
}

export function formatTime(value) {
  const date = validDate(value);
  if (!date) return '';
  return `${String(date.getHours()).padStart(2, '0')}h${String(date.getMinutes()).padStart(2, '0')}`;
}

export function relativeDay(value, now) {
  const date = validDate(value);
  if (!date) return '';
  const days = Math.round((startOfDay(date) - startOfDay(now)) / DAY_MS);
  if (days <= 0) return "Aujourd'hui";
  if (days === 1) return 'Demain';
  return `Dans ${days} jours`;
}

export function plural(count, singular, pluralForm) {
  return `${count} ${count > 1 ? pluralForm : singular}`;
}

function upcomingScheduled(items, now) {
  return (Array.isArray(items) ? items : [])
    .filter((item) => item.status === 'SCHEDULED')
    .map((item) => ({ item, date: validDate(item.dateTime) }))
    .filter(({ date }) => date && date >= now)
    .sort((a, b) => a.date - b.date);
}

export function casesMetric(cases, now) {
  const all = Array.isArray(cases) ? cases : [];
  const openedThisMonth = all.filter((item) => {
    const created = validDate(item.createdAt);
    return created && created.getFullYear() === now.getFullYear() && created.getMonth() === now.getMonth();
  });
  return {
    value: String(all.filter(isActiveCase).length),
    trend: `${openedThisMonth.length} ouvert(s) ce mois`
  };
}

export function documentsMetric(documents, now) {
  const all = Array.isArray(documents) ? documents : [];
  const since = now.getTime() - RECENT_DAYS * DAY_MS;
  const recent = all.filter((item) => {
    const created = validDate(item.createdAt);
    return created && created.getTime() >= since;
  });
  return { value: String(all.length), trend: `${recent.length} récent(s) (7 j)` };
}

export function hearingMetric(hearings, now) {
  const next = upcomingScheduled(hearings, now)[0];
  if (!next) return { value: 'Aucune', trend: 'Rien de planifié' };
  return { value: formatShortDate(next.item.dateTime), trend: relativeDay(next.item.dateTime, now) };
}

export function pendingInvoices(invoices) {
  return (Array.isArray(invoices) ? invoices : []).filter((item) => PENDING_INVOICE_STATUSES.includes(item.status));
}

export function paymentMetric(invoices, formatAmount) {
  const pending = pendingInvoices(invoices);
  if (pending.length === 0) return { value: formatAmount(0), trend: 'À jour' };
  const total = pending.reduce((sum, item) => sum + Number(item.amount || 0), 0);
  const overdue = pending.filter((item) => item.status === 'OVERDUE').length;
  const trend = overdue > 0
    ? `${plural(pending.length, 'facture', 'factures')} dont ${overdue} en retard`
    : plural(pending.length, 'facture en attente', 'factures en attente');
  return { value: formatAmount(total), trend };
}

export function notificationsMetric(notifications) {
  const all = Array.isArray(notifications) ? notifications : [];
  const unread = all.filter((item) => !item.read).length;
  return { value: String(all.length), trend: `${unread} non lue(s)` };
}

export function nextAppointmentText(appointments, now) {
  const next = upcomingScheduled(appointments, now)[0];
  if (!next) return 'Aucun rendez-vous à venir';
  return `Prochain rendez-vous : ${formatShortDate(next.item.dateTime)} à ${formatTime(next.item.dateTime)}`;
}

export function latestDocumentText(documents) {
  const sorted = (Array.isArray(documents) ? documents : [])
    .filter((item) => validDate(item.createdAt))
    .sort((a, b) => validDate(b.createdAt) - validDate(a.createdAt));
  return sorted.length === 0 ? 'Aucun document partagé' : `Dernier document : ${sorted[0].fileName}`;
}

export function pendingInvoiceText(invoices) {
  const sorted = pendingInvoices(invoices)
    .filter((item) => validDate(item.dueDate))
    .sort((a, b) => validDate(a.dueDate) - validDate(b.dueDate));
  if (sorted.length === 0) return 'Aucun paiement en attente';
  const first = sorted[0];
  return `Paiement en attente : facture ${first.invoiceNumber} (échéance ${formatShortDate(first.dueDate)})`;
}
