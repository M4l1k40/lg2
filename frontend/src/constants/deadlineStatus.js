export const DEADLINE_PRIORITIES = [
  { value: 'LOW', label: 'Basse' },
  { value: 'MEDIUM', label: 'Moyenne' },
  { value: 'HIGH', label: 'Haute' },
  { value: 'URGENT', label: 'Urgente' }
];

const PRIORITY_TONES = { LOW: 'slate', MEDIUM: 'blue', HIGH: 'amber', URGENT: 'rose' };

const STATUS_DISPLAY = {
  PENDING: { label: 'À faire', tone: 'blue' },
  OVERDUE: { label: 'En retard', tone: 'rose' },
  COMPLETED: { label: 'Terminée', tone: 'emerald' },
  CANCELLED: { label: 'Annulée', tone: 'slate' }
};

export function getPriorityDisplay(priority) {
  const label = DEADLINE_PRIORITIES.find((item) => item.value === priority)?.label || priority || '—';
  return { label, tone: PRIORITY_TONES[priority] || 'slate' };
}

export function getDeadlineStatusDisplay(status) {
  return STATUS_DISPLAY[status] || { label: status || '—', tone: 'slate' };
}
