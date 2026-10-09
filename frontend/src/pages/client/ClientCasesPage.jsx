import { useMemo } from 'react';
import PageHeader from '../../components/common/PageHeader';
import SectionCard from '../../components/common/SectionCard';
import StatusBadge from '../../components/common/StatusBadge';
import useApi from '../../hooks/useApi';

// Correspond à l'enum CaseStatus du case-service
const STATUS_DISPLAY = {
  OPEN: { label: 'Ouvert', tone: 'blue' },
  IN_PROGRESS: { label: 'En cours', tone: 'emerald' },
  WAITING_CLIENT: { label: 'En attente du client', tone: 'amber' },
  COURT_PROCEEDING: { label: 'Procédure judiciaire', tone: 'violet' },
  CLOSED: { label: 'Clôturé', tone: 'slate' },
  ARCHIVED: { label: 'Archivé', tone: 'slate' }
};

function formatDate(value) {
  if (!value) {
    return '—';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }

  return date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
}

export default function ClientCasesPage() {
  const { data, loading, error, refetch } = useApi('/api/cases');

  const cases = useMemo(
    () => [...(Array.isArray(data) ? data : [])].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)),
    [data]
  );

  return (
    <div>
      <PageHeader
        eyebrow="Mes dossiers"
        title="Suivi de vos dossiers"
        description="Consultez l’état de vos affaires en cours et leurs prochaines étapes."
      />

      <SectionCard title="Dossiers">
        {loading && <p className="text-sm text-slate-500">Chargement de vos dossiers…</p>}

        {!loading && error && (
          <div className="rounded-xl border border-rose-200 bg-rose-50 p-4">
            <p className="text-sm font-medium text-rose-700">Impossible de charger les dossiers.</p>
            <p className="mt-1 text-sm text-rose-600">
              {error.status ? `Erreur ${error.status} : ` : ''}
              {error.message}
            </p>
            <button
              type="button"
              onClick={refetch}
              className="mt-3 rounded-lg border border-rose-200 bg-white px-3 py-1.5 text-sm font-medium text-rose-700 transition hover:bg-rose-100"
            >
              Réessayer
            </button>
          </div>
        )}

        {!loading && !error && cases.length === 0 && (
          <p className="text-sm text-slate-500">Aucun dossier pour le moment.</p>
        )}

        {!loading && !error && cases.length > 0 && (
          <div className="space-y-4">
            {cases.map((item) => {
              const status = STATUS_DISPLAY[item.status] || { label: item.status || 'Inconnu', tone: 'slate' };

              return (
                <div
                  key={item.id}
                  className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div className="min-w-0">
                    <div className="font-medium text-slate-800">{item.title}</div>
                    <div className="text-sm text-slate-500">
                      Réf. {item.reference} · Ouvert le {formatDate(item.createdAt)}
                    </div>
                    {item.description && (
                      <div className="mt-1 line-clamp-2 text-sm text-slate-600">{item.description}</div>
                    )}
                  </div>
                  <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
                </div>
              );
            })}
          </div>
        )}
      </SectionCard>
    </div>
  );
}
