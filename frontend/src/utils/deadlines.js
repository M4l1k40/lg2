// Logique pure de la page « Échéances » : regroupement, délais, droits d'action, validation et corps de requête.
// Les dates du serveur sont des heures locales sans fuseau (LocalDateTime) : on les lit telles quelles.

const DAY_MS = 24 * 60 * 60 * 1000;
const TITLE_MAX = 255;
const DESCRIPTION_MAX = 2000;

export function parseDateTime(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function startOfDay(date) {
  const copy = new Date(date);
  copy.setHours(0, 0, 0, 0);
  return copy;
}

export function formatDueDate(value) {
  const date = parseDateTime(value);
  if (!date) return '—';
  const day = date.toLocaleDateString('fr-FR', { weekday: 'short', day: 'numeric', month: 'long', year: 'numeric' });
  const time = `${String(date.getHours()).padStart(2, '0')}h${String(date.getMinutes()).padStart(2, '0')}`;
  return `${day} à ${time}`;
}

export function toInputValue(value) {
  return typeof value === 'string' ? value.slice(0, 16) : '';
}

export function toApiDateTime(inputValue) {
  return inputValue.length === 16 ? `${inputValue}:00` : inputValue;
}

export function nowAsInputValue(now = new Date()) {
  const pad = (number) => String(number).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}`;
}

/**
 * Une échéance à faire dont la date est dépassée est « en retard » même si le planificateur du serveur ne l'a pas
 * encore marquée : l'utilisateur doit la voir tout de suite.
 */
export function isLate(deadline, now) {
  if (deadline.status === 'OVERDUE') return true;
  const due = parseDateTime(deadline.dueDate);
  return deadline.status === 'PENDING' && due !== null && due < now;
}

const byDueAsc = (a, b) => (parseDateTime(a.dueDate) ?? 0) - (parseDateTime(b.dueDate) ?? 0);

export function groupDeadlines(deadlines, now) {
  const list = Array.isArray(deadlines) ? deadlines : [];
  return {
    upcoming: list.filter((item) => item.status === 'PENDING' && !isLate(item, now)).sort(byDueAsc),
    late: list.filter((item) => isLate(item, now)).sort(byDueAsc),
    completed: list.filter((item) => item.status === 'COMPLETED').sort((a, b) => byDueAsc(b, a)),
    cancelled: list.filter((item) => item.status === 'CANCELLED').sort((a, b) => byDueAsc(b, a))
  };
}

/** « Dans 3 jours », « Demain », « Aujourd'hui », « En retard de 2 jours » (jours calendaires). */
export function describeDelay(deadline, now) {
  if (deadline.status === 'COMPLETED' || deadline.status === 'CANCELLED') return '';
  const due = parseDateTime(deadline.dueDate);
  if (!due) return '';
  const days = Math.round((startOfDay(due) - startOfDay(now)) / DAY_MS);
  if (due < now) return days === 0 ? "En retard (aujourd'hui)" : `En retard de ${-days} jour${-days > 1 ? 's' : ''}`;
  if (days === 0) return "Aujourd'hui";
  if (days === 1) return 'Demain';
  return `Dans ${days} jours`;
}

/**
 * Le serveur exige une date limite future pour toute modification : une échéance déjà en retard ne peut donc plus
 * être modifiée, seulement terminée, annulée ou supprimée.
 */
export function deadlineActions(deadline, now) {
  const pending = deadline.status === 'PENDING' && !isLate(deadline, now);
  const open = deadline.status === 'PENDING' || deadline.status === 'OVERDUE';
  return { canEdit: pending, canComplete: open, canCancel: open, canDelete: true };
}

export function filterByPriority(deadlines, priority) {
  return priority ? deadlines.filter((item) => item.priority === priority) : deadlines;
}

export function emptyForm() {
  return { caseId: '', title: '', description: '', dueDate: '', priority: 'MEDIUM' };
}

export function validateForm(form, now) {
  const errors = {};
  if (!form.caseId) errors.caseId = 'Choisissez un dossier.';
  if (!form.title.trim()) errors.title = 'Indiquez un intitulé.';
  else if (form.title.length > TITLE_MAX) errors.title = `L'intitulé ne doit pas dépasser ${TITLE_MAX} caractères.`;
  if (!form.dueDate) {
    errors.dueDate = 'Indiquez la date limite.';
  } else {
    const due = parseDateTime(form.dueDate);
    if (due === null) errors.dueDate = 'Date invalide.';
    else if (due <= now) errors.dueDate = 'La date limite doit être dans le futur.';
  }
  if (!form.priority) errors.priority = 'Choisissez une priorité.';
  if (form.description.length > DESCRIPTION_MAX) {
    errors.description = `La description ne doit pas dépasser ${DESCRIPTION_MAX} caractères.`;
  }
  return errors;
}

export function buildPayload(form) {
  const description = form.description.trim();
  return {
    caseId: form.caseId,
    title: form.title.trim(),
    description: description === '' ? null : description,
    dueDate: toApiDateTime(form.dueDate),
    priority: form.priority
  };
}

export function describeDeadlineError(error) {
  switch (error?.status) {
    case 400:
      return 'Les informations saisies sont invalides (la date limite doit être dans le futur).';
    case 401:
      return 'Votre session a expiré : reconnectez-vous.';
    case 403:
      return "Vous n'avez pas le droit d'effectuer cette action.";
    case 404:
      return 'Échéance introuvable : elle a été supprimée ou ne fait pas partie de votre périmètre.';
    case 409:
      return "Cette échéance ne peut pas passer à ce statut (déjà terminée ou annulée).";
    case 422:
      return 'Le dossier choisi est introuvable dans votre cabinet.';
    default:
      return error?.status >= 500
        ? 'Le service des échéances est momentanément indisponible. Réessayez dans un instant.'
        : error?.message || "L'action a échoué.";
  }
}
