import PageHeader from '../../components/common/PageHeader';
import StatCard from '../../components/common/StatCard';
import SectionCard from '../../components/common/SectionCard';
import StatusBadge from '../../components/common/StatusBadge';

const stats = [
  { label: 'Nouveaux clients', value: '18', trend: '+6%', accent: 'blue' },
  { label: 'Dossiers actifs', value: '64', trend: '+12%', accent: 'violet' },
  { label: 'Échéances proches', value: '9', trend: '3 urgentes', accent: 'amber' },
  { label: 'Audiences', value: '5', trend: 'Cette semaine', accent: 'emerald' },
  { label: 'Notifications', value: '14', trend: '4 nouvelles', accent: 'rose' }
];

const activity = [
  { name: 'Société Aster', type: 'Nouveau dossier', status: 'À valider', tone: 'amber' },
  { name: 'M. Benali', type: 'Audience programmée', status: 'Confirmée', tone: 'emerald' },
  { name: 'Cabinet El Mourad', type: 'Paiement reçu', status: 'Terminé', tone: 'blue' }
];

export default function LawyerDashboardPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Espace avocat"
        title="Activité du cabinet"
        description="Vue d’ensemble de l’activité juridique, des dossiers et des rendez-vous en cours."
      />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        {stats.map((stat) => (
          <StatCard key={stat.label} {...stat} />
        ))}
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.4fr_1fr]">
        <SectionCard title="Activité récente">
          <div className="space-y-4">
            {activity.map((item) => (
              <div key={item.name} className="flex items-center justify-between rounded-xl border border-slate-200 bg-slate-50 p-3">
                <div>
                  <div className="font-medium text-slate-800">{item.name}</div>
                  <div className="text-sm text-slate-500">{item.type}</div>
                </div>
                <StatusBadge tone={item.tone}>{item.status}</StatusBadge>
              </div>
            ))}
          </div>
        </SectionCard>

        <SectionCard title="Points de vigilance">
          <ul className="space-y-3 text-sm text-slate-600">
            <li className="rounded-lg bg-slate-50 p-3">3 échéances client à relancer</li>
            <li className="rounded-lg bg-slate-50 p-3">1 audience à finaliser</li>
            <li className="rounded-lg bg-slate-50 p-3">2 documents à signer</li>
          </ul>
        </SectionCard>
      </div>
    </div>
  );
}
