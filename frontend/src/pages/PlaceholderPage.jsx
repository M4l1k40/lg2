import { useLanguage } from '../context/LanguageContext';

function resolveLabel(key, t) {
  return key.split('.').reduce((value, part) => value?.[part], t) || key;
}

export default function PlaceholderPage({ titleKey }) {
  const { t } = useLanguage();

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8">
      <div className="text-sm font-semibold uppercase tracking-[0.15em] text-slate-500">LegalFlow</div>
      <h1 className="mt-3 text-3xl font-bold text-slate-900">{resolveLabel(titleKey, t)}</h1>
      <p className="mt-3 max-w-xl text-slate-600">
        Cette vue est préparée pour la prochaine étape métier. Le routage et la structure du frontend sont déjà en place.
      </p>

      <div className="mt-6 grid gap-4 md:grid-cols-3">
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-sm text-slate-500">État</div>
          <div className="mt-2 text-lg font-semibold text-slate-900">Prêt</div>
        </div>
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-sm text-slate-500">Source</div>
          <div className="mt-2 text-lg font-semibold text-slate-900">Gateway</div>
        </div>
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-sm text-slate-500">Intégration</div>
          <div className="mt-2 text-lg font-semibold text-slate-900">Keycloak</div>
        </div>
      </div>
    </div>
  );
}
