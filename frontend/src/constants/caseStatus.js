// Correspond à l'enum CaseStatus du case-service
export const STATUS_DISPLAY = {
  OPEN: { label: 'Ouvert', tone: 'blue' },
  IN_PROGRESS: { label: 'En cours', tone: 'emerald' },
  WAITING_CLIENT: { label: 'En attente du client', tone: 'amber' },
  COURT_PROCEEDING: { label: 'Procédure judiciaire', tone: 'violet' },
  CLOSED: { label: 'Clôturé', tone: 'slate' },
  ARCHIVED: { label: 'Archivé', tone: 'slate' }
};

export const STATUS_OPTIONS = Object.entries(STATUS_DISPLAY).map(([value, display]) => ({
  value,
  label: display.label
}));

export function getStatusDisplay(status) {
  return STATUS_DISPLAY[status] || { label: status || 'Inconnu', tone: 'slate' };
}

export function isActiveCase(legalCase) {
  return legalCase.status !== 'CLOSED' && legalCase.status !== 'ARCHIVED';
}

export function formatDate(value) {
  if (!value) {
    return '—';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }

  return date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
}

export function sortByCreatedAtDesc(items) {
  return [...items].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
}
