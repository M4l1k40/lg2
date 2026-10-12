import { useMemo, useState } from 'react';
import PageHeader from '../../components/common/PageHeader';
import SectionCard from '../../components/common/SectionCard';
import AsyncState from '../../components/common/AsyncState';
import FormField, { inputClass } from '../../components/common/FormField';
import ConsultationCard from '../../components/consultations/ConsultationCard';
import { dangerButton, primaryButton, secondaryButton } from '../../components/common/buttonStyles';
import useApi from '../../hooks/useApi';
import useAction from '../../hooks/useAction';
import { api } from '../../services/api';
import {
  CANCELLABLE_STATUSES,
  CONSULTATION_STATUS_OPTIONS,
  UUID_REGEX,
  describeConsultationError,
  sortByCreatedAtDesc
} from '../../constants/consultationStatus';

const EMPTY_FORM = { clientId: '', subject: '', description: '' };

function AssignForm({ busy, lawyers, directoryAvailable, knownLawyerIds, onSubmit }) {
  const [lawyerId, setLawyerId] = useState('');
  const [error, setError] = useState(null);

  async function handleSubmit(event) {
    event.preventDefault();
    const value = lawyerId.trim();
    if (directoryAvailable ? !value : !UUID_REGEX.test(value)) {
      setError(directoryAvailable ? 'Choisissez un avocat.' : "L'identifiant de l'avocat doit être un UUID valide.");
      return;
    }
    setError(null);
    const assigned = await onSubmit(value);
    if (assigned) setLawyerId('');
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex w-full flex-col gap-2 sm:flex-row sm:items-start">
      <div className="flex-1">
        {directoryAvailable ? (
          <select
            aria-label="Avocat à assigner"
            className={inputClass}
            value={lawyerId}
            onChange={(event) => {
              setLawyerId(event.target.value);
              setError(null);
            }}
            disabled={busy}
          >
            <option value="">— Choisir un avocat —</option>
            {lawyers.map((lawyer) => (
              <option key={lawyer.id} value={lawyer.id}>
                {lawyer.firstName} {lawyer.lastName}
              </option>
            ))}
          </select>
        ) : (
          <>
            <input
              list="known-lawyers"
              aria-label="Identifiant de l'avocat"
              placeholder="Annuaire indisponible : identifiant (UUID) de l'avocat"
              className={inputClass}
              value={lawyerId}
              onChange={(event) => {
                setLawyerId(event.target.value);
                setError(null);
              }}
              disabled={busy}
            />
            <datalist id="known-lawyers">
              {knownLawyerIds.map((id) => (
                <option key={id} value={id} />
              ))}
            </datalist>
          </>
        )}
        {error ? <p className="mt-1 text-xs text-rose-600">{error}</p> : null}
      </div>
      <button type="submit" className={primaryButton} disabled={busy}>
        Assigner
      </button>
    </form>
  );
}

/** Vue du personnel administratif (secrétaire et admin) : toutes les consultations du cabinet. */
export default function OfficeConsultationsPage() {
  const consultationsApi = useApi('/api/consultations');
  const clientsApi = useApi('/api/clients');
  const lawyersApi = useApi('/api/users/lawyers');
  const { busyKey, feedback, run, clearFeedback } = useAction();

  const [statusFilter, setStatusFilter] = useState('ALL');
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState({});

  const allConsultations = useMemo(
    () => sortByCreatedAtDesc(Array.isArray(consultationsApi.data) ? consultationsApi.data : []),
    [consultationsApi.data]
  );

  const consultations = useMemo(
    () => (statusFilter === 'ALL' ? allConsultations : allConsultations.filter((item) => item.status === statusFilter)),
    [allConsultations, statusFilter]
  );

  const clients = useMemo(() => (Array.isArray(clientsApi.data) ? clientsApi.data : []), [clientsApi.data]);

  const clientNames = useMemo(() => {
    const names = {};
    clients.forEach((client) => {
      names[client.id] = `${client.firstName} ${client.lastName}`;
    });
    return names;
  }, [clients]);

  const lawyers = useMemo(() => (Array.isArray(lawyersApi.data) ? lawyersApi.data : []), [lawyersApi.data]);
  const directoryAvailable = !lawyersApi.loading && !lawyersApi.error && lawyers.length > 0;

  const lawyerNames = useMemo(() => {
    const names = {};
    lawyers.forEach((lawyer) => {
      names[lawyer.id] = `${lawyer.firstName} ${lawyer.lastName}`;
    });
    return names;
  }, [lawyers]);

  const knownLawyerIds = useMemo(
    () => [...new Set(allConsultations.map((item) => item.lawyerId).filter(Boolean))],
    [allConsultations]
  );

  function updateField(name, value) {
    setForm((previous) => ({ ...previous, [name]: value }));
    setFieldErrors((previous) => ({ ...previous, [name]: undefined }));
  }

  function closeForm() {
    setShowForm(false);
    setForm(EMPTY_FORM);
    setFieldErrors({});
  }

  async function handleCreate(event) {
    event.preventDefault();

    const errors = {};
    if (!form.clientId) errors.clientId = 'Choisissez un client.';
    if (form.subject.trim().length < 3) errors.subject = 'Le sujet doit contenir au moins 3 caractères.';
    if (form.description.trim().length < 10) errors.description = 'La description doit contenir au moins 10 caractères.';
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    const created = await run(
      'create',
      () =>
        api.post('/api/consultations', {
          clientId: form.clientId,
          subject: form.subject.trim(),
          description: form.description.trim()
        }),
      'Consultation créée.',
      describeConsultationError
    );
    if (created) {
      closeForm();
      consultationsApi.refetch();
    }
  }

  async function act(consultation, verb, request, successMessage) {
    const ok = await run(`${verb}-${consultation.id}`, request, successMessage, describeConsultationError);
    if (ok) consultationsApi.refetch();
    return ok;
  }

  const assign = (consultation, lawyerId) =>
    act(consultation, 'assign', () => api.put(`/api/consultations/${consultation.id}/assign`, { lawyerId }),
      'Consultation assignée à l’avocat.');
  const cancel = (consultation) => {
    if (!window.confirm('Annuler cette consultation ?')) return Promise.resolve(false);
    return act(consultation, 'cancel', () => api.put(`/api/consultations/${consultation.id}/cancel`), 'Consultation annulée.');
  };
  const close = (consultation) =>
    act(consultation, 'close', () => api.put(`/api/consultations/${consultation.id}/close`), 'Consultation clôturée.');

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Secrétariat"
        title="Consultations"
        description="Recevez les demandes des clients, assignez-les à un avocat et suivez leur avancement."
        action={
          !showForm ? (
            <button
              type="button"
              className={primaryButton}
              onClick={() => {
                clearFeedback();
                setShowForm(true);
              }}
            >
              Nouvelle consultation
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

      {showForm ? (
        <SectionCard title="Nouvelle consultation pour un client">
          <form onSubmit={handleCreate} noValidate className="space-y-4">
            <FormField label="Client" htmlFor="clientId" required error={fieldErrors.clientId}>
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
            <FormField label="Sujet" htmlFor="subject" required error={fieldErrors.subject}>
              <input
                id="subject"
                className={inputClass}
                value={form.subject}
                onChange={(event) => updateField('subject', event.target.value)}
                disabled={busyKey === 'create'}
                maxLength={255}
              />
            </FormField>
            <FormField label="Description" htmlFor="description" required error={fieldErrors.description}>
              <textarea
                id="description"
                rows={4}
                maxLength={4000}
                className={inputClass}
                value={form.description}
                onChange={(event) => updateField('description', event.target.value)}
                disabled={busyKey === 'create'}
              />
            </FormField>
            <div className="flex gap-3">
              <button type="submit" className={primaryButton} disabled={busyKey === 'create'}>
                {busyKey === 'create' ? 'Enregistrement…' : 'Créer la consultation'}
              </button>
              <button type="button" className={secondaryButton} onClick={closeForm} disabled={busyKey === 'create'}>
                Annuler
              </button>
            </div>
          </form>
        </SectionCard>
      ) : null}

      <SectionCard
        title={`Toutes les consultations${Array.isArray(consultationsApi.data) ? ` (${consultationsApi.data.length})` : ''}`}
        action={
          <select
            aria-label="Filtrer par statut"
            className={`${inputClass} max-w-[220px]`}
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
          >
            <option value="ALL">Tous les statuts</option>
            {CONSULTATION_STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        }
      >
        <AsyncState
          loading={consultationsApi.loading}
          error={consultationsApi.error}
          onRetry={consultationsApi.refetch}
          isEmpty={consultations.length === 0}
          emptyMessage={statusFilter === 'ALL' ? 'Aucune consultation pour le moment.' : 'Aucune consultation avec ce statut.'}
          loadingMessage="Chargement des consultations…"
        >
          <div className="space-y-4">
            {consultations.map((consultation) => {
              const busy = busyKey && busyKey.endsWith(consultation.id);

              return (
                <ConsultationCard
                  key={consultation.id}
                  consultation={consultation}
                  clientName={clientNames[consultation.clientId] || 'Client inconnu'}
                  lawyerLabel={consultation.lawyerId ? lawyerNames[consultation.lawyerId] || `${consultation.lawyerId.slice(0, 8)}…` : null}
                >
                  {consultation.status === 'PENDING' ? (
                    <AssignForm
                      busy={busy}
                      lawyers={lawyers}
                      directoryAvailable={directoryAvailable}
                      knownLawyerIds={knownLawyerIds}
                      onSubmit={(lawyerId) => assign(consultation, lawyerId)}
                    />
                  ) : null}

                  {consultation.status === 'ANSWERED' ? (
                    <button type="button" className={primaryButton} disabled={busy} onClick={() => close(consultation)}>
                      Clôturer
                    </button>
                  ) : null}

                  {CANCELLABLE_STATUSES.includes(consultation.status) ? (
                    <button type="button" className={dangerButton} disabled={busy} onClick={() => cancel(consultation)}>
                      Annuler
                    </button>
                  ) : null}
                </ConsultationCard>
              );
            })}
          </div>
        </AsyncState>
      </SectionCard>
    </div>
  );
}
