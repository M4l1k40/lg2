import { useMemo, useState } from 'react';
import PageHeader from '../../components/common/PageHeader';
import SectionCard from '../../components/common/SectionCard';
import AsyncState from '../../components/common/AsyncState';
import FormField, { inputClass } from '../../components/common/FormField';
import useApi from '../../hooks/useApi';
import { api } from '../../services/api';
import { formatDate, sortByCreatedAtDesc } from '../../constants/caseStatus';

const EMPTY_FORM = { firstName: '', lastName: '', email: '', phone: '', address: '' };
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function validate(form) {
  const errors = {};
  if (!form.firstName.trim()) errors.firstName = 'Le prénom est obligatoire.';
  if (!form.lastName.trim()) errors.lastName = 'Le nom est obligatoire.';
  if (!form.email.trim()) errors.email = "L'email est obligatoire.";
  else if (!EMAIL_REGEX.test(form.email.trim())) errors.email = "Format d'email invalide.";
  return errors;
}

function describeSubmitError(error) {
  if (error.status === 409) return error.message || 'Un client avec cet email existe déjà.';
  if (error.status === 400) return 'Les données saisies sont invalides.';
  if (error.status === 403) return "Vous n'avez pas le droit de créer un client.";
  if (error.status >= 500) {
    return `Erreur serveur (${error.status}). Vérifiez que le client-service est démarré (docker ps).`;
  }
  return error.message || 'Impossible de créer le client.';
}

export default function LawyerClientsPage() {
  const { data, loading, error, refetch } = useApi('/api/clients');

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [search, setSearch] = useState('');

  const clients = useMemo(() => {
    const list = sortByCreatedAtDesc(Array.isArray(data) ? data : []);
    const term = search.trim().toLowerCase();
    if (!term) return list;
    return list.filter((client) =>
      `${client.firstName} ${client.lastName} ${client.email}`.toLowerCase().includes(term)
    );
  }, [data, search]);

  function updateField(name, value) {
    setForm((previous) => ({ ...previous, [name]: value }));
    setFieldErrors((previous) => ({ ...previous, [name]: undefined }));
  }

  function openForm() {
    setShowForm(true);
    setSuccess(null);
    setSubmitError(null);
  }

  function closeForm() {
    setShowForm(false);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setSubmitError(null);
  }

  async function handleSubmit(event) {
    event.preventDefault();

    const errors = validate(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    setSubmitError(null);

    try {
      const created = await api.post('/api/clients', {
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        email: form.email.trim(),
        phone: form.phone.trim() || null,
        address: form.address.trim() || null
      });
      closeForm();
      setSuccess(`Client « ${created.firstName} ${created.lastName} » créé avec succès.`);
      refetch();
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
        title="Clients"
        description="Consultez les clients du cabinet et ajoutez-en de nouveaux."
        action={
          !showForm ? (
            <button
              type="button"
              onClick={openForm}
              className="rounded-xl bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-700"
            >
              Nouveau client
            </button>
          ) : null
        }
      />

      {success ? (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700">{success}</div>
      ) : null}

      {showForm ? (
        <SectionCard title="Nouveau client">
          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2">
              <FormField label="Prénom" htmlFor="firstName" required error={fieldErrors.firstName}>
                <input
                  id="firstName"
                  className={inputClass}
                  value={form.firstName}
                  onChange={(event) => updateField('firstName', event.target.value)}
                  disabled={submitting}
                />
              </FormField>
              <FormField label="Nom" htmlFor="lastName" required error={fieldErrors.lastName}>
                <input
                  id="lastName"
                  className={inputClass}
                  value={form.lastName}
                  onChange={(event) => updateField('lastName', event.target.value)}
                  disabled={submitting}
                />
              </FormField>
              <FormField label="Email" htmlFor="email" required error={fieldErrors.email}>
                <input
                  id="email"
                  type="email"
                  className={inputClass}
                  value={form.email}
                  onChange={(event) => updateField('email', event.target.value)}
                  disabled={submitting}
                />
              </FormField>
              <FormField label="Téléphone" htmlFor="phone">
                <input
                  id="phone"
                  className={inputClass}
                  value={form.phone}
                  onChange={(event) => updateField('phone', event.target.value)}
                  disabled={submitting}
                />
              </FormField>
            </div>
            <FormField label="Adresse" htmlFor="address">
              <input
                id="address"
                className={inputClass}
                value={form.address}
                onChange={(event) => updateField('address', event.target.value)}
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
                {submitting ? 'Enregistrement…' : 'Enregistrer'}
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
        </SectionCard>
      ) : null}

      <SectionCard
        title={`Liste des clients${Array.isArray(data) ? ` (${data.length})` : ''}`}
        action={
          <input
            type="search"
            placeholder="Rechercher…"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            className={`${inputClass} max-w-[220px]`}
          />
        }
      >
        <AsyncState
          loading={loading}
          error={error}
          onRetry={refetch}
          isEmpty={clients.length === 0}
          emptyMessage={search ? 'Aucun client ne correspond à la recherche.' : 'Aucun client pour le moment.'}
          loadingMessage="Chargement des clients…"
        >
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-4 font-medium">Nom</th>
                  <th className="py-2 pr-4 font-medium">Email</th>
                  <th className="py-2 pr-4 font-medium">Téléphone</th>
                  <th className="py-2 pr-4 font-medium">Adresse</th>
                  <th className="py-2 font-medium">Créé le</th>
                </tr>
              </thead>
              <tbody>
                {clients.map((client) => (
                  <tr key={client.id} className="border-b border-slate-100 last:border-0">
                    <td className="py-3 pr-4 font-medium text-slate-800">
                      {client.firstName} {client.lastName}
                    </td>
                    <td className="py-3 pr-4 text-slate-600">{client.email}</td>
                    <td className="py-3 pr-4 text-slate-600">{client.phone || '—'}</td>
                    <td className="py-3 pr-4 text-slate-600">{client.address || '—'}</td>
                    <td className="py-3 text-slate-600">{formatDate(client.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </AsyncState>
      </SectionCard>
    </div>
  );
}
