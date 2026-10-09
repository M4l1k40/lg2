import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import StatCard from '../../components/common/StatCard';
import SectionCard from '../../components/common/SectionCard';
import StatusBadge from '../../components/common/StatusBadge';
import AsyncState from '../../components/common/AsyncState';
import useApi from '../../hooks/useApi';
import { getStatusDisplay, isActiveCase, sortByCreatedAtDesc } from '../../constants/caseStatus';

const actionClass =
  'rounded-xl border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50';

export default function LawyerDashboardPage() {
  const casesApi = useApi('/api/cases');
  const clientsApi = useApi('/api/clients');

  const loading = casesApi.loading || clientsApi.loading;
  const error = casesApi.error || clientsApi.error;

  const cases = useMemo(() => sortByCreatedAtDesc(Array.isArray(casesApi.data) ? casesApi.data : []), [casesApi.data]);
  const clients = useMemo(() => (Array.isArray(clientsApi.data) ? clientsApi.data : []), [clientsApi.data]);

  const clientNames = useMemo(() => {
    const names = {};
    clients.forEach((client) => {
      names[client.id] = `${client.firstName} ${client.lastName}`;
    });
    return names;
  }, [clients]);

  const activeCases = cases.filter(isActiveCase);
  const waitingCases = cases.filter((item) => item.status === 'WAITING_CLIENT');
  const courtCases = cases.filter((item) => item.status === 'COURT_PROCEEDING');

  const value = (count) => (loading || error ? '—' : String(count));

  const stats = [
    { label: 'Clients', value: value(clients.length), trend: 'Total', accent: 'blue' },
    { label: 'Dossiers actifs', value: value(activeCases.length), trend: `${cases.length} au total`, accent: 'violet' },
    { label: 'En attente du client', value: value(waitingCases.length), trend: 'À relancer', accent: 'amber' },
    { label: 'Procédure judiciaire', value: value(courtCases.length), trend: 'En cours', accent: 'emerald' }
  ];

  function refetchAll() {
    casesApi.refetch();
    clientsApi.refetch();
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Espace avocat"
        title="Activité du cabinet"
        description="Vue d’ensemble de vos clients et de vos dossiers."
        action={
          <>
            <Link to="/lawyer/clients" className={actionClass}>
              Nouveau client
            </Link>
            <Link to="/lawyer/cases" className={actionClass}>
              Nouveau dossier
            </Link>
          </>
        }
      />

      {error ? (
        <AsyncState error={error} onRetry={refetchAll} />
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {stats.map((stat) => (
              <StatCard key={stat.label} {...stat} />
            ))}
          </div>

          <div className="grid gap-6 xl:grid-cols-[1.4fr_1fr]">
            <SectionCard title="Dossiers récents">
              <AsyncState
                loading={loading}
                isEmpty={cases.length === 0}
                emptyMessage="Aucun dossier pour le moment. Créez-en un depuis la page Dossiers."
              >
                <div className="space-y-4">
                  {cases.slice(0, 5).map((item) => {
                    const status = getStatusDisplay(item.status);

                    return (
                      <div
                        key={item.id}
                        className="flex items-center justify-between gap-3 rounded-xl border border-slate-200 bg-slate-50 p-3"
                      >
                        <div className="min-w-0">
                          <div className="truncate font-medium text-slate-800">{item.title}</div>
                          <div className="truncate text-sm text-slate-500">
                            Réf. {item.reference} · {clientNames[item.clientId] || 'Client inconnu'}
                          </div>
                        </div>
                        <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
                      </div>
                    );
                  })}
                </div>
              </AsyncState>
            </SectionCard>

            <SectionCard title="Points de vigilance">
              <AsyncState
                loading={loading}
                isEmpty={waitingCases.length === 0}
                emptyMessage="Aucun dossier en attente du client."
              >
                <ul className="space-y-3 text-sm text-slate-600">
                  {waitingCases.slice(0, 5).map((item) => (
                    <li key={item.id} className="rounded-lg bg-slate-50 p-3">
                      <span className="font-medium text-slate-800">{item.title}</span> —{' '}
                      {clientNames[item.clientId] || 'client inconnu'} doit répondre
                    </li>
                  ))}
                </ul>
              </AsyncState>
            </SectionCard>
          </div>
        </>
      )}
    </div>
  );
}
