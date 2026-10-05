import PageHeader from '../../components/common/PageHeader';
import StatCard from '../../components/common/StatCard';
import SectionCard from '../../components/common/SectionCard';
import StatusBadge from '../../components/common/StatusBadge';

const stats = [
  { label: 'Rendez-vous du jour', value: '11', trend: '3 internes', accent: 'blue' },
  { label: 'Nouveaux clients', value: '7', trend: '+2', accent: 'violet' },
  { label: 'Dossiers modifiés', value: '23', trend: 'Aujourd’hui', accent: 'amber' },
  { label: 'Paiements en attente', value: '15', trend: '5 urgents', accent: 'rose' },
  { label: 'Documents récents', value: '42', trend: '6 ajoutés', accent: 'emerald' }
];

const agenda = [
  { name: 'Réunion interne', type: '09:30', status: 'Confirmé', tone: 'emerald' },
  { name: 'RDV client M. Martin', type: '11:00', status: 'À venir', tone: 'amber' },
  { name: 'Validation facture', type: '15:15', status: 'À traiter', tone: 'rose' }
];

export default function SecretaryDashboardPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Espace secrétaire"
        title="Suivi administratif"
        description="Vue sur les rendez-vous, dossiers, paiements et documents à coordonner."
      />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        {stats.map((stat) => (
          <StatCard key={stat.label} {...stat} />
        ))}
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.5fr_1fr]">
        <SectionCard title="Agenda du jour">
          <div className="space-y-4">
            {agenda.map((item) => (
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

        <SectionCard title="Nouveaux éléments">
          <ul className="space-y-3 text-sm text-slate-600">
            <li className="rounded-lg bg-slate-50 p-3">2 nouveaux clients à onboarding</li>
            <li className="rounded-lg bg-slate-50 p-3">4 dossiers modifiés hier</li>
            <li className="rounded-lg bg-slate-50 p-3">3 factures en attente d’envoi</li>
          </ul>
        </SectionCard>
      </div>
    </div>
  );
}
