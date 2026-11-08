const STATUS_DISPLAY = {
  DRAFT: { label: 'Brouillon', tone: 'slate' },
  SENT: { label: 'Envoyée', tone: 'blue' },
  OVERDUE: { label: 'En retard', tone: 'rose' },
  PAID: { label: 'Payée', tone: 'emerald' },
  CANCELLED: { label: 'Annulée', tone: 'slate' }
};

export function getInvoiceStatusDisplay(status) {
  return STATUS_DISPLAY[status] || { label: status || '—', tone: 'slate' };
}
