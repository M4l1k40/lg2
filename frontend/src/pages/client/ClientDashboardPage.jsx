import PageHeader from '../../components/common/PageHeader';
import StatCard from '../../components/common/StatCard';
import StatusBadge from '../../components/common/StatusBadge';
import SectionCard from '../../components/common/SectionCard';

const stats = [
  { label: 'Dossiers actifs', value: '12', trend: '+3 ce mois', accent: 'blue' },
  { label: 'Documents', value: '48', trend: '4 récents', accent: 'violet' },
  { label: 'Prochaine audience', value: '18 mai', trend: 'Dans 4 jours', accent: 'amber' },
  { label: 'Paiement', value: '€1 240', trend: 'En attente', accent: 'rose' },
  { label: 'Notifications', value: '6', trend: '2 non lues', accent: 'emerald' }
];

const cases = [
  { name: 'Litige commercial', status: 'En cours', tone: 'emerald' },
  { name: 'Contrat de travail', status: 'À suivre', tone: 'amber' },
  { name: 'Procédure de divorce', status: 'Urgent', tone: 'rose' }
];

export default function ClientDashboardPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Espace client"
        title="Ma situation juridique"
        description="Vue synthétique de votre activité, vos dossiers et vos prochaines échéances."
      />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        {stats.map((stat) => (
          <StatCard key={stat.label} {...stat} />
        ))}
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.5fr_1fr]">
        <SectionCard title="Mes dossiers en cours">
          <div className="space-y-4">
            {cases.map((item) => (
              <div key={item.name} className="flex items-center justify-between rounded-xl border border-slate-200 bg-slate-50 p-3">
                <div>
                  <div className="font-medium text-slate-800">{item.name}</div>
                  <div className="text-sm text-slate-500">Dossier principal</div>
                </div>
                <StatusBadge tone={item.tone}>{item.status}</StatusBadge>
              </div>
            ))}
          </div>
        </SectionCard>

        <SectionCard title="À retenir">
          <ul className="space-y-3 text-sm text-slate-600">
            <li className="rounded-lg bg-slate-50 p-3">Prochain rendez-vous : 18 mai à 14h30</li>
            <li className="rounded-lg bg-slate-50 p-3">Dernier document envoyé : décision finale</li>
            <li className="rounded-lg bg-slate-50 p-3">Paiement en attente : facture n°2408</li>
          </ul>
        </SectionCard>
      </div>
    </div>
  );
}
