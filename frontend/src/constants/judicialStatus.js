// Libellés, couleurs et transitions du module judiciaire. Les transitions reproduisent celles du serveur
// (judicial-service) ; c'est le serveur qui fait autorité et refuse tout changement interdit.

export const PROCEDURE_TYPES = [
  { value: 'CIVIL', label: 'Civile' },
  { value: 'COMMERCIAL', label: 'Commerciale' },
  { value: 'CRIMINAL', label: 'Pénale' },
  { value: 'ADMINISTRATIVE', label: 'Administrative' }
];

export const HEARING_TYPES = [
  { value: 'FIRST_HEARING', label: 'Première audience' },
  { value: 'FOLLOW_UP', label: 'Audience de suivi' },
  { value: 'PLEADING', label: 'Plaidoirie' },
  { value: 'DELIBERATION', label: 'Délibéré' }
];

// Suggestions de saisie : le type de tribunal et la fonction d'un juge sont du texte libre côté serveur.
export const COURT_TYPE_SUGGESTIONS = [
  'Tribunal de première instance',
  'Tribunal de commerce',
  'Tribunal administratif',
  "Cour d'appel",
  'Cour de cassation'
];
export const JUDGE_ROLE_SUGGESTIONS = ['Président', 'Juge', "Juge d'instruction", 'Conseiller', 'Procureur'];

const CASE_STATUS = {
  REGISTERED: { label: 'Enregistrée', tone: 'slate' },
  IN_PROGRESS: { label: 'En cours', tone: 'blue' },
  HEARING_SCHEDULED: { label: 'Audience programmée', tone: 'violet' },
  DELIBERATION: { label: 'En délibéré', tone: 'amber' },
  JUDGMENT_ISSUED: { label: 'Jugement rendu', tone: 'cyan' },
  CLOSED: { label: 'Clôturée', tone: 'emerald' }
};

const HEARING_STATUS = {
  SCHEDULED: { label: 'Planifiée', tone: 'blue' },
  COMPLETED: { label: 'Tenue', tone: 'emerald' },
  POSTPONED: { label: 'Reportée', tone: 'amber' },
  CANCELLED: { label: 'Annulée', tone: 'rose' }
};

export const CASE_TRANSITIONS = {
  REGISTERED: ['IN_PROGRESS', 'HEARING_SCHEDULED'],
  IN_PROGRESS: ['HEARING_SCHEDULED', 'DELIBERATION', 'CLOSED'],
  HEARING_SCHEDULED: ['DELIBERATION', 'CLOSED'],
  DELIBERATION: ['JUDGMENT_ISSUED', 'CLOSED'],
  JUDGMENT_ISSUED: ['CLOSED'],
  CLOSED: []
};

export const HEARING_TRANSITIONS = {
  SCHEDULED: ['COMPLETED', 'POSTPONED', 'CANCELLED'],
  POSTPONED: ['SCHEDULED', 'CANCELLED'],
  COMPLETED: [],
  CANCELLED: []
};

// Libellé du bouton qui provoque chaque changement de statut d'une procédure. L'état « audience programmée » n'a pas de
// bouton : il vient de la planification d'une audience.
export const CASE_ACTION_LABELS = {
  IN_PROGRESS: 'Passer en cours',
  DELIBERATION: 'Mettre en délibéré',
  JUDGMENT_ISSUED: 'Jugement rendu',
  CLOSED: 'Clôturer'
};

export const HEARING_ACTION_LABELS = {
  COMPLETED: 'Tenue',
  POSTPONED: 'Reporter',
  SCHEDULED: 'Replanifier',
  CANCELLED: 'Annuler'
};

export const getProcedureLabel = (value) => PROCEDURE_TYPES.find((item) => item.value === value)?.label || value || '—';
export const getHearingTypeLabel = (value) => HEARING_TYPES.find((item) => item.value === value)?.label || value || '—';
export const getCaseStatusDisplay = (status) => CASE_STATUS[status] || { label: status || '—', tone: 'slate' };
export const getHearingStatusDisplay = (status) => HEARING_STATUS[status] || { label: status || '—', tone: 'slate' };

// Libellés des événements enregistrés par le serveur dans l'historique d'une procédure.
const EVENT_LABELS = {
  JUDICIAL_CASE_REGISTERED: 'Procédure enregistrée',
  JUDICIAL_STATUS_CHANGED: 'Statut de la procédure modifié',
  HEARING_CREATED: 'Audience planifiée',
  HEARING_STATUS_CHANGED: "Statut d'une audience modifié"
};
export const getEventLabel = (type) => EVENT_LABELS[type] || type || 'Événement';
