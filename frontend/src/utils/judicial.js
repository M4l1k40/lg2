import {
  CASE_ACTION_LABELS,
  CASE_TRANSITIONS,
  HEARING_TRANSITIONS
} from '../constants/judicialStatus.js';

// Logique pure du module judiciaire : transitions, regroupement des audiences, validation et corps de requête.
// Les dates du serveur sont des heures locales sans fuseau (LocalDateTime) : on les lit telles quelles.

const NAME_MAX = 255;
const TEXT_MAX = 255;

export function parseDateTime(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatDateTime(value) {
  const date = parseDateTime(value);
  if (!date) return '—';
  const day = date.toLocaleDateString('fr-FR', { weekday: 'short', day: 'numeric', month: 'long', year: 'numeric' });
  const time = `${String(date.getHours()).padStart(2, '0')}h${String(date.getMinutes()).padStart(2, '0')}`;
  return `${day} à ${time}`;
}

export function formatDay(value) {
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}/.test(value)) {
    const [year, month, day] = value.slice(0, 10).split('-').map(Number);
    return new Date(year, month - 1, day).toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
  }
  return '—';
}

export function toInputDateTime(now = new Date()) {
  const pad = (number) => String(number).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}`;
}

export function toInputDay(now = new Date()) {
  return toInputDateTime(now).slice(0, 10);
}

export const toApiDateTime = (inputValue) => (inputValue.length === 16 ? `${inputValue}:00` : inputValue);

export const judgeName = (judge) => (judge ? `${judge.firstName ?? ''} ${judge.lastName ?? ''}`.trim() : '');
export const courtLabel = (court) => (court ? `${court.name}${court.city ? ` — ${court.city}` : ''}` : '');

// ---------- Transitions ----------

/** Changements de statut proposés pour une procédure (sans « audience programmée », qui vient de la planification). */
export function caseTransitions(status) {
  return (CASE_TRANSITIONS[status] || []).filter((next) => CASE_ACTION_LABELS[next]);
}

export function hearingTransitions(status) {
  return HEARING_TRANSITIONS[status] || [];
}

// ---------- Audiences ----------

/**
 * À venir : planifiées dont l'heure n'est pas passée. À traiter : planifiées dont l'heure est passée (à marquer tenues,
 * reportées ou annulées) et reportées (à replanifier ou annuler). Historique : tenues et annulées.
 */
export function groupHearings(hearings, now) {
  const list = Array.isArray(hearings) ? hearings : [];
  const asc = (a, b) => (parseDateTime(a.dateTime) ?? 0) - (parseDateTime(b.dateTime) ?? 0);
  const past = (item) => {
    const date = parseDateTime(item.dateTime);
    return date !== null && date < now;
  };
  return {
    upcoming: list.filter((item) => item.status === 'SCHEDULED' && !past(item)).sort(asc),
    toHandle: list.filter((item) => (item.status === 'SCHEDULED' && past(item)) || item.status === 'POSTPONED').sort(asc),
    history: list.filter((item) => item.status === 'COMPLETED' || item.status === 'CANCELLED').sort((a, b) => asc(b, a))
  };
}

/** Toutes les audiences, chacune avec la procédure à laquelle elle appartient. */
export function flattenHearings(judicialCases, hearingsByCase) {
  return (Array.isArray(judicialCases) ? judicialCases : []).flatMap((judicialCase) =>
    (hearingsByCase?.[judicialCase.id] || []).map((hearing) => ({ ...hearing, judicialCaseId: judicialCase.id }))
  );
}

export function nextHearing(hearings, now) {
  return groupHearings(hearings, now).upcoming[0] || null;
}

// ---------- Formulaires ----------

const trimmedOrNull = (value) => {
  const trimmed = (value ?? '').trim();
  return trimmed === '' ? null : trimmed;
};

export const emptyCourtForm = () => ({ name: '', type: '', city: '', address: '' });

export function validateCourt(form) {
  const errors = {};
  if (!form.name.trim()) errors.name = 'Indiquez le nom du tribunal.';
  else if (form.name.length > NAME_MAX) errors.name = `Le nom ne doit pas dépasser ${NAME_MAX} caractères.`;
  if (!form.type.trim()) errors.type = 'Indiquez le type de tribunal.';
  if (!form.city.trim()) errors.city = 'Indiquez la ville.';
  return errors;
}

export const buildCourtPayload = (form) => ({
  name: form.name.trim(), type: form.type.trim(), city: form.city.trim(), address: trimmedOrNull(form.address)
});

export const emptyJudgeForm = () => ({ courtId: '', firstName: '', lastName: '', role: '' });

export function validateJudge(form) {
  const errors = {};
  if (!form.courtId) errors.courtId = 'Choisissez un tribunal.';
  if (!form.firstName.trim()) errors.firstName = 'Indiquez le prénom.';
  if (!form.lastName.trim()) errors.lastName = 'Indiquez le nom.';
  if (!form.role.trim()) errors.role = 'Indiquez la fonction.';
  return errors;
}

export const buildJudgePayload = (form) => ({
  courtId: form.courtId, firstName: form.firstName.trim(), lastName: form.lastName.trim(), role: form.role.trim()
});

export const emptyJudicialCaseForm = (today = toInputDay()) => ({
  caseId: '', courtId: '', judgeId: '', courtCaseNumber: '', procedureType: 'CIVIL', openingDate: today
});

export function validateJudicialCase(form) {
  const errors = {};
  if (!form.caseId) errors.caseId = 'Choisissez un dossier.';
  if (!form.courtId) errors.courtId = 'Choisissez un tribunal.';
  if (!form.judgeId) errors.judgeId = 'Choisissez un juge.';
  if (!form.courtCaseNumber.trim()) errors.courtCaseNumber = "Indiquez le numéro d'affaire du tribunal.";
  else if (form.courtCaseNumber.length > TEXT_MAX) errors.courtCaseNumber = `Le numéro ne doit pas dépasser ${TEXT_MAX} caractères.`;
  if (!form.procedureType) errors.procedureType = 'Choisissez un type de procédure.';
  if (!form.openingDate) errors.openingDate = "Indiquez la date d'ouverture.";
  return errors;
}

/** Le cabinet vient du jeton ; la procédure démarre toujours à l'état « enregistrée ». */
export const buildJudicialCasePayload = (form, lawFirmId) => ({
  lawFirmId,
  caseId: form.caseId,
  courtId: form.courtId,
  judgeId: form.judgeId,
  courtCaseNumber: form.courtCaseNumber.trim(),
  procedureType: form.procedureType,
  status: 'REGISTERED',
  openingDate: form.openingDate
});

export const emptyHearingForm = () => ({ dateTime: '', type: 'FIRST_HEARING', location: '', notes: '', nextAction: '' });

export function validateHearing(form) {
  const errors = {};
  if (!form.dateTime || !parseDateTime(form.dateTime)) errors.dateTime = "Indiquez la date et l'heure de l'audience.";
  if (!form.type) errors.type = "Choisissez un type d'audience.";
  return errors;
}

export const buildHearingPayload = (form) => ({
  dateTime: toApiDateTime(form.dateTime),
  type: form.type,
  location: trimmedOrNull(form.location),
  notes: trimmedOrNull(form.notes),
  nextAction: trimmedOrNull(form.nextAction)
});

export function describeJudicialError(error) {
  switch (error?.status) {
    case 400:
      return 'Les informations saisies sont invalides ou incomplètes (le juge doit appartenir au tribunal choisi).';
    case 401:
      return 'Votre session a expiré : reconnectez-vous.';
    case 403:
      return "Vous n'avez pas le droit d'effectuer cette action.";
    case 404:
      return 'Élément introuvable : il a été supprimé ou ne fait pas partie de votre périmètre.';
    case 409:
      if (/closed judicial case/i.test(error.message || '')) return 'Cette procédure est clôturée : on ne peut plus y planifier d\'audience.';
      return "Ce changement de statut n'est pas autorisé depuis l'état actuel.";
    case 422:
      return 'Le dossier choisi est introuvable dans votre cabinet.';
    default:
      return error?.status >= 500
        ? 'Le service judiciaire est momentanément indisponible. Réessayez dans un instant.'
        : error?.message || "L'action a échoué.";
  }
}
