// Correspond à l'enum ConsultationStatus du consultation-service
export const CONSULTATION_STATUS = {
  PENDING: { label: 'En attente', tone: 'amber' },
  ASSIGNED: { label: 'Assignée', tone: 'blue' },
  IN_PROGRESS: { label: 'En cours', tone: 'cyan' },
  ANSWERED: { label: 'Répondue', tone: 'violet' },
  CLOSED: { label: 'Clôturée', tone: 'emerald' },
  CANCELLED: { label: 'Annulée', tone: 'rose' }
};

export const CONSULTATION_STATUS_OPTIONS = Object.entries(CONSULTATION_STATUS).map(([value, display]) => ({
  value,
  label: display.label
}));

// Statuts dans lesquels une consultation peut encore être annulée
export const CANCELLABLE_STATUSES = ['PENDING', 'ASSIGNED', 'IN_PROGRESS'];

export const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function getConsultationStatus(status) {
  return CONSULTATION_STATUS[status] || { label: status || 'Inconnu', tone: 'slate' };
}

export function formatDateTime(value) {
  if (!value) {
    return '—';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }

  return date.toLocaleString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

export function sortByCreatedAtDesc(items) {
  return [...items].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
}

export function describeConsultationError(error) {
  switch (error.status) {
    case 400:
      return 'Les données saisies sont invalides.';
    case 403:
      return "Action non autorisée pour votre rôle, ou votre compte n'est pas lié à une fiche client.";
    case 404:
      return 'Consultation introuvable (elle a peut-être été prise en charge par un autre avocat).';
    case 409:
      return "Cette action n'est plus possible dans l'état actuel de la consultation. Actualisez la liste.";
    case 422:
      return "Le client indiqué n'existe pas dans votre cabinet.";
    default:
      if (error.status >= 500) {
        return `Erreur serveur (${error.status}). Vérifiez que le consultation-service est démarré (docker ps).`;
      }
      return error.message || "L'action a échoué.";
  }
}
