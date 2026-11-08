// Logique pure de la page « Factures » : regroupement, retards, totaux, droits d'action, validation et corps de requête.
// Les montants et dates viennent du serveur ; rien n'est inventé ici.

const DAY_MS = 24 * 60 * 60 * 1000;
const NUMBER_MAX = 50;
const DESCRIPTION_MAX = 255;
const AMOUNT_MAX = 1e12;

// Le modèle de facture n'a pas de champ « devise » : le symbole affiché est un choix d'affichage.
export const CURRENCY_SYMBOL = '€';

export function formatMoney(value) {
  const formatted = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(value) || 0);
  return `${formatted} ${CURRENCY_SYMBOL}`;
}

function startOfDay(date) {
  const copy = new Date(date);
  copy.setHours(0, 0, 0, 0);
  return copy;
}

/** « 2026-11-02 » (date seule) reste le 2 novembre local, sans décalage de fuseau. */
export function parseDay(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}/.test(value)) return null;
  const [year, month, day] = value.slice(0, 10).split('-').map(Number);
  return new Date(year, month - 1, day);
}

export function formatDay(value) {
  const date = parseDay(value) ?? (value ? new Date(value) : null);
  if (!date || Number.isNaN(date.getTime())) return '—';
  return date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
}

/**
 * Une facture envoyée dont l'échéance est dépassée est « en retard » même si le planificateur du serveur ne l'a pas
 * encore marquée : l'avocat doit la voir tout de suite.
 */
export function isLate(invoice, now) {
  if (invoice.status === 'OVERDUE') return true;
  const due = parseDay(invoice.dueDate);
  return invoice.status === 'SENT' && due !== null && due < startOfDay(now);
}

const byDueAsc = (a, b) => (parseDay(a.dueDate) ?? 0) - (parseDay(b.dueDate) ?? 0);

export function groupInvoices(invoices, now) {
  const list = Array.isArray(invoices) ? invoices : [];
  return {
    open: list.filter((item) => item.status === 'SENT' && !isLate(item, now)).sort(byDueAsc),
    late: list.filter((item) => isLate(item, now)).sort(byDueAsc),
    paid: list.filter((item) => item.status === 'PAID').sort((a, b) => byDueAsc(b, a)),
    cancelled: list.filter((item) => item.status === 'CANCELLED').sort((a, b) => byDueAsc(b, a)),
    draft: list.filter((item) => item.status === 'DRAFT').sort(byDueAsc)
  };
}

/** « Dans 3 jours », « Aujourd'hui », « En retard de 2 jours » pour une facture encore à encaisser. */
export function describeDelay(invoice, now) {
  if (invoice.status !== 'SENT' && invoice.status !== 'OVERDUE') return '';
  const due = parseDay(invoice.dueDate);
  if (!due) return '';
  const days = Math.round((due - startOfDay(now)) / DAY_MS);
  if (days < 0) return `En retard de ${-days} jour${-days > 1 ? 's' : ''}`;
  if (days === 0) return "Échéance aujourd'hui";
  if (days === 1) return 'Échéance demain';
  return `Dans ${days} jours`;
}

const cents = (value) => Math.round((Number(value) || 0) * 100);
const total = (items) => items.reduce((sum, item) => sum + cents(item.amount), 0) / 100;

/** Montants à encaisser (envoyées et en retard), en retard, et déjà encaissés (payées). */
export function invoiceTotals(invoices, now) {
  const groups = groupInvoices(invoices, now);
  const toCollect = [...groups.open, ...groups.late];
  return {
    toCollect: { count: toCollect.length, amount: total(toCollect) },
    late: { count: groups.late.length, amount: total(groups.late) },
    collected: { count: groups.paid.length, amount: total(groups.paid) }
  };
}

/**
 * Droits proposés. Le serveur reste l'autorité : une facture payée ou annulée est figée, la suppression est réservée à
 * l'administrateur, et seules ces transitions existent : brouillon → envoyée ; envoyée ou en retard → payée.
 */
export function invoiceActions(invoice, { isAdmin }) {
  const open = ['DRAFT', 'SENT', 'OVERDUE'].includes(invoice.status);
  return {
    canEdit: open,
    canSend: invoice.status === 'DRAFT',
    canPay: invoice.status === 'SENT' || invoice.status === 'OVERDUE',
    canCancel: open,
    canDelete: Boolean(isAdmin)
  };
}

/** Prochain numéro « F-AAAA-NNNN » d'après les factures déjà visibles ; le serveur vérifie l'unicité. */
export function suggestInvoiceNumber(invoices, now) {
  const year = now.getFullYear();
  const pattern = new RegExp(`^F-${year}-(\\d+)$`);
  const highest = (Array.isArray(invoices) ? invoices : []).reduce((max, item) => {
    const match = pattern.exec(item.invoiceNumber || '');
    return match ? Math.max(max, Number(match[1])) : max;
  }, 0);
  return `F-${year}-${String(highest + 1).padStart(4, '0')}`;
}

export function emptyForm(invoiceNumber = '') {
  return { caseId: '', invoiceNumber, description: '', amount: '', dueDate: '' };
}

/** Accepte « 1250,5 » comme « 1250.5 » ; renvoie NaN si ce n'est pas un nombre. */
export function parseAmount(text) {
  const normalized = String(text ?? '').trim().replace(/\s/g, '').replace(',', '.');
  return normalized === '' ? NaN : Number(normalized);
}

export function validateForm(form, { editing }) {
  const errors = {};
  if (!editing && !form.caseId) errors.caseId = 'Choisissez un dossier.';
  if (!form.invoiceNumber.trim()) errors.invoiceNumber = 'Indiquez le numéro de facture.';
  else if (form.invoiceNumber.trim().length > NUMBER_MAX) errors.invoiceNumber = `Le numéro ne doit pas dépasser ${NUMBER_MAX} caractères.`;
  if (!form.description.trim()) errors.description = 'Indiquez la description.';
  else if (form.description.trim().length > DESCRIPTION_MAX) errors.description = `La description ne doit pas dépasser ${DESCRIPTION_MAX} caractères.`;
  const amount = parseAmount(form.amount);
  if (!Number.isFinite(amount) || amount < 0.01) errors.amount = 'Indiquez un montant supérieur à 0.';
  else if (amount > AMOUNT_MAX) errors.amount = 'Montant trop élevé.';
  else if (Math.abs(amount * 100 - Math.round(amount * 100)) > 1e-6) errors.amount = 'Au plus deux décimales.';
  if (!form.dueDate || !parseDay(form.dueDate)) errors.dueDate = "Indiquez la date d'échéance.";
  return errors;
}

/** Le cabinet vient du jeton, le client du dossier : jamais d'une saisie libre. */
export function buildPayload(form, { lawFirmId, caseId, clientId }) {
  return {
    lawFirmId,
    caseId,
    clientId,
    invoiceNumber: form.invoiceNumber.trim(),
    description: form.description.trim(),
    amount: Math.round(parseAmount(form.amount) * 100) / 100,
    dueDate: form.dueDate
  };
}

export function describeInvoiceError(error, { saving = false } = {}) {
  switch (error?.status) {
    case 400:
      return 'Les informations saisies sont invalides (montant supérieur à 0, numéro et description obligatoires).';
    case 401:
      return 'Votre session a expiré : reconnectez-vous.';
    case 403:
      return "Vous n'avez pas le droit d'effectuer cette action.";
    case 404:
      return 'Facture introuvable : elle a été supprimée ou ne fait pas partie de votre périmètre.';
    case 409:
      if (/number already exists/i.test(error.message || '')) return 'Ce numéro de facture existe déjà dans votre cabinet.';
      if (/no longer be modified/i.test(error.message || '')) return 'Une facture payée ou annulée ne peut plus être modifiée.';
      return "Ce changement de statut n'est pas autorisé pour cette facture.";
    case 422:
      return 'Le dossier choisi est introuvable dans votre cabinet.';
    default:
      if (error?.status >= 500) {
        return saving
          ? "L'enregistrement a échoué. Si cela se reproduit, ce numéro de facture est peut-être déjà utilisé : essayez-en un autre."
          : 'Le service de facturation est momentanément indisponible. Réessayez dans un instant.';
      }
      return error?.message || "L'action a échoué.";
  }
}
