import { useLanguage } from '../context/LanguageContext';

const stats = [
  { key: 'cases', value: '128', trend: '+12%', tone: 'blue' },
  { key: 'clients', value: '84', trend: '+8%', tone: 'violet' },
  { key: 'upcomingDeadlines', value: '19', trend: '3 aujourd’hui', tone: 'amber' },
  { key: 'upcomingAppointments', value: '11', trend: '2 ce matin', tone: 'emerald' },
  { key: 'unreadNotifications', value: '7', trend: '2 urgentes', tone: 'rose' }
];

const tableRows = [
  { label: 'M. Ibrahim Benali', type: 'Contrat de travail', status: 'À suivre', due: '12 mai 2026' },
  { label: 'Cabinet EL MOURAD', type: 'Litige commercial', status: 'Urgent', due: '13 mai 2026' },
  { label: 'Société Azur', type: 'Aide à la décision', status: 'En cours', due: '14 mai 2026' }
];

const statusClassMap = {
  'À suivre': 'bg-slate-100 text-slate-700',
  Urgent: 'bg-rose-100 text-rose-700',
  'En cours': 'bg-emerald-100 text-emerald-700'
};

export default function DashboardPage() {
  const { t, language } = useLanguage();

  return (
    <div className="space-y-6" dir={language === 'ar' ? 'rtl' : 'ltr'}>
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-sm font-medium uppercase tracking-[0.15em] text-slate-500">{t.common.dashboard}</p>
          <h1 className="mt-1 text-3xl font-bold text-slate-900">{t.dashboard.title}</h1>
        </div>
        <div className="rounded-xl border border-dashed border-slate-300 bg-white px-4 py-2 text-sm text-slate-500">
          {t.dashboard.mockData}
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        {stats.map((stat) => (
          <div key={stat.key} className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between">
              <span className="text-sm text-slate-500">{t.dashboard[stat.key]}</span>
              <span
                className={`rounded-full px-2 py-1 text-xs font-medium ${
                  stat.tone === 'blue'
                    ? 'bg-blue-100 text-blue-700'
                    : stat.tone === 'violet'
                      ? 'bg-violet-100 text-violet-700'
                      : stat.tone === 'amber'
                        ? 'bg-amber-100 text-amber-700'
                        : stat.tone === 'emerald'
                          ? 'bg-emerald-100 text-emerald-700'
                          : 'bg-rose-100 text-rose-700'
                }`}
              >
                {stat.trend}
              </span>
            </div>
            <div className="mt-6 text-3xl font-bold text-slate-900">{stat.value}</div>
          </div>
        ))}
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.5fr_1fr]">
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-slate-900">{t.dashboard.summary}</h2>
            <button type="button" className="text-sm font-medium text-slate-600 hover:text-slate-900">
              {t.common.viewAll}
            </button>
          </div>

          <div className="space-y-4">
            {tableRows.map((row) => (
              <div key={row.label} className="flex items-center justify-between border-b border-slate-100 pb-3 last:border-b-0 last:pb-0">
                <div>
                  <div className="font-medium text-slate-800">{row.label}</div>
                  <div className="text-sm text-slate-500">{row.type}</div>
                </div>

                <div className="flex items-center gap-3">
                  <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${statusClassMap[row.status]}`}>
                    {row.status}
                  </span>
                  <span className="text-sm text-slate-500">{row.due}</span>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="space-y-6">
          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <h3 className="text-lg font-semibold text-slate-900">Activité récente</h3>
            <ul className="mt-4 space-y-3 text-sm text-slate-600">
              <li className="rounded-lg bg-slate-50 p-3">Nouveau client ajouté : Société Aster</li>
              <li className="rounded-lg bg-slate-50 p-3">Rendez-vous validé avec M. Laurent</li>
              <li className="rounded-lg bg-slate-50 p-3">Échéance rapprochée pour le dossier 2047</li>
            </ul>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <h3 className="text-lg font-semibold text-slate-900">Statut système</h3>
            <div className="mt-4 space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-sm text-slate-600">Gateway</span>
                <span className="rounded-full bg-emerald-100 px-2 py-1 text-xs font-medium text-emerald-700">OK</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-slate-600">Keycloak</span>
                <span className="rounded-full bg-emerald-100 px-2 py-1 text-xs font-medium text-emerald-700">OK</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-slate-600">API</span>
                <span className="rounded-full bg-amber-100 px-2 py-1 text-xs font-medium text-amber-700">Mock</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
