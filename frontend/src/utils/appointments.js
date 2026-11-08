// Logique pure de la page « Rendez-vous » : regroupement, droits d'action, validation et corps de requête.
// Les dates du serveur sont des heures locales sans fuseau (LocalDateTime) : on les lit telles quelles.

const NOTES_MAX = 2000;
const LOCATION_MAX = 500;

export function parseDateTime(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatDateTime(value) {
  const date = parseDateTime(value);
  if (!date) return '—';
  const day = date.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  const time = `${String(date.getHours()).padStart(2, '0')}h${String(date.getMinutes()).padStart(2, '0')}`;
  return `${day} à ${time}`;
}

/** Valeur d'un champ datetime-local (« 2026-10-20T14:30 ») à partir de la date du serveur. */
export function toInputValue(value) {
  return typeof value === 'string' ? value.slice(0, 16) : '';
}

export function toApiDateTime(inputValue) {
  return inputValue.length === 16 ? `${inputValue}:00` : inputValue;
}

/** « Maintenant » au format d'un champ datetime-local, pour borner le choix de la date. */
export function nowAsInputValue(now = new Date()) {
  const pad = (number) => String(number).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}`;
}

const byDateAsc = (a, b) => (parseDateTime(a.dateTime) ?? 0) - (parseDateTime(b.dateTime) ?? 0);
const byDateDesc = (a, b) => byDateAsc(b, a);

/**
 * À venir : planifiés dont l'heure n'est pas passée. À clôturer : planifiés dont l'heure est passée
 * (à marquer terminés ou annulés). Terminés et annulés : l'historique, le plus récent d'abord.
 */
export function groupAppointments(appointments, now) {
  const list = Array.isArray(appointments) ? appointments : [];
  const scheduled = list.filter((item) => item.status === 'SCHEDULED');
  const isPast = (item) => {
    const date = parseDateTime(item.dateTime);
    return date !== null && date < now;
  };
  return {
    upcoming: scheduled.filter((item) => !isPast(item)).sort(byDateAsc),
    toClose: scheduled.filter(isPast).sort(byDateAsc),
    completed: list.filter((item) => item.status === 'COMPLETED').sort(byDateDesc),
    cancelled: list.filter((item) => item.status === 'CANCELLED').sort(byDateDesc)
  };
}

/**
 * Actions proposées pour un rendez-vous. Le serveur reste l'autorité (il refuse ce qui est interdit) ;
 * l'interface ne propose que ce qui peut réussir. « Terminé » n'est proposé qu'une fois l'heure passée.
 */
export function appointmentActions(appointment, { isOffice, now }) {
  const scheduled = appointment.status === 'SCHEDULED';
  const date = parseDateTime(appointment.dateTime);
  return {
    canEdit: scheduled,
    canCancel: scheduled,
    canComplete: scheduled && date !== null && date <= now,
    canDelete: Boolean(isOffice)
  };
}

export function emptyForm() {
  return { clientId: '', caseId: '', lawyerId: '', type: 'CLIENT_MEETING', dateTime: '', location: '', notes: '' };
}

export function validateForm(form, { editing, isOffice, now }) {
  const errors = {};
  if (!editing && !form.clientId) errors.clientId = 'Choisissez un client.';
  if (!editing && isOffice && !form.lawyerId) errors.lawyerId = 'Choisissez un avocat.';
  if (!form.type) errors.type = 'Choisissez un type de rendez-vous.';
  if (!form.dateTime) {
    errors.dateTime = "Indiquez la date et l'heure.";
  } else {
    const date = parseDateTime(form.dateTime);
    if (date === null) errors.dateTime = 'Date invalide.';
    else if (date <= now) errors.dateTime = 'La date doit être dans le futur.';
  }
  if (form.location.length > LOCATION_MAX) errors.location = `Le lieu ne doit pas dépasser ${LOCATION_MAX} caractères.`;
  if (form.notes.length > NOTES_MAX) errors.notes = `Les notes ne doivent pas dépasser ${NOTES_MAX} caractères.`;
  return errors;
}

const textOrNull = (value) => {
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
};

export function buildCreatePayload(form, lawyerId) {
  return {
    clientId: form.clientId,
    caseId: form.caseId || null,
    lawyerId,
    dateTime: toApiDateTime(form.dateTime),
    type: form.type,
    location: textOrNull(form.location),
    notes: textOrNull(form.notes)
  };
}

export function buildUpdatePayload(form) {
  return {
    dateTime: toApiDateTime(form.dateTime),
    type: form.type,
    location: textOrNull(form.location),
    notes: textOrNull(form.notes)
  };
}

export function describeAppointmentError(error) {
  switch (error?.status) {
    case 400:
      return 'Les informations saisies sont invalides (la date doit être dans le futur).';
    case 401:
      return 'Votre session a expiré : reconnectez-vous.';
    case 403:
      return "Vous n'avez pas le droit d'effectuer cette action.";
    case 404:
      return 'Rendez-vous introuvable : il a été supprimé ou ne fait pas partie de votre périmètre.';
    case 409:
      return 'Ce rendez-vous ne peut plus être modifié (déjà terminé ou annulé).';
    case 422:
      return 'Le client ou le dossier choisi est introuvable dans votre cabinet.';
    default:
      return error?.status >= 500
        ? 'Le service des rendez-vous est momentanément indisponible. Réessayez dans un instant.'
        : error?.message || "L'action a échoué.";
  }
}
