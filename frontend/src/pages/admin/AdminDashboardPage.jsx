import { useMemo } from 'react';
import PageHeader from '../../components/common/PageHeader';
import StatCard from '../../components/common/StatCard';
import SectionCard from '../../components/common/SectionCard';
import StatusBadge from '../../components/common/StatusBadge';
import AsyncState from '../../components/common/AsyncState';
import { secondaryButton } from '../../components/common/buttonStyles';
import useDashboardSources from '../../hooks/useDashboardSources';
import { api } from '../../services/api';
import { formatAmount } from '../../constants/currency';
import { casesMetric } from '../../utils/dashboardMetrics';
import {
  activeUsersMetric,
  billingMetric,
  consultationsToAssignText,
  disabledAccountsText,
  overdueInvoicesText,
  readMetric,
  recentActivity,
  roleUsersMetric
} from '../../utils/officeDashboardMetrics';

// Défini hors du composant : le hook ne doit pas relancer les chargements à chaque rendu.
const loaders = {
  users: () => api.get('/api/users'),
  cases: () => api.get('/api/cases'),
  invoices: () => api.get('/api/invoices'),
  consultations: () => api.get('/api/consultations')
};

export default function AdminDashboardPage() {
  const { loading, sources, refetch } = useDashboardSources(loaders);
  // Recalculé à chaque chargement : « ce mois » reste juste.
  const now = useMemo(() => new Date(), [sources]); // eslint-disable-line react-hooks/exhaustive-deps

  const metric = (name, compute) => readMetric(loading, sources, name, compute);
  const text = (name, compute) => {
    if (loading) return 'Chargement…';
    return sources?.[name]?.ok ? compute(sources[name].value) : 'Information indisponible';
  };
  const valueOf = (name) => (sources?.[name]?.ok ? sources[name].value : undefined);

  const stats = [
    { label: 'Utilisateurs actifs', accent: 'blue', ...metric('users', activeUsersMetric) },
    { label: 'Avocats', accent: 'violet', ...metric('users', (value) => roleUsersMetric(value, 'LAWYER')) },
    { label: 'Secrétaires', accent: 'amber', ...metric('users', (value) => roleUsersMetric(value, 'SECRETARY')) },
    { label: 'Dossiers actifs', accent: 'emerald', ...metric('cases', (value) => casesMetric(value, now)) },
    { label: 'Facturation', accent: 'rose', ...metric('invoices', (value) => billingMetric(value, now, formatAmount)) }
  ];

  const activity = useMemo(
    () => recentActivity({ cases: valueOf('cases'), consultations: valueOf('consultations'), invoices: valueOf('invoices') }),
    [sources] // eslint-disable-line react-hooks/exhaustive-deps
  );
  const allSourcesFailed = !loading && sources && ['cases', 'consultations', 'invoices'].every((name) => !sources[name].ok);

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Espace admin"
        title="Administration du cabinet"
        description="Suivi des comptes, de l’activité, des rôles et des opérations de gestion."
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

      <div className="grid gap-6 xl:grid-cols-[1.4fr_1fr]">
        <SectionCard title="Activité récente">
          <AsyncState
            loading={loading}
            error={allSourcesFailed ? sources.cases.error : null}
            onRetry={refetch}
            isEmpty={activity.length === 0}
            emptyMessage="Aucune activité récente."
          >
            <div className="space-y-4">
              {activity.map((item) => (
                <div key={item.key} className="flex items-center justify-between rounded-xl border border-slate-200 bg-slate-50 p-3">
                  <div className="min-w-0">
                    <div className="truncate font-medium text-slate-800">{item.name}</div>
                    <div className="text-sm text-slate-500">{item.type}</div>
                  </div>
                  <StatusBadge tone={item.tone}>{item.status}</StatusBadge>
                </div>
              ))}
            </div>
          </AsyncState>
        </SectionCard>

        <SectionCard title="Actions prioritaires">
          <ul className="space-y-3 text-sm text-slate-600">
            <li className="rounded-lg bg-slate-50 p-3">{text('consultations', consultationsToAssignText)}</li>
            <li className="rounded-lg bg-slate-50 p-3">{text('invoices', overdueInvoicesText)}</li>
            <li className="rounded-lg bg-slate-50 p-3">{text('users', disabledAccountsText)}</li>
          </ul>
        </SectionCard>
      </div>
    </div>
  );
}
