// Le modèle de facture (billing-service) n'a pas de champ « devise » : le symbole affiché est un choix d'affichage.
export const DISPLAY_CURRENCY = '€';

export function formatAmount(value) {
  const formatted = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 0, maximumFractionDigits: 2 }).format(value);
  return `${formatted} ${DISPLAY_CURRENCY}`;
}
