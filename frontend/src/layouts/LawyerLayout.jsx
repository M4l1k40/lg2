import { Outlet } from 'react-router-dom';
import SidebarNav from '../components/common/SidebarNav';
import { useLanguage } from '../context/LanguageContext';

const lawyerNav = [
  { to: '/lawyer/dashboard', labelKey: 'lawyer.nav.dashboard', label: 'Dashboard' },
  { to: '/lawyer/clients', labelKey: 'lawyer.nav.clients', label: 'Clients' },
  { to: '/lawyer/cases', labelKey: 'lawyer.nav.cases', label: 'Dossiers' },
  { to: '/lawyer/judicial', labelKey: 'lawyer.nav.judicial', label: 'Judiciaire' },
  { to: '/lawyer/deadlines', labelKey: 'lawyer.nav.deadlines', label: 'Échéances' },
  { to: '/lawyer/appointments', labelKey: 'lawyer.nav.appointments', label: 'Rendez-vous' },
  { to: '/lawyer/documents', labelKey: 'lawyer.nav.documents', label: 'Documents' },
  { to: '/lawyer/consultations', labelKey: 'lawyer.nav.consultations', label: 'Consultations' },
  { to: '/lawyer/notifications', labelKey: 'lawyer.nav.notifications', label: 'Notifications' }
];

export default function LawyerLayout() {
  const { t, language, setLanguage, dir } = useLanguage();

  return (
    <div dir={dir} className="min-h-screen bg-slate-100 text-slate-900">
      <div className="flex min-h-screen">
        <SidebarNav
          title="LegalFlow"
          subtitle="Avocat"
          items={lawyerNav}
          footerLabel="Espace avocat"
        />

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="border-b border-slate-200 bg-white/90 backdrop-blur-sm">
            <div className="flex items-center justify-between gap-3 px-4 py-3 sm:px-6">
              <div>
                <div className="text-lg font-semibold text-slate-900">{t.appName}</div>
                <div className="text-xs text-slate-500">Cabinet</div>
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
