import { useMemo } from 'react';
import * as useApiModule from '../../hooks/useApi';

// Fonctionne que useApi soit exporté en "default" ou en "named".
// Si tu préfères, remplace ces deux lignes par le même import que dans LawyerDashboardPage.jsx.
const useApi = useApiModule.useApi ?? useApiModule.default;

/* ----------------------------- Helpers ----------------------------- */
const toList = (data) => {
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.content)) return data.content;
  if (Array.isArray(data?.items)) return data.items;
  return [];
};

const normalizeRoles = (user) => {
  const raw = [...(Array.isArray(user.roles) ? user.roles : []), user.role].filter(Boolean);
  return raw.map((r) => String(typeof r === 'object' ? r.name || r.role : r).replace(/^ROLE_/, '').toLowerCase());
};

const numberFormat = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });

const caseLabel = (item) =>
  item.title || item.name || item.reference || item.caseNumber || `Dossier ${String(item.id ?? '').slice(0, 8)}`;

const statusTone = (status = '') => {
  const s = String(status).toUpperCase();
  if (['OPEN', 'ACTIVE', 'IN_PROGRESS', 'PAID'].includes(s)) return 'emerald';
  if (['PENDING', 'DRAFT', 'ON_HOLD'].includes(s)) return 'amber';
  if (['OVERDUE', 'URGENT'].includes(s)) return 'rose';
  if (['CLOSED', 'ARCHIVED', 'DONE', 'COMPLETED'].includes(s)) return 'blue';
  return 'slate';
};

const errorText = (error) => String(error?.message || error?.status || error);

/* ------------------------ Petits composants ------------------------ */
const ACCENTS = {
  blue: 'bg-blue-100 text-blue-700',
  violet: 'bg-violet-100 text-violet-700',
  amber: 'bg-amber-100 text-amber-700',
  emerald: 'bg-emerald-100 text-emerald-700',
  rose: 'bg-rose-100 text-rose-700',
  slate: 'bg-slate-100 text-slate-700'
};

function StatCard({ label, value, trend, accent = 'blue' }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <span className="text-sm text-slate-500">{label}</span>
        {trend ? (
          <span className={`rounded-full px-2 py-1 text-xs font-medium ${ACCENTS[accent] || ACCENTS.blue}`}>
            {trend}
          </span>
        ) : null}
      </div>
      <div className="mt-6 text-3xl font-bold text-slate-900">{value}</div>
    </div>
  );
}

function Badge({ tone = 'slate', children }) {
  return (
    <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${ACCENTS[tone] || ACCENTS.slate}`}>
      {children}
    </span>
  );
}

function Card({ title, children }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
      </div>
      {children}
    </div>
  );
}

/* ------------------------------ Page ------------------------------- */
export default function AdminDashboardPage() {
  const usersApi = useApi('/api/users');
  const clientsApi = useApi('/api/clients');
  const casesApi = useApi('/api/cases');
  // À vérifier : chemins de ces deux routes dans ta gateway
  const invoicesApi = useApi('/api/invoices');
  const deadlinesApi = useApi('/api/deadlines');

  const sources = [
    { label: 'utilisateurs', api: usersApi },
    { label: 'clients', api: clientsApi },
    { label: 'dossiers', api: casesApi },
    { label: 'factures', api: invoicesApi },
    { label: 'échéances', api: deadlinesApi }
  ];

  const loading = sources.some((s) => s.api.loading);
  const failed = sources.filter((s) => s.api.error);

  const summary = useMemo(() => {
    const users = toList(usersApi.data);
    const cases = toList(casesApi.data);
    const invoices = toList(invoicesApi.data);
    const deadlines = toList(deadlinesApi.data);

    const withRoles = users.map((u) => ({ ...u, _roles: normalizeRoles(u) }));
    const disabledUsers = users.filter((u) => u.enabled === false).length;

    const totalBilled = invoices.reduce((sum, i) => sum + (Number(i.totalAmount) || 0), 0);

    return {
      activeUsers: users.length - disabledUsers,
      disabledUsers,
      lawyers: withRoles.filter((u) => u._roles.includes('lawyer')).length,
      secretaries: withRoles.filter((u) => u._roles.includes('secretary')).length,
      clientsCount: toList(clientsApi.data).length,
      casesCount: cases.length,
      invoicesCount: invoices.length,
      totalBilled,
      currency: invoices.find((i) => i.currency)?.currency || '€',
      overdueInvoices: invoices.filter((i) => String(i.status).toUpperCase() === 'OVERDUE').length,
      overdueDeadlines: deadlines.filter((d) => String(d.status).toUpperCase() === 'OVERDUE').length,
      recentCases: [...cases]
        .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
        .slice(0, 5)
    };
  }, [usersApi.data, clientsApi.data, casesApi.data, invoicesApi.data, deadlinesApi.data]);

  // Affiche "…" pendant le chargement, "—" si la source a échoué, sinon la valeur
  const show = (api, value) => (api.loading ? '…' : api.error ? '—' : numberFormat.format(value));

  const priorities = [];
  if (summary.disabledUsers > 0) priorities.push(`Vérifier ${summary.disabledUsers} compte(s) désactivé(s)`);
  if (summary.overdueInvoices > 0) priorities.push(`Relancer ${summary.overdueInvoices} facture(s) en retard`);
  if (summary.overdueDeadlines > 0) priorities.push(`Traiter ${summary.overdueDeadlines} échéance(s) en retard`);

  return (
    <div className="space-y-6">
      <div>
        <p className="text-sm font-medium uppercase tracking-[0.15em] text-slate-500">Espace admin</p>
        <h1 className="mt-1 text-3xl font-bold text-slate-900">Administration du cabinet</h1>
        <p className="mt-2 text-sm text-slate-600">
          Suivi des comptes, de l’activité, des rôles et des opérations de gestion.
        </p>
      </div>

      {failed.length > 0 && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
          Données indisponibles : {failed.map((s) => `${s.label} (${errorText(s.api.error)})`).join(', ')}.
          Vérifie le chemin de la route dans la gateway et les droits du rôle admin.
        </div>
      )}

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        <StatCard
          label="Utilisateurs actifs"
          value={show(usersApi, summary.activeUsers)}
          trend={summary.disabledUsers > 0 ? `${summary.disabledUsers} désactivé(s)` : null}
          accent="blue"
        />
        <StatCard label="Avocats" value={show(usersApi, summary.lawyers)} accent="violet" />
        <StatCard label="Secrétaires" value={show(usersApi, summary.secretaries)} accent="amber" />
        <StatCard
          label="Dossiers"
          value={show(casesApi, summary.casesCount)}
          trend={clientsApi.error || clientsApi.loading ? null : `${summary.clientsCount} clients`}
          accent="emerald"
        />
        <StatCard
          label="Facturation"
          value={
            invoicesApi.loading
              ? '…'
              : invoicesApi.error
                ? '—'
                : `${numberFormat.format(summary.totalBilled)} ${summary.currency}`
          }
          trend={`${summary.invoicesCount} facture(s)`}
          accent="rose"
        />
      </div>

      <div className="grid gap-6 xl:grid-cols-[1.4fr_1fr]">
        <Card title="Dossiers récents">
          {casesApi.loading ? (
            <p className="text-sm text-slate-500">Chargement…</p>
          ) : summary.recentCases.length === 0 ? (
            <p className="text-sm text-slate-500">Aucun dossier pour le moment.</p>
          ) : (
            <div className="space-y-4">
              {summary.recentCases.map((item) => (
                <div
                  key={item.id}
                  className="flex items-center justify-between rounded-xl border border-slate-200 bg-slate-50 p-3"
                >
                  <div>
                    <div className="font-medium text-slate-800">{caseLabel(item)}</div>
                    <div className="text-sm text-slate-500">
                      {item.createdAt ? new Date(item.createdAt).toLocaleDateString('fr-FR') : '—'}
                    </div>
                  </div>
                  {item.status ? <Badge tone={statusTone(item.status)}>{item.status}</Badge> : null}
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card title="Actions prioritaires">
          {loading ? (
            <p className="text-sm text-slate-500">Chargement…</p>
          ) : priorities.length === 0 ? (
            <p className="text-sm text-slate-500">Rien d’urgent pour le moment.</p>
          ) : (
            <ul className="space-y-3 text-sm text-slate-600">
              {priorities.map((text) => (
                <li key={text} className="rounded-lg bg-slate-50 p-3">
                  {text}
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  );
}