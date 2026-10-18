import { useMemo } from 'react';
import PageHeader from '../../components/common/PageHeader';
import StatCard from '../../components/common/StatCard';
import SectionCard from '../../components/common/SectionCard';
import StatusBadge from '../../components/common/StatusBadge';
import AsyncState from '../../components/common/AsyncState';
import { secondaryButton } from '../../components/common/buttonStyles';
import useDashboardSources from '../../hooks/useDashboardSources';
import { api } from '../../services/api';
import { startOfDay } from '../../utils/dashboardMetrics';
import {
  agendaItems,
  appointmentsTodayMetric,
  draftInvoicesText,
  modifiedCasesMetric,
  modifiedCasesText,
  newClientsMetric,
  newClientsText,
  pendingPaymentsMetric,
  readMetric,
  recentDocumentsMetric
} from '../../utils/officeDashboardMetrics';

const DAY_MS = 24 * 60 * 60 * 1000;

// Défini hors du composant : le hook ne doit pas relancer les chargements à chaque rendu.
const loaders = {
  appointments: () => api.get('/api/appointments'),
  clients: () => api.get('/api/clients'),
  cases: () => api.get('/api/cases'),
  invoices: () => api.get('/api/invoices'),
  documents: () => {
    const now = new Date();
    const weekStart = encodeURIComponent(new Date(now.getTime() - 7 * DAY_MS).toISOString());
    const todayStart = encodeURIComponent(startOfDay(now).toISOString());
    return api.get(`/api/documents/stats?weekStart=${weekStart}&todayStart=${todayStart}`);
  }
};

export default function SecretaryDashboardPage() {
  const { loading, sources, refetch } = useDashboardSources(loaders);
  // Recalculé à chaque chargement : « aujourd'hui » et « cette semaine » restent justes.
  const now = useMemo(() => new Date(), [sources]); // eslint-disable-line react-hooks/exhaustive-deps

  const metric = (name, compute) => readMetric(loading, sources, name, compute);
  const text = (name, compute) => {
    if (loading) return 'Chargement…';
    return sources?.[name]?.ok ? compute(sources[name].value) : 'Information indisponible';
  };

  const stats = [
    { label: 'Rendez-vous du jour', accent: 'blue', ...metric('appointments', (value) => appointmentsTodayMetric(value, now)) },
    { label: 'Nouveaux clients', accent: 'violet', ...metric('clients', (value) => newClientsMetric(value, now)) },
    { label: 'Dossiers modifiés', accent: 'amber', ...metric('cases', (value) => modifiedCasesMetric(value, now)) },
    { label: 'Paiements en attente', accent: 'rose', ...metric('invoices', pendingPaymentsMetric) },
    { label: 'Documents récents', accent: 'emerald', ...metric('documents', recentDocumentsMetric) }
  ];

  const agenda = useMemo(
    () => (sources?.appointments?.ok ? agendaItems(sources.appointments.value, sources.clients?.ok ? sources.clients.value : [], now) : []),
    [sources, now]
  );

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Espace secrétaire"
        title="Suivi administratif"
        description="Vue sur les rendez-vous, dossiers, paiements et documents à coordonner."
        action={
          <button type="button" className={secondaryButton} onClick={refetch}>
            Actualiser
          </button>
        }
      />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        {stats.map((stat) => (
          <StatCard key={stat.label} {...stat} />
        ))}
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.5fr_1fr]">
        <SectionCard title="Agenda du jour">
          <AsyncState
            loading={loading}
            error={!loading && sources && !sources.appointments.ok ? sources.appointments.error : null}
            onRetry={refetch}
            isEmpty={agenda.length === 0}
            emptyMessage="Aucun rendez-vous aujourd'hui."
          >
            <div className="space-y-4">
              {agenda.map((item) => (
                <div key={item.key} className="flex items-center justify-between rounded-xl border border-slate-200 bg-slate-50 p-3">
                  <div>
                    <div className="font-medium text-slate-800">{item.name}</div>
                    <div className="text-sm text-slate-500">{item.type}</div>
                  </div>
                  <StatusBadge tone={item.tone}>{item.status}</StatusBadge>
                </div>
              ))}
            </div>
          </AsyncState>
        </SectionCard>

        <SectionCard title="Nouveaux éléments">
          <ul className="space-y-3 text-sm text-slate-600">
            <li className="rounded-lg bg-slate-50 p-3">{text('clients', (value) => newClientsText(value, now))}</li>
            <li className="rounded-lg bg-slate-50 p-3">{text('cases', (value) => modifiedCasesText(value, now))}</li>
            <li className="rounded-lg bg-slate-50 p-3">{text('invoices', draftInvoicesText)}</li>
          </ul>
        </SectionCard>
      </div>
    </div>
  );
}
