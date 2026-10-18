import { useMemo } from 'react';
import PageHeader from '../../components/common/PageHeader';
import StatCard from '../../components/common/StatCard';
import StatusBadge from '../../components/common/StatusBadge';
import SectionCard from '../../components/common/SectionCard';
import AsyncState from '../../components/common/AsyncState';
import { secondaryButton } from '../../components/common/buttonStyles';
import useClientDashboard from '../../hooks/useClientDashboard';
import { getStatusDisplay, isActiveCase, sortByCreatedAtDesc } from '../../constants/caseStatus';
import { formatAmount } from '../../constants/currency';
import {
  casesMetric,
  documentsMetric,
  hearingMetric,
  latestDocumentText,
  nextAppointmentText,
  notificationsMetric,
  paymentMetric,
  pendingInvoiceText
} from '../../utils/dashboardMetrics';

export default function ClientDashboardPage() {
  const { loading, sources, refetch } = useClientDashboard();
  // Recalculé à chaque chargement : « Dans N jours » et « ce mois » restent justes.
  const now = useMemo(() => new Date(), [sources]); // eslint-disable-line react-hooks/exhaustive-deps

  const available = (name) => !loading && sources?.[name]?.ok;
  const metric = (name, compute) => {
    if (loading) return { value: '—', trend: 'Chargement…' };
    return available(name) ? compute(sources[name].value) : { value: '—', trend: 'Indisponible' };
  };
  const text = (name, compute, fallback) => (available(name) ? compute(sources[name].value) : fallback);

  const stats = [
    { label: 'Dossiers actifs', accent: 'blue', ...metric('cases', (value) => casesMetric(value, now)) },
    { label: 'Documents', accent: 'violet', ...metric('documents', (value) => documentsMetric(value, now)) },
    { label: 'Prochaine audience', accent: 'amber', ...metric('hearings', (value) => hearingMetric(value, now)) },
    { label: 'Paiement', accent: 'rose', ...metric('invoices', (value) => paymentMetric(value, formatAmount)) },
    { label: 'Notifications', accent: 'emerald', ...metric('notifications', notificationsMetric) }
  ];

  const activeCases = useMemo(() => {
    if (!sources?.cases?.ok || !Array.isArray(sources.cases.value)) return [];
    return sortByCreatedAtDesc(sources.cases.value.filter(isActiveCase)).slice(0, 5);
  }, [sources]);

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Espace client"
        title="Ma situation juridique"
        description="Vue synthétique de votre activité, vos dossiers et vos prochaines échéances."
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
        <SectionCard title="Mes dossiers en cours">
          <AsyncState
            loading={loading}
            error={!loading && sources && !sources.cases.ok ? sources.cases.error : null}
            onRetry={refetch}
            isEmpty={activeCases.length === 0}
            emptyMessage="Aucun dossier en cours pour le moment."
          >
            <div className="space-y-4">
              {activeCases.map((item) => {
                const status = getStatusDisplay(item.status);

                return (
                  <div key={item.id} className="flex items-center justify-between rounded-xl border border-slate-200 bg-slate-50 p-3">
                    <div className="min-w-0">
                      <div className="truncate font-medium text-slate-800">{item.title}</div>
                      <div className="text-sm text-slate-500">{item.reference}</div>
                    </div>
                    <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
                  </div>
                );
              })}
            </div>
          </AsyncState>
        </SectionCard>

        <SectionCard title="À retenir">
          <ul className="space-y-3 text-sm text-slate-600">
            <li className="rounded-lg bg-slate-50 p-3">
              {loading ? 'Chargement…' : text('appointments', (value) => nextAppointmentText(value, now), 'Rendez-vous : indisponible')}
            </li>
            <li className="rounded-lg bg-slate-50 p-3">
              {loading ? 'Chargement…' : text('documents', latestDocumentText, 'Documents : indisponible')}
            </li>
            <li className="rounded-lg bg-slate-50 p-3">
              {loading ? 'Chargement…' : text('invoices', pendingInvoiceText, 'Paiements : indisponible')}
            </li>
          </ul>
        </SectionCard>
      </div>
    </div>
  );
}
