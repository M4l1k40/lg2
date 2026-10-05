import PageHeader from '../../components/common/PageHeader';
import SectionCard from '../../components/common/SectionCard';

export default function ClientProfilePage() {
  return (
    <div>
      <PageHeader
        eyebrow="Mon profil"
        title="Informations client"
        description="Données personnelles et paramètres du compte."
      />

      <SectionCard title="Profil">
        <div className="grid gap-4 md:grid-cols-2">
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-sm text-slate-500">Nom</div>
            <div className="mt-2 text-lg font-semibold text-slate-900">Sophie Martin</div>
          </div>
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-sm text-slate-500">Email</div>
            <div className="mt-2 text-lg font-semibold text-slate-900">sophie.martin@email.fr</div>
          </div>
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-sm text-slate-500">Téléphone</div>
            <div className="mt-2 text-lg font-semibold text-slate-900">+33 6 12 45 98 75</div>
          </div>
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-sm text-slate-500">Langue préférée</div>
            <div className="mt-2 text-lg font-semibold text-slate-900">Français</div>
          </div>
        </div>
      </SectionCard>
    </div>
  );
}
