import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useLanguage } from '../context/LanguageContext';
import { getHomePath } from '../utils/roleHome';

export default function LoginPage() {
  const { t, language, setLanguage, dir } = useLanguage();
  const { login, logout, user, loading } = useAuth();
  const homePath = user ? getHomePath(user.roles || []) : null;

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-100 text-sm font-medium text-slate-600">
        Chargement de votre espace…
      </div>
    );
  }

  if (homePath) {
    return <Navigate to={homePath} replace />;
  }

  return (
    <div dir={dir} className="flex min-h-screen items-center justify-center bg-slate-100 p-6">
      <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <div className="mb-8 text-center">
          <div className="text-3xl font-bold text-slate-900">LegalFlow</div>
          <p className="mt-2 text-sm text-slate-500">{t.auth.loginSubtitle}</p>
        </div>

        <div className="space-y-5">
          <button
            type="button"
            onClick={login}
            className="w-full rounded-xl bg-slate-900 px-4 py-3 text-sm font-semibold text-white transition hover:bg-slate-800"
          >
            {t.auth.continue}
          </button>

          {user && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
              Votre compte est authentifié mais ne possède aucun rôle LegalFlow reconnu.
              <button type="button" onClick={logout} className="mt-2 block font-semibold underline">
                {t.auth.logout}
              </button>
            </div>
          )}

          <button
            type="button"
            onClick={() => setLanguage(language === 'fr' ? 'ar' : 'fr')}
            className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
          >
            {language === 'fr' ? 'AR' : 'FR'}
          </button>
        </div>
      </div>
    </div>
  );
}
