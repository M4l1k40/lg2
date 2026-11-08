import { Fragment, useMemo, useState } from 'react';
import PageHeader from '../../components/common/PageHeader';
import SectionCard from '../../components/common/SectionCard';
import StatCard from '../../components/common/StatCard';
import AsyncState from '../../components/common/AsyncState';
import StatusBadge from '../../components/common/StatusBadge';
import FormField, { inputClass } from '../../components/common/FormField';
import { dangerButton, primaryButton, secondaryButton } from '../../components/common/buttonStyles';
import { keycloak } from '../../context/AuthContext';
import useApi from '../../hooks/useApi';
import useAction from '../../hooks/useAction';
import { api } from '../../services/api';
import { getInvoiceStatusDisplay } from '../../constants/invoiceStatus';
import {
  buildPayload,
  describeDelay,
  describeInvoiceError,
  emptyForm,
  formatDay,
  formatMoney,
  groupInvoices,
  invoiceActions,
  invoiceTotals,
  isLate,
  suggestInvoiceNumber,
  validateForm
} from '../../utils/invoices';

const TABS = [
  { key: 'open', label: 'À régler', empty: 'Aucune facture en attente de paiement.' },
  { key: 'late', label: 'En retard', empty: 'Aucune facture en retard.' },
  { key: 'paid', label: 'Payées', empty: 'Aucune facture payée.' },
  { key: 'cancelled', label: 'Annulées', empty: 'Aucune facture annulée.' },
  { key: 'draft', label: 'Brouillons', empty: 'Aucun brouillon.' }
];

const asList = (value) => (Array.isArray(value) ? value : []);

export default function LawyerInvoicesPage() {
  const isAdmin = Boolean(keycloak.hasRealmRole?.('ADMIN'));
  // Le cabinet vient exclusivement du jeton, jamais d'une saisie.
  const lawFirmId = keycloak.tokenParsed?.lawFirmId || keycloak.tokenParsed?.lawfirmid;

  const invoicesApi = useApi('/api/invoices');
  const casesApi = useApi('/api/cases');
  const clientsApi = useApi('/api/clients');
  const { busyKey, feedback, run, clearFeedback } = useAction();

  const [tab, setTab] = useState('open');
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm());
  const [errors, setErrors] = useState({});
  const [expandedId, setExpandedId] = useState(null);

  // Recalculé à chaque rechargement : « en retard » et les délais restent justes.
  const now = useMemo(() => new Date(), [invoicesApi.data]); // eslint-disable-line react-hooks/exhaustive-deps
  const groups = useMemo(() => groupInvoices(invoicesApi.data, now), [invoicesApi.data, now]);
  const totals = useMemo(() => invoiceTotals(invoicesApi.data, now), [invoicesApi.data, now]);
  const visible = groups[tab];
  // L'onglet « Brouillons » n'apparaît que s'il y en a : le serveur n'en crée plus, mais d'anciens peuvent exister.
  const tabs = TABS.filter((item) => item.key !== 'draft' || groups.draft.length > 0 || tab === 'draft');

  const cases = asList(casesApi.data);
  const clients = asList(clientsApi.data);
  const casesById = useMemo(() => new Map(cases.map((item) => [item.id, item])), [cases]);
  const clientsById = useMemo(
    () => new Map(clients.map((item) => [item.id, `${item.firstName} ${item.lastName}`.trim()])),
    [clients]
  );
  const selectedCase = casesById.get(form.caseId);

  function openCreate() {
    clearFeedback();
    setEditing(null);
    setForm(emptyForm(suggestInvoiceNumber(invoicesApi.data, new Date())));
    setErrors({});
    setFormOpen(true);
  }

  function openEdit(invoice) {
    clearFeedback();
    setEditing(invoice);
    setForm({
      caseId: invoice.caseId,
      invoiceNumber: invoice.invoiceNumber,
      description: invoice.description,
      amount: String(invoice.amount).replace('.', ','),
      dueDate: invoice.dueDate
    });
    setErrors({});
    setFormOpen(true);
  }

  function closeForm() {
    setFormOpen(false);
    setEditing(null);
    setErrors({});
  }

  function update(field, value) {
    setForm((previous) => ({ ...previous, [field]: value }));
    setErrors((previous) => ({ ...previous, [field]: undefined }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const found = validateForm(form, { editing: Boolean(editing) });
    if (!lawFirmId) found.invoiceNumber = found.invoiceNumber || 'Votre cabinet est introuvable dans votre session : reconnectez-vous.';
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    // Le client est celui du dossier : il n'est jamais choisi à la main, donc la facture reste cohérente avec le dossier.
    const clientId = editing ? editing.clientId : selectedCase?.clientId;
    const caseId = editing ? editing.caseId : form.caseId;
    const payload = buildPayload(form, { lawFirmId, caseId, clientId });
    const action = editing
      ? () => api.put(`/api/invoices/${editing.id}`, payload)
      : () => api.post('/api/invoices', payload);
    const ok = await run(
      editing ? `save-${editing.id}` : 'create',
      action,
      editing ? 'Facture modifiée.' : 'Facture créée et envoyée.',
      (error) => describeInvoiceError(error, { saving: true })
    );
    if (ok) {
      closeForm();
      invoicesApi.refetch();
    }
  }

  async function changeStatus(invoice, status, message, confirmation) {
    if (confirmation && !window.confirm(confirmation)) return;
    const ok = await run(
      `status-${invoice.id}`,
      () => api.patch(`/api/invoices/${invoice.id}/status?status=${status}`),
      message,
      describeInvoiceError
    );
    if (ok) invoicesApi.refetch();
  }

  async function remove(invoice) {
    if (!window.confirm('Supprimer définitivement cette facture ?')) return;
    const ok = await run(`delete-${invoice.id}`, () => api.delete(`/api/invoices/${invoice.id}`), 'Facture supprimée.', describeInvoiceError);
    if (ok) invoicesApi.refetch();
  }

  const saving = busyKey === 'create' || (editing && busyKey === `save-${editing.id}`);

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow={isAdmin ? 'Administration' : 'Espace avocat'}
        title="Factures"
        description="Émettez vos factures, suivez leur règlement et relancez les paiements en retard."
        action={
          <>
            <button type="button" className={secondaryButton} onClick={invoicesApi.refetch}>
              Actualiser
            </button>
            <button type="button" className={primaryButton} onClick={openCreate}>
              Nouvelle facture
            </button>
          </>
        }
      />

      <div className="grid gap-4 md:grid-cols-3">
        <StatCard label="À encaisser" accent="blue" value={invoicesApi.error ? '—' : formatMoney(totals.toCollect.amount)}
          trend={invoicesApi.error ? 'Indisponible' : `${totals.toCollect.count} facture(s)`} />
        <StatCard label="En retard" accent="rose" value={invoicesApi.error ? '—' : formatMoney(totals.late.amount)}
          trend={invoicesApi.error ? 'Indisponible' : `${totals.late.count} facture(s)`} />
        <StatCard label="Encaissé" accent="emerald" value={invoicesApi.error ? '—' : formatMoney(totals.collected.amount)}
          trend={invoicesApi.error ? 'Indisponible' : `${totals.collected.count} facture(s) payée(s)`} />
      </div>

      {feedback ? (
        <div
          className={`rounded-xl border p-3 text-sm ${
            feedback.type === 'success'
              ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
              : 'border-rose-200 bg-rose-50 text-rose-700'
          }`}
        >
          {feedback.message}
        </div>
      ) : null}

      {formOpen ? (
        <SectionCard title={editing ? `Modifier la facture ${editing.invoiceNumber}` : 'Nouvelle facture'}>
          <form onSubmit={handleSubmit} className="grid gap-4 md:grid-cols-2" noValidate>
            <FormField label="Dossier" htmlFor="invoice-case" required error={errors.caseId}
              hint={editing ? 'Le dossier ne peut pas être changé : annulez cette facture et créez-en une nouvelle.' : undefined}>
              <select id="invoice-case" className={inputClass} value={form.caseId} disabled={Boolean(editing)}
                onChange={(event) => update('caseId', event.target.value)}>
                <option value="">— Choisir un dossier —</option>
                {cases.map((item) => (
                  <option key={item.id} value={item.id}>{item.title} ({item.reference})</option>
                ))}
              </select>
            </FormField>

            <FormField label="Client" htmlFor="invoice-client" hint="Déduit du dossier.">
              <input id="invoice-client" type="text" className={inputClass} readOnly
                value={clientsById.get(editing ? editing.clientId : selectedCase?.clientId) || '—'} />
            </FormField>

            <FormField label="Numéro de facture" htmlFor="invoice-number" required error={errors.invoiceNumber}>
              <input id="invoice-number" type="text" className={inputClass} value={form.invoiceNumber} maxLength={50}
                onChange={(event) => update('invoiceNumber', event.target.value)} />
            </FormField>

            <FormField label="Montant" htmlFor="invoice-amount" required error={errors.amount}>
              <input id="invoice-amount" type="text" inputMode="decimal" className={inputClass} value={form.amount}
                placeholder="1 250,00" onChange={(event) => update('amount', event.target.value)} />
            </FormField>

            <FormField label="Date d'échéance" htmlFor="invoice-due" required error={errors.dueDate}>
              <input id="invoice-due" type="date" className={inputClass} value={form.dueDate}
                onChange={(event) => update('dueDate', event.target.value)} />
            </FormField>

            <FormField label="Description" htmlFor="invoice-description" required error={errors.description}>
              <input id="invoice-description" type="text" className={inputClass} value={form.description} maxLength={255}
                placeholder="Honoraires, provision, frais d'huissier…" onChange={(event) => update('description', event.target.value)} />
            </FormField>

            <div className="flex gap-2 md:col-span-2">
              <button type="submit" className={primaryButton} disabled={Boolean(saving)}>
                {saving ? 'Enregistrement…' : editing ? 'Enregistrer les modifications' : 'Créer et envoyer la facture'}
              </button>
              <button type="button" className={secondaryButton} onClick={closeForm}>
                Annuler
              </button>
            </div>
          </form>
        </SectionCard>
      ) : null}

      <SectionCard
        title="Suivi des factures"
        action={
          <div className="flex flex-wrap gap-2">
            {tabs.map((item) => (
              <button
                key={item.key}
                type="button"
                onClick={() => setTab(item.key)}
                className={tab === item.key ? primaryButton : secondaryButton}
              >
                {item.label} ({groups[item.key].length})
              </button>
            ))}
          </div>
        }
      >
        <AsyncState
          loading={invoicesApi.loading}
          error={invoicesApi.error}
          onRetry={invoicesApi.refetch}
          isEmpty={visible.length === 0}
          emptyMessage={TABS.find((item) => item.key === tab).empty}
          loadingMessage="Chargement des factures…"
        >
          <div className="overflow-x-auto">
            <table className="w-full min-w-[960px] text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-4 font-medium">N°</th>
                  <th className="py-2 pr-4 font-medium">Client</th>
                  <th className="py-2 pr-4 font-medium">Dossier</th>
                  <th className="py-2 pr-4 text-right font-medium">Montant</th>
                  <th className="py-2 pr-4 font-medium">Échéance</th>
                  <th className="py-2 pr-4 font-medium">Statut</th>
                  <th className="py-2 font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((invoice) => {
                  const late = isLate(invoice, now);
                  const status = getInvoiceStatusDisplay(late ? 'OVERDUE' : invoice.status);
                  const actions = invoiceActions(invoice, { isAdmin });
                  const delay = describeDelay(invoice, now);
                  const busy = busyKey && busyKey.endsWith(invoice.id);
                  const expanded = expandedId === invoice.id;
                  const linkedCase = casesById.get(invoice.caseId);

                  return (
                    <Fragment key={invoice.id}>
                      <tr className="border-b border-slate-100 align-top last:border-0">
                        <td className="py-3 pr-4 font-medium text-slate-800">{invoice.invoiceNumber}</td>
                        <td className="py-3 pr-4 text-slate-600">{clientsById.get(invoice.clientId) || 'Client inconnu'}</td>
                        <td className="py-3 pr-4 text-slate-600">{linkedCase ? `${linkedCase.title} (${linkedCase.reference})` : 'Dossier inconnu'}</td>
                        <td className="py-3 pr-4 text-right font-medium text-slate-800">{formatMoney(invoice.amount)}</td>
                        <td className="py-3 pr-4 text-slate-600">
                          <div>{formatDay(invoice.dueDate)}</div>
                          {delay ? <div className={`text-xs ${late ? 'font-medium text-rose-600' : 'text-slate-500'}`}>{delay}</div> : null}
                        </td>
                        <td className="py-3 pr-4"><StatusBadge tone={status.tone}>{status.label}</StatusBadge></td>
                        <td className="py-3">
                          <div className="flex flex-wrap gap-2">
                            <button type="button" className={secondaryButton} onClick={() => setExpandedId(expanded ? null : invoice.id)}>
                              {expanded ? 'Masquer' : 'Détails'}
                            </button>
                            {actions.canEdit ? (
                              <button type="button" className={secondaryButton} disabled={Boolean(busy)} onClick={() => openEdit(invoice)}>
                                Modifier
                              </button>
                            ) : null}
                            {actions.canSend ? (
                              <button type="button" className={secondaryButton} disabled={Boolean(busy)}
                                onClick={() => changeStatus(invoice, 'SENT', 'Facture envoyée.')}>
                                Envoyer
                              </button>
                            ) : null}
                            {actions.canPay ? (
                              <button type="button" className={secondaryButton} disabled={Boolean(busy)}
                                onClick={() => changeStatus(invoice, 'PAID', 'Facture marquée comme payée.',
                                  `Marquer la facture ${invoice.invoiceNumber} comme payée ? Elle ne pourra plus être modifiée.`)}>
                                Payée
                              </button>
                            ) : null}
                            {actions.canCancel ? (
                              <button type="button" className={dangerButton} disabled={Boolean(busy)}
                                onClick={() => changeStatus(invoice, 'CANCELLED', 'Facture annulée.',
                                  `Annuler la facture ${invoice.invoiceNumber} ? Cette action est définitive.`)}>
                                Annuler
                              </button>
                            ) : null}
                            {actions.canDelete ? (
                              <button type="button" className={dangerButton} disabled={Boolean(busy)} onClick={() => remove(invoice)}>
                                Supprimer
                              </button>
                            ) : null}
                          </div>
                        </td>
                      </tr>
                      {expanded ? (
                        <tr className="border-b border-slate-100 bg-slate-50">
                          <td colSpan={7} className="px-3 py-3 text-sm text-slate-600">
                            <dl className="grid gap-2 md:grid-cols-3">
                              <div className="md:col-span-3">
                                <dt className="text-xs uppercase text-slate-500">Description</dt>
                                <dd className="whitespace-pre-wrap">{invoice.description || '—'}</dd>
                              </div>
                              <div><dt className="text-xs uppercase text-slate-500">Créée le</dt><dd>{formatDay(invoice.createdAt)}</dd></div>
                              <div><dt className="text-xs uppercase text-slate-500">Dernière modification</dt><dd>{formatDay(invoice.updatedAt)}</dd></div>
                              <div><dt className="text-xs uppercase text-slate-500">Statut serveur</dt><dd>{getInvoiceStatusDisplay(invoice.status).label}</dd></div>
                            </dl>
                          </td>
                        </tr>
                      ) : null}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        </AsyncState>
      </SectionCard>
    </div>
  );
}
