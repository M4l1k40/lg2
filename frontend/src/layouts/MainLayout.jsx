import { NavLink } from 'react-router-dom';
import { useLanguage } from '../context/LanguageContext';

const navItems = [
  { to: '/dashboard', labelKey: 'nav.dashboard' },
  { to: '/clients', labelKey: 'nav.clients' },
  { to: '/cases', labelKey: 'nav.cases' },
  { to: '/judicial', labelKey: 'nav.judicial' },
  { to: '/deadlines', labelKey: 'nav.deadlines' },
  { to: '/appointments', labelKey: 'nav.appointments' },
  { to: '/documents', labelKey: 'nav.documents' },
  { to: '/notifications', labelKey: 'nav.notifications' },
  { to: '/billing', labelKey: 'nav.billing' },
  { to: '/consultations', labelKey: 'nav.consultations' }
];

function translateLabel(key, t) {
  return key.split('.').reduce((obj, segment) => obj?.[segment], t) || key;
}

export default function MainLayout({ children }) {
  const { t, language, setLanguage, dir } = useLanguage();

  return (
    <div dir={dir} className="min-h-screen bg-slate-100 text-slate-900">
      <div className="flex min-h-screen">
        <aside className="hidden w-72 shrink-0 border-r border-slate-200 bg-slate-950 text-slate-100 lg:flex lg:flex-col">
          <div className="border-b border-slate-800 px-6 py-5">
            <div className="text-xl font-bold tracking-tight">LegalFlow</div>
            <div className="mt-1 text-xs uppercase tracking-[0.2em] text-slate-400">SaaS legal</div>
          </div>

          <nav className="flex-1 space-y-1 p-4">
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `flex items-center justify-between rounded-xl px-3 py-2.5 text-sm font-medium transition ${
                    isActive
                      ? 'bg-slate-800 text-white shadow-sm'
                      : 'text-slate-300 hover:bg-slate-800/70 hover:text-white'
                  }`
                }
              >
                <span>{translateLabel(item.labelKey, t)}</span>
              </NavLink>
            ))}
          </nav>

          <div className="border-t border-slate-800 p-4 text-sm text-slate-400">
            <div className="font-medium text-slate-200">Cabinet juridique</div>
            <div className="mt-1">Centre de gestion</div>
          </div>
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="border-b border-slate-200 bg-white/90 backdrop-blur-sm">
            <div className="flex items-center justify-between gap-3 px-4 py-3 sm:px-6">
              <div>
                <div className="text-lg font-semibold text-slate-900">LegalFlow</div>
                <div className="text-xs text-slate-500">Plateforme juridique</div>
              </div>

              <div className="flex items-center gap-3">
                <div className="hidden rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600 md:block">
                  {t.common.search}
                </div>

                <button
                  type="button"
                  onClick={() => setLanguage(language === 'fr' ? 'ar' : 'fr')}
                  className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
                >
                  {language === 'fr' ? 'AR' : 'FR'}
                </button>
              </div>
            </div>
          </header>

          <main className="flex-1 p-4 sm:p-6">{children}</main>
        </div>
      </div>
    </div>
  );
}
