import { Outlet } from 'react-router-dom';
import SidebarNav from '../components/common/SidebarNav';
import { useLanguage } from '../context/LanguageContext';

const adminNav = [
  { to: '/admin/dashboard', labelKey: 'admin.nav.dashboard', label: 'Dashboard' },
  { to: '/admin/users', labelKey: 'admin.nav.users', label: 'Utilisateurs' },
  { to: '/admin/lawyers', labelKey: 'admin.nav.lawyers', label: 'Avocats' },
  { to: '/admin/secretaries', labelKey: 'admin.nav.secretaries', label: 'Secrétaires' },
  { to: '/admin/clients', labelKey: 'admin.nav.clients', label: 'Clients' },
  { to: '/admin/cases', labelKey: 'admin.nav.cases', label: 'Dossiers' },
  { to: '/admin/billing', labelKey: 'admin.nav.billing', label: 'Facturation' },
  { to: '/admin/settings', labelKey: 'admin.nav.settings', label: 'Paramètres' }
];

export default function AdminLayout() {
  const { t, language, setLanguage, dir } = useLanguage();

  return (
    <div dir={dir} className="min-h-screen bg-slate-100 text-slate-900">
      <div className="flex min-h-screen">
        <SidebarNav
          title="LegalFlow"
          subtitle="Admin"
          items={adminNav}
          footerLabel="Espace admin"
        />

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="border-b border-slate-200 bg-white/90 backdrop-blur-sm">
            <div className="flex items-center justify-between gap-3 px-4 py-3 sm:px-6">
              <div>
                <div className="text-lg font-semibold text-slate-900">{t.appName}</div>
                <div className="text-xs text-slate-500">Administration</div>
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

          <main className="flex-1 p-4 sm:p-6">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  );
}
