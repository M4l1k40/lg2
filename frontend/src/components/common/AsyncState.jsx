/**
 * Affiche l'état d'un chargement : chargement, erreur (avec bouton Réessayer),
 * liste vide, ou le contenu (children) quand tout est prêt.
 */
export default function AsyncState({
  loading,
  error,
  onRetry,
  isEmpty = false,
  emptyMessage = 'Aucune donnée pour le moment.',
  loadingMessage = 'Chargement…',
  children
}) {
  if (loading) {
    return <p className="text-sm text-slate-500">{loadingMessage}</p>;
  }

  if (error) {
    return (
      <div className="rounded-xl border border-rose-200 bg-rose-50 p-4">
        <p className="text-sm font-medium text-rose-700">Impossible de charger les données.</p>
        <p className="mt-1 text-sm text-rose-600">
          {error.status ? `Erreur ${error.status} : ` : ''}
          {error.message}
        </p>
        {onRetry ? (
          <button
            type="button"
            onClick={onRetry}
            className="mt-3 rounded-lg border border-rose-200 bg-white px-3 py-1.5 text-sm font-medium text-rose-700 transition hover:bg-rose-100"
          >
            Réessayer
          </button>
        ) : null}
      </div>
    );
  }

  if (isEmpty) {
    return <p className="text-sm text-slate-500">{emptyMessage}</p>;
  }

  return children;
}
