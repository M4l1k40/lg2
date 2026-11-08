export const APPOINTMENT_TYPES = [
  { value: 'CLIENT_MEETING', label: 'Rendez-vous client' },
  { value: 'COURT_HEARING', label: 'Audience' },
  { value: 'CONSULTATION', label: 'Consultation' },
  { value: 'INTERNAL_MEETING', label: 'Réunion interne' }
];

const STATUS_DISPLAY = {
  SCHEDULED: { label: 'Planifié', tone: 'blue' },
  COMPLETED: { label: 'Terminé', tone: 'emerald' },
  CANCELLED: { label: 'Annulé', tone: 'rose' }
};

export function getAppointmentTypeLabel(type) {
  return APPOINTMENT_TYPES.find((item) => item.value === type)?.label || type || '—';
}

export function getAppointmentStatusDisplay(status) {
  return STATUS_DISPLAY[status] || { label: status || '—', tone: 'slate' };
}
