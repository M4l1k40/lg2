import PageHeader from '../../components/common/PageHeader';
import SectionCard from '../../components/common/SectionCard';
import StatusBadge from '../../components/common/StatusBadge';

const cases = [
  { name: 'Litige commercial', type: 'Dossier principal', status: 'En cours', tone: 'emerald' },
  { name: 'Contrat de travail', type: 'Revue contractuelle', status: 'À suivre', tone: 'amber' },
  { name: 'Procédure de divorce', type: 'Affaire familiale', status: 'Urgent', tone: 'rose' }
];

export default function ClientCasesPage() {
  return (
    <div>
      <PageHeader
        eyebrow="Mes dossiers"
        title="Suivi de vos dossiers"
        description="Consultez l’état de vos affaires en cours et leurs prochaines étapes."
      />

      <SectionCard title="Dossiers">
        <div className="space-y-4">
          {cases.map((item) => (
            <div key={item.name} className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <div className="font-medium text-slate-800">{item.name}</div>
                <div className="text-sm text-slate-500">{item.type}</div>
              </div>
              <StatusBadge tone={item.tone}>{item.status}</StatusBadge>
            </div>
          ))}
        </div>
      </SectionCard>
    </div>
  );
}
