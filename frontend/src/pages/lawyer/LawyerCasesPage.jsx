import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import SectionCard from '../../components/common/SectionCard';
import StatusBadge from '../../components/common/StatusBadge';
import AsyncState from '../../components/common/AsyncState';
import FormField, { inputClass } from '../../components/common/FormField';
import useApi from '../../hooks/useApi';
import { api } from '../../services/api';
import { keycloak } from '../../context/AuthContext';
import {
  STATUS_OPTIONS,
  formatDate,
  getStatusDisplay,
  sortByCreatedAtDesc
} from '../../constants/caseStatus';

function generateReference() {
  const suffix = String(Math.floor(Math.random() * 9000) + 1000);
  return `CASE-${new Date().getFullYear()}-${suffix}`;
}

function buildEmptyForm() {
  return { clientId: '', reference: generateReference(), title: '', description: '', status: 'OPEN' };
}

function validate(form) {
  const errors = {};
  if (!form.clientId) errors.clientId = 'Choisissez un client.';
  if (!form.reference.trim()) errors.reference = 'La référence est obligatoire.';
  if (!form.title.trim()) errors.title = 'Le titre est obligatoire.';
  return errors;
}

function describeSubmitError(error) {
  if (error.status === 400) return 'Les données saisies sont invalides (client, référence ou titre).';
  if (error.status === 403) return "Vous n'avez pas le droit de créer un dossier.";
  if (error.status === 404) return 'Client introuvable pour ce cabinet.';
  if (error.status === 409) return error.message || 'Cette référence existe déjà.';
  if (error.status >= 500) {
    return `Erreur serveur (${error.status}). Vérifiez que le case-service est démarré (docker ps).`;
  }
  return error.message || 'Impossible de créer le dossier.';
}

export default function LawyerCasesPage() {
  const casesApi = useApi('/api/cases');
  const clientsApi = useApi('/api/clients');

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(buildEmptyForm);
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [statusFilter, setStatusFilter] = useState('ALL');

  const clients = useMemo(() => (Array.isArray(clientsApi.data) ? clientsApi.data : []), [clientsApi.data]);

  const clientNames = useMemo(() => {
    const names = {};
    clients.forEach((client) => {
      names[client.id] = `${client.firstName} ${client.lastName}`;
    });
    return names;
  }, [clients]);

  const cases = useMemo(() => {
    const list = sortByCreatedAtDesc(Array.isArray(casesApi.data) ? casesApi.data : []);
    return statusFilter === 'ALL' ? list : list.filter((item) => item.status === statusFilter);
  }, [casesApi.data, statusFilter]);

  function updateField(name, value) {
    setForm((previous) => ({ ...previous, [name]: value }));
    setFieldErrors((previous) => ({ ...previous, [name]: undefined }));
  }

  function openForm() {
    setForm(buildEmptyForm());
    setShowForm(true);
    setSuccess(null);
    setSubmitError(null);
  }

  function closeForm() {
    setShowForm(false);
    setFieldErrors({});
    setSubmitError(null);
  }

  async function handleSubmit(event) {
    event.preventDefault();

    const errors = validate(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    // L'avocat connecté est le responsable du dossier (identifiant Keycloak)
    const lawyerId = keycloak.tokenParsed?.sub;
    if (!lawyerId) {
      setSubmitError('Identifiant avocat introuvable, reconnectez-vous.');
      return;
    }

    setSubmitting(true);
    setSubmitError(null);

    try {
      const created = await api.post('/api/cases', {
        clientId: form.clientId,
        lawyerId,
        reference: form.reference.trim(),
        title: form.title.trim(),
        description: form.description.trim() || null,
        status: form.status
      });
      closeForm();
      setSuccess(`Dossier « ${created.title} » (${created.reference}) créé avec succès.`);
      casesApi.refetch();
    } catch (requestError) {
      setSubmitError(describeSubmitError(requestError));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Espace avocat"
        title="Dossiers"
        description="Suivez les dossiers du cabinet et ouvrez-en de nouveaux."
        action={
          !showForm ? (
            <button
              type="button"
              onClick={openForm}
              className="rounded-xl bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-700"
            >
              Nouveau dossier
            </button>
          ) : null
        }
      />

      {success ? (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700">{success}</div>
      ) : null}

      {showForm ? (
        <SectionCard title="Nouveau dossier">
          {!clientsApi.loading && clients.length === 0 ? (
            <p className="text-sm text-slate-600">
              Aucun client n’existe encore. Créez d’abord un client dans{' '}
              <Link to="/lawyer/clients" className="font-medium text-slate-900 underline">
                Clients
              </Link>
              .
            </p>
          ) : (
            <form onSubmit={handleSubmit} noValidate className="space-y-4">
              <div className="grid gap-4 md:grid-cols-2">
                <FormField label="Client" htmlFor="clientId" required error={fieldErrors.clientId}>
                  <select
                    id="clientId"
                    className={inputClass}
                    value={form.clientId}
                    onChange={(event) => updateField('clientId', event.target.value)}
                    disabled={submitting}
                  >
                    <option value="">— Choisir un client —</option>
                    {clients.map((client) => (
                      <option key={client.id} value={client.id}>
                        {client.firstName} {client.lastName} ({client.email})
                      </option>
                    ))}
                  </select>
                </FormField>
                <FormField label="Référence" htmlFor="reference" required error={fieldErrors.reference}>
                  <input
                    id="reference"
                    className={inputClass}
                    value={form.reference}
                    onChange={(event) => updateField('reference', event.target.value)}
                    disabled={submitting}
                  />
                </FormField>
                <FormField label="Titre" htmlFor="title" required error={fieldErrors.title}>
                  <input
                    id="title"
                    className={inputClass}
                    value={form.title}
                    onChange={(event) => updateField('title', event.target.value)}
                    disabled={submitting}
                  />
                </FormField>
                <FormField label="Statut" htmlFor="status">
                  <select
                    id="status"
                    className={inputClass}
                    value={form.status}
                    onChange={(event) => updateField('status', event.target.value)}
                    disabled={submitting}
                  >
                    {STATUS_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </FormField>
              </div>
              <FormField label="Description" htmlFor="description" hint="2000 caractères maximum.">
                <textarea
                  id="description"
                  rows={3}
                  maxLength={2000}
                  className={inputClass}
                  value={form.description}
                  onChange={(event) => updateField('description', event.target.value)}
                  disabled={submitting}
                />
              </FormField>

              {submitError ? (
                <div className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">{submitError}</div>
              ) : null}

              <div className="flex gap-3">
                <button
                  type="submit"
                  disabled={submitting}
                  className="rounded-xl bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-700 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {submitting ? 'Enregistrement…' : 'Créer le dossier'}
                </button>
                <button
                  type="button"
                  onClick={closeForm}
                  disabled={submitting}
                  className="rounded-xl border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
                >
                  Annuler
                </button>
              </div>
            </form>
          )}
        </SectionCard>
      ) : null}

      <SectionCard
        title={`Liste des dossiers${Array.isArray(casesApi.data) ? ` (${casesApi.data.length})` : ''}`}
        action={
          <select
            aria-label="Filtrer par statut"
            className={`${inputClass} max-w-[220px]`}
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
          >
            <option value="ALL">Tous les statuts</option>
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        }
      >
        <AsyncState
          loading={casesApi.loading}
          error={casesApi.error}
          onRetry={casesApi.refetch}
          isEmpty={cases.length === 0}
          emptyMessage={statusFilter === 'ALL' ? 'Aucun dossier pour le moment.' : 'Aucun dossier avec ce statut.'}
          loadingMessage="Chargement des dossiers…"
        >
          <div className="space-y-3">
            {cases.map((item) => {
              const status = getStatusDisplay(item.status);

              return (
                <div
                  key={item.id}
                  className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div className="min-w-0">
                    <div className="font-medium text-slate-800">{item.title}</div>
                    <div className="text-sm text-slate-500">
                      Réf. {item.reference} · Client : {clientNames[item.clientId] || 'Inconnu'} · Ouvert le{' '}
                      {formatDate(item.createdAt)}
                    </div>
                    {item.description ? (
                      <div className="mt-1 line-clamp-2 text-sm text-slate-600">{item.description}</div>
                    ) : null}
                  </div>
                  <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
                </div>
              );
            })}
          </div>
        </AsyncState>
      </SectionCard>
    </div>
  );
}
