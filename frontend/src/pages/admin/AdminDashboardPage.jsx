import PageHeader from '../../components/common/PageHeader';
import StatCard from '../../components/common/StatCard';
import SectionCard from '../../components/common/SectionCard';
import StatusBadge from '../../components/common/StatusBadge';

const stats = [
  { label: 'Utilisateurs actifs', value: '128', trend: '+8%', accent: 'blue' },
  { label: 'Avocats', value: '14', trend: '4 internes', accent: 'violet' },
  { label: 'Secrétaires', value: '6', trend: '2 nouveaux', accent: 'amber' },
  { label: 'Dossiers actifs', value: '312', trend: '12 ce mois', accent: 'emerald' },
  { label: 'Facturation', value: '€48k', trend: 'Ce mois', accent: 'rose' }
];

const indicators = [
  { name: 'Cabinet A', type: 'Activité élevée', status: 'Stable', tone: 'emerald' },
  { name: 'Cabinet B', type: 'Paiements retardés', status: 'À surveiller', tone: 'amber' },
  { name: 'Cabinet C', type: 'Rôle mis à jour', status: 'Terminé', tone: 'blue' }
];

export default function AdminDashboardPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Espace admin"
        title="Administration du cabinet"
        description="Suivi des comptes, de l’activité, des rôles et des opérations de gestion."
      />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        {stats.map((stat) => (
          <StatCard key={stat.label} {...stat} />
        ))}
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.4fr_1fr]">
        <SectionCard title="Activité récente">
          <div className="space-y-4">
            {indicators.map((item) => (
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

        <SectionCard title="Actions prioritaires">
          <ul className="space-y-3 text-sm text-slate-600">
            <li className="rounded-lg bg-slate-50 p-3">Approuver 4 nouveaux comptes</li>
            <li className="rounded-lg bg-slate-50 p-3">Vérifier les rôles de 3 utilisateurs</li>
            <li className="rounded-lg bg-slate-50 p-3">Finaliser le reporting facture</li>
          </ul>
        </SectionCard>
      </div>
    </div>
  );
}
