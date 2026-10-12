import { useMemo, useState } from 'react';
import PageHeader from '../../components/common/PageHeader';
import SectionCard from '../../components/common/SectionCard';
import StatusBadge from '../../components/common/StatusBadge';
import AsyncState from '../../components/common/AsyncState';
import FormField, { inputClass } from '../../components/common/FormField';
import { dangerButton, primaryButton, secondaryButton } from '../../components/common/buttonStyles';
import useApi from '../../hooks/useApi';
import useAction from '../../hooks/useAction';
import { api } from '../../services/api';
import { keycloak } from '../../context/AuthContext';

const ROLES = {
  ADMIN: { label: 'Administrateur', tone: 'violet' },
  LAWYER: { label: 'Avocat', tone: 'blue' },
  SECRETARY: { label: 'Secrétaire', tone: 'amber' },
  CLIENT: { label: 'Client', tone: 'emerald' }
};

const EMPTY_FORM = {
  role: 'LAWYER',
  username: '',
  email: '',
  firstName: '',
  lastName: '',
  clientId: '',
  temporaryPassword: ''
};

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const USERNAME_REGEX = /^[a-zA-Z0-9._-]+$/;

function validate(form) {
  const errors = {};
  const username = form.username.trim();
  if (username.length < 3) errors.username = 'Au moins 3 caractères.';
  else if (!USERNAME_REGEX.test(username)) errors.username = 'Lettres, chiffres, « . », « _ » et « - » uniquement.';
  if (!EMAIL_REGEX.test(form.email.trim())) errors.email = "Format d'email invalide.";
  if (!form.firstName.trim()) errors.firstName = 'Le prénom est obligatoire.';
  if (!form.lastName.trim()) errors.lastName = 'Le nom est obligatoire.';
  if (form.role === 'CLIENT' && !form.clientId) errors.clientId = 'Choisissez la fiche client à lier à ce compte.';
  if (form.temporaryPassword && form.temporaryPassword.length < 10) {
    errors.temporaryPassword = 'Au moins 10 caractères (ou laissez vide pour en générer un).';
  }
  return errors;
}

function describeError(error) {
  switch (error.status) {
    case 400:
      return error.message || 'Les données saisies sont invalides.';
    case 403:
      return "Action réservée à l'administrateur du cabinet.";
    case 404:
      return 'Utilisateur introuvable.';
    case 409:
      return error.message || 'Conflit : cet utilisateur existe déjà, ou cette action est impossible.';
    case 422:
      return "La fiche client choisie n'existe pas dans votre cabinet.";
    case 502:
    case 503:
      return `Le service Utilisateurs ou Keycloak est indisponible (${error.status}). Vérifiez que user-service est démarré.`;
    default:
      return error.message || "L'action a échoué.";
  }
}

/** Gestion des comptes du cabinet (administrateur) : liste, création, activation, réinitialisation du mot de passe. */
export default function AdminUsersPage({ initialRole = 'ALL' }) {
  const usersApi = useApi('/api/users');
  const clientsApi = useApi('/api/clients');
  const { busyKey, feedback, run, clearFeedback } = useAction();

  const [roleFilter, setRoleFilter] = useState(initialRole);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ ...EMPTY_FORM, role: initialRole === 'ALL' ? 'LAWYER' : initialRole });
  const [fieldErrors, setFieldErrors] = useState({});
  const [secret, setSecret] = useState(null);

  const myId = keycloak.tokenParsed?.sub;

  const users = useMemo(() => {
    const list = Array.isArray(usersApi.data) ? usersApi.data : [];
    const filtered = roleFilter === 'ALL' ? list : list.filter((user) => user.role === roleFilter);
    return [...filtered].sort((a, b) => `${a.lastName} ${a.firstName}`.localeCompare(`${b.lastName} ${b.firstName}`));
  }, [usersApi.data, roleFilter]);

  const clients = useMemo(() => (Array.isArray(clientsApi.data) ? clientsApi.data : []), [clientsApi.data]);

  const clientNames = useMemo(() => {
    const names = {};
    clients.forEach((client) => {
      names[client.id] = `${client.firstName} ${client.lastName}`;
    });
    return names;
  }, [clients]);

  function updateField(name, value) {
    setForm((previous) => ({ ...previous, [name]: value }));
    setFieldErrors((previous) => ({ ...previous, [name]: undefined }));
  }

  function closeForm() {
    setShowForm(false);
    setForm({ ...EMPTY_FORM, role: initialRole === 'ALL' ? 'LAWYER' : initialRole });
    setFieldErrors({});
  }

  async function handleCreate(event) {
    event.preventDefault();
    const errors = validate(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    let created = null;
    const ok = await run(
      'create',
      async () => {
        created = await api.post('/api/users', {
          username: form.username.trim(),
          email: form.email.trim(),
          firstName: form.firstName.trim(),
          lastName: form.lastName.trim(),
          role: form.role,
          clientId: form.role === 'CLIENT' ? form.clientId : null,
          temporaryPassword: form.temporaryPassword || null
        });
      },
      'Compte créé.',
      describeError
    );
    if (ok) {
      setSecret(
        created?.temporaryPassword
          ? { username: created.user.username, password: created.temporaryPassword }
          : null
      );
      closeForm();
      usersApi.refetch();
    }
  }

  async function toggleEnabled(user) {
    const next = !user.enabled;
    if (!next && !window.confirm(`Désactiver le compte de ${user.firstName} ${user.lastName} ?`)) return;
    const ok = await run(
      `enabled-${user.id}`,
      () => api.put(`/api/users/${user.id}/enabled`, { enabled: next }),
      next ? 'Compte réactivé.' : 'Compte désactivé.',
      describeError
    );
    if (ok) usersApi.refetch();
  }

  async function resetPassword(user) {
    if (!window.confirm(`Générer un nouveau mot de passe temporaire pour ${user.firstName} ${user.lastName} ?`)) return;
    let result = null;
    const ok = await run(
      `reset-${user.id}`,
      async () => {
        result = await api.post(`/api/users/${user.id}/reset-password`);
      },
      'Mot de passe réinitialisé.',
      describeError
    );
    if (ok && result?.temporaryPassword) {
      setSecret({ username: user.username, password: result.temporaryPassword });
    }
  }

  async function copySecret() {
    try {
      await navigator.clipboard.writeText(secret.password);
    } catch {
      // Presse-papiers indisponible : l'administrateur peut recopier le mot de passe à la main.
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Administration"
        title="Utilisateurs du cabinet"
        description="Créez les comptes des avocats, secrétaires et clients, et gérez leur accès."
        action={
          !showForm ? (
            <button
              type="button"
              className={primaryButton}
              onClick={() => {
                clearFeedback();
                setSecret(null);
                setShowForm(true);
              }}
            >
              Nouvel utilisateur
            </button>
          ) : null
        }
      />

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

      {secret ? (
        <div className="rounded-xl border border-amber-300 bg-amber-50 p-4">
          <div className="text-sm font-semibold text-amber-900">Mot de passe temporaire de « {secret.username} »</div>
          <div className="mt-2 flex flex-wrap items-center gap-3">
            <code className="rounded-lg bg-white px-3 py-2 text-base font-semibold tracking-wide text-slate-900">
              {secret.password}
            </code>
            <button type="button" className={secondaryButton} onClick={copySecret}>
              Copier
            </button>
            <button type="button" className={secondaryButton} onClick={() => setSecret(null)}>
              J’ai noté le mot de passe
            </button>
          </div>
          <p className="mt-2 text-xs text-amber-800">
            Il n’est affiché qu’une seule fois. L’utilisateur devra le changer à sa première connexion.
          </p>
        </div>
      ) : null}

      {showForm ? (
        <SectionCard title="Nouvel utilisateur">
          <form onSubmit={handleCreate} noValidate className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2">
              <FormField label="Rôle" htmlFor="role" required>
                <select
                  id="role"
                  className={inputClass}
                  value={form.role}
                  onChange={(event) => updateField('role', event.target.value)}
                  disabled={busyKey === 'create'}
                >
                  {Object.entries(ROLES).map(([value, display]) => (
                    <option key={value} value={value}>
                      {display.label}
                    </option>
                  ))}
                </select>
              </FormField>
              <FormField label="Identifiant de connexion" htmlFor="username" required error={fieldErrors.username}>
                <input
                  id="username"
                  className={inputClass}
                  value={form.username}
                  onChange={(event) => updateField('username', event.target.value)}
                  disabled={busyKey === 'create'}
                  autoComplete="off"
                />
              </FormField>
              <FormField label="Prénom" htmlFor="firstName" required error={fieldErrors.firstName}>
                <input
                  id="firstName"
                  className={inputClass}
                  value={form.firstName}
                  onChange={(event) => updateField('firstName', event.target.value)}
                  disabled={busyKey === 'create'}
                />
              </FormField>
              <FormField label="Nom" htmlFor="lastName" required error={fieldErrors.lastName}>
                <input
                  id="lastName"
                  className={inputClass}
                  value={form.lastName}
                  onChange={(event) => updateField('lastName', event.target.value)}
                  disabled={busyKey === 'create'}
                />
              </FormField>
              <FormField label="Email" htmlFor="email" required error={fieldErrors.email}>
                <input
                  id="email"
                  type="email"
                  className={inputClass}
                  value={form.email}
                  onChange={(event) => updateField('email', event.target.value)}
                  disabled={busyKey === 'create'}
                />
              </FormField>
              <FormField
                label="Mot de passe temporaire"
                htmlFor="temporaryPassword"
                error={fieldErrors.temporaryPassword}
                hint="Facultatif : un mot de passe sécurisé est généré si vous laissez ce champ vide."
              >
                <input
                  id="temporaryPassword"
                  type="password"
                  className={inputClass}
                  value={form.temporaryPassword}
                  onChange={(event) => updateField('temporaryPassword', event.target.value)}
                  disabled={busyKey === 'create'}
                  autoComplete="new-password"
                />
              </FormField>
            </div>

            {form.role === 'CLIENT' ? (
              <FormField
                label="Fiche client liée"
                htmlFor="clientId"
                required
                error={fieldErrors.clientId}
                hint="Le client ne verra que ses propres dossiers, factures et consultations."
              >
                <select
                  id="clientId"
                  className={inputClass}
                  value={form.clientId}
                  onChange={(event) => updateField('clientId', event.target.value)}
                  disabled={busyKey === 'create'}
                >
                  <option value="">— Choisir un client —</option>
                  {clients.map((client) => (
                    <option key={client.id} value={client.id}>
                      {client.firstName} {client.lastName} ({client.email})
                    </option>
                  ))}
                </select>
              </FormField>
            ) : null}

            <div className="flex gap-3">
              <button type="submit" className={primaryButton} disabled={busyKey === 'create'}>
                {busyKey === 'create' ? 'Création…' : 'Créer le compte'}
              </button>
              <button type="button" className={secondaryButton} onClick={closeForm} disabled={busyKey === 'create'}>
                Annuler
              </button>
            </div>
          </form>
        </SectionCard>
      ) : null}

      <SectionCard
        title={`Comptes${Array.isArray(usersApi.data) ? ` (${usersApi.data.length})` : ''}`}
        action={
          <select
            aria-label="Filtrer par rôle"
            className={`${inputClass} max-w-[220px]`}
            value={roleFilter}
            onChange={(event) => setRoleFilter(event.target.value)}
          >
            <option value="ALL">Tous les rôles</option>
            {Object.entries(ROLES).map(([value, display]) => (
              <option key={value} value={value}>
                {display.label}
              </option>
            ))}
          </select>
        }
      >
        <AsyncState
          loading={usersApi.loading}
          error={usersApi.error}
          onRetry={usersApi.refetch}
          isEmpty={users.length === 0}
          emptyMessage="Aucun compte pour ce filtre."
          loadingMessage="Chargement des comptes…"
        >
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-4 font-medium">Nom</th>
                  <th className="py-2 pr-4 font-medium">Identifiant</th>
                  <th className="py-2 pr-4 font-medium">Email</th>
                  <th className="py-2 pr-4 font-medium">Rôle</th>
                  <th className="py-2 pr-4 font-medium">Statut</th>
                  <th className="py-2 font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => {
                  const role = ROLES[user.role] || { label: user.role || '—', tone: 'slate' };
                  const isMe = user.id === myId;
                  const busy = busyKey && busyKey.endsWith(user.id);

                  return (
                    <tr key={user.id} className="border-b border-slate-100 align-top last:border-0">
                      <td className="py-3 pr-4 font-medium text-slate-800">
                        {user.firstName} {user.lastName}
                        {user.clientId ? (
                          <div className="text-xs font-normal text-slate-500">
                            Fiche : {clientNames[user.clientId] || 'client inconnu'}
                          </div>
                        ) : null}
                      </td>
                      <td className="py-3 pr-4 text-slate-600">{user.username}</td>
                      <td className="py-3 pr-4 text-slate-600">{user.email}</td>
                      <td className="py-3 pr-4">
                        <StatusBadge tone={role.tone}>{role.label}</StatusBadge>
                      </td>
                      <td className="py-3 pr-4">
                        <StatusBadge tone={user.enabled ? 'emerald' : 'rose'}>{user.enabled ? 'Actif' : 'Désactivé'}</StatusBadge>
                      </td>
                      <td className="py-3">
                        <div className="flex flex-wrap gap-2">
                          <button
                            type="button"
                            className={user.enabled ? dangerButton : secondaryButton}
                            disabled={busy || (isMe && user.enabled)}
                            title={isMe ? 'Vous ne pouvez pas désactiver votre propre compte.' : undefined}
                            onClick={() => toggleEnabled(user)}
                          >
                            {user.enabled ? 'Désactiver' : 'Réactiver'}
                          </button>
                          <button type="button" className={secondaryButton} disabled={busy} onClick={() => resetPassword(user)}>
                            Réinitialiser le mot de passe
                          </button>
                        </div>
                      </td>
                    </tr>
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
