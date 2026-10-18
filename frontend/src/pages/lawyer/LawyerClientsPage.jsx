import { useMemo, useState } from 'react';
import PageHeader from '../../components/common/PageHeader';
import SectionCard from '../../components/common/SectionCard';
import AsyncState from '../../components/common/AsyncState';
import FormField, { inputClass } from '../../components/common/FormField';
import { primaryButton, secondaryButton } from '../../components/common/buttonStyles';
import useApi from '../../hooks/useApi';
import useAction from '../../hooks/useAction';
import { api } from '../../services/api';
import { keycloak } from '../../context/AuthContext';
import { formatDate, sortByCreatedAtDesc } from '../../constants/caseStatus';

const EMPTY_FORM = { firstName: '', lastName: '', email: '', phone: '', address: '', lawyerIds: [] };
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
  if (error.status === 403) return "Vous n'avez pas le droit d'effectuer cette action.";
  if (error.status >= 500) {
    return `Erreur serveur (${error.status}). Vérifiez que le client-service est démarré (docker ps).`;
  }
  return error.message || "L'action a échoué.";
}

/** Choix des avocats en charge d'un client (administrateur et secrétaire). */
function LawyerPicker({ lawyers, selected, onChange, disabled }) {
  if (lawyers.length === 0) {
    return (
      <p className="text-sm text-slate-500">
        Aucun avocat disponible. Créez d’abord des comptes avocats dans « Utilisateurs ».
      </p>
    );
  }

  return (
    <div className="flex flex-wrap gap-4">
      {lawyers.map((lawyer) => (
        <label key={lawyer.id} className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={selected.includes(lawyer.id)}
            disabled={disabled}
            onChange={() =>
              onChange(selected.includes(lawyer.id) ? selected.filter((id) => id !== lawyer.id) : [...selected, lawyer.id])
            }
          />
          {lawyer.firstName} {lawyer.lastName}
        </label>
      ))}
    </div>
  );
}

/**
 * Clients du cabinet.
 * - Avocat : uniquement les clients dont il est en charge (un client qu'il crée lui est confié automatiquement).
 * - Administrateur / secrétaire : tous les clients, avec désignation des avocats en charge.
 */
export default function LawyerClientsPage() {
  const isOffice = Boolean(keycloak.hasRealmRole?.('ADMIN') || keycloak.hasRealmRole?.('SECRETARY'));

  const { data, loading, error, refetch } = useApi('/api/clients');
  const lawyersApi = useApi('/api/users/lawyers', { enabled: isOffice });
  const { busyKey, feedback, run, clearFeedback } = useAction();

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [search, setSearch] = useState('');
  const [editing, setEditing] = useState(null);

  const lawyers = useMemo(() => (Array.isArray(lawyersApi.data) ? lawyersApi.data : []), [lawyersApi.data]);

  const lawyerNames = useMemo(() => {
    const names = {};
    lawyers.forEach((lawyer) => {
      names[lawyer.id] = `${lawyer.firstName} ${lawyer.lastName}`;
    });
    return names;
  }, [lawyers]);

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
    clearFeedback();
  }

  function closeForm() {
    setShowForm(false);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setSubmitError(null);
  }

  function describeInviteError(error) {
    if (error.status === 409) {
      return error.message?.includes('disabled')
        ? "Le compte de ce client est désactivé : réactivez-le dans « Utilisateurs » avant de l'inviter."
        : 'Un compte existe déjà avec cet email.';
    }
    if (error.status === 422) {
      return error.message?.includes('email')
        ? "Ce client n'a pas d'adresse email : impossible de l'inviter."
        : 'Client introuvable dans votre cabinet.';
    }
    if (error.status === 403) return "Vous n'avez pas le droit d'inviter ce client.";
    if (error.status >= 500) {
      return "L'invitation n'a pas pu être envoyée (serveur d'authentification ou messagerie indisponible).";
    }
    return error.message || "L'invitation a échoué.";
  }

  function inviteClient(client) {
    return run(
      `invite-${client.id}`,
      () => api.post(`/api/users/clients/${client.id}/invitation`),
      `Invitation envoyée à ${client.email} : le client choisira son mot de passe via le lien reçu (valable 72 h).`,
      describeInviteError
    );
  }

  async function handleSubmit(event) {
    event.preventDefault();

    const errors = validate(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    setSubmitError(null);

    try {
      const body = {
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        email: form.email.trim(),
        phone: form.phone.trim() || null,
        address: form.address.trim() || null
      };
      // Un avocat devient automatiquement responsable du client qu'il crée ; l'administration désigne les avocats.
      if (isOffice) body.lawyerIds = form.lawyerIds;

      const created = await api.post('/api/clients', body);
      closeForm();
      setSuccess(`Client « ${created.firstName} ${created.lastName} » créé avec succès.`);
      refetch();
    } catch (requestError) {
      setSubmitError(describeSubmitError(requestError));
    } finally {
      setSubmitting(false);
    }
  }

  async function saveLawyers() {
    const ok = await run(
      `lawyers-${editing.id}`,
      () => api.put(`/api/clients/${editing.id}/lawyers`, { lawyerIds: editing.lawyerIds }),
      'Avocats en charge mis à jour.',
      describeSubmitError
    );
    if (ok) {
      setEditing(null);
      refetch();
    }
  }

  const columnCount = isOffice ? 8 : 6;

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow={isOffice ? 'Administration' : 'Espace avocat'}
        title="Clients"
        description={
          isOffice
            ? "Tous les clients du cabinet. Désignez l'avocat en charge de chacun : il sera le seul avocat à le voir."
            : 'Les clients dont vous êtes en charge. Un client que vous créez vous est automatiquement confié.'
        }
        action={
          !showForm ? (
            <button type="button" onClick={openForm} className={primaryButton}>
              Nouveau client
            </button>
          ) : null
        }
      />

      {success ? (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700">{success}</div>
      ) : null}

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

            {isOffice ? (
              <FormField
                label="Avocat(s) en charge"
                htmlFor="lawyerIds"
                hint="Facultatif : sans avocat en charge, seuls l'administration et le secrétariat voient ce client."
              >
                <LawyerPicker
                  lawyers={lawyers}
                  selected={form.lawyerIds}
                  onChange={(ids) => updateField('lawyerIds', ids)}
                  disabled={submitting}
                />
              </FormField>
            ) : null}

            {submitError ? (
              <div className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">{submitError}</div>
            ) : null}

            <div className="flex gap-3">
              <button type="submit" disabled={submitting} className={primaryButton}>
                {submitting ? 'Enregistrement…' : 'Enregistrer'}
              </button>
              <button type="button" onClick={closeForm} disabled={submitting} className={secondaryButton}>
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
          emptyMessage={
            search
              ? 'Aucun client ne correspond à la recherche.'
              : isOffice
                ? 'Aucun client pour le moment.'
                : "Aucun client ne vous est confié pour le moment. Créez-en un, ou demandez à l'administration de vous en confier."
          }
          loadingMessage="Chargement des clients…"
        >
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-4 font-medium">Nom</th>
                  <th className="py-2 pr-4 font-medium">Email</th>
                  <th className="py-2 pr-4 font-medium">Téléphone</th>
                  {isOffice ? <th className="py-2 pr-4 font-medium">Avocat(s) en charge</th> : null}
                  <th className="py-2 pr-4 font-medium">Adresse</th>
                  <th className="py-2 pr-4 font-medium">Créé le</th>
                  <th className="py-2 pr-4 font-medium">Accès</th>
                  {isOffice ? <th className="py-2 font-medium">Actions</th> : null}
                </tr>
              </thead>
              <tbody>
                {clients.map((client) => {
                  const assigned = Array.isArray(client.lawyerIds) ? client.lawyerIds : [];
                  const isEditing = editing?.id === client.id;

                  return [
                    <tr key={client.id} className="border-b border-slate-100 align-top last:border-0">
                      <td className="py-3 pr-4 font-medium text-slate-800">
                        {client.firstName} {client.lastName}
                      </td>
                      <td className="py-3 pr-4 text-slate-600">{client.email}</td>
                      <td className="py-3 pr-4 text-slate-600">{client.phone || '—'}</td>
                      {isOffice ? (
                        <td className="py-3 pr-4 text-slate-600">
                          {assigned.length === 0
                            ? 'Non confié'
                            : assigned.map((id) => lawyerNames[id] || `${id.slice(0, 8)}…`).join(', ')}
                        </td>
                      ) : null}
                      <td className="py-3 pr-4 text-slate-600">{client.address || '—'}</td>
                      <td className="py-3 pr-4 text-slate-600">{formatDate(client.createdAt)}</td>
                      <td className="py-3 pr-4">
                        <button
                          type="button"
                          className={secondaryButton}
                          title="Envoie un email avec un lien pour choisir son mot de passe. Peut être renvoyé."
                          disabled={busyKey === `invite-${client.id}`}
                          onClick={() => inviteClient(client)}
                        >
                          {busyKey === `invite-${client.id}` ? 'Envoi…' : 'Inviter'}
                        </button>
                      </td>
                      {isOffice ? (
                        <td className="py-3">
                          <button
                            type="button"
                            className={secondaryButton}
                            onClick={() => setEditing({ id: client.id, lawyerIds: assigned })}
                          >
                            Modifier
                          </button>
                        </td>
                      ) : null}
                    </tr>,
                    isOffice && isEditing ? (
                      <tr key={`${client.id}-editor`} className="border-b border-slate-100 bg-slate-50">
                        <td colSpan={columnCount} className="space-y-3 px-4 py-4">
                          <div className="text-sm font-medium text-slate-700">
                            Avocat(s) en charge de {client.firstName} {client.lastName}
                          </div>
                          <LawyerPicker
                            lawyers={lawyers}
                            selected={editing.lawyerIds}
                            onChange={(ids) => setEditing({ ...editing, lawyerIds: ids })}
                            disabled={busyKey === `lawyers-${client.id}`}
                          />
                          <div className="flex gap-3">
                            <button
                              type="button"
                              className={primaryButton}
                              disabled={busyKey === `lawyers-${client.id}`}
                              onClick={saveLawyers}
                            >
                              Enregistrer
                            </button>
                            <button type="button" className={secondaryButton} onClick={() => setEditing(null)}>
                              Annuler
                            </button>
                          </div>
                        </td>
                      </tr>
                    ) : null
                  ];
                })}
              </tbody>
            </table>
          </div>
        </AsyncState>
      </SectionCard>
    </div>
  );
}
