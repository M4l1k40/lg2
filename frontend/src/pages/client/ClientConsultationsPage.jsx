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
import { keycloak } from '../../context/AuthContext';
import {
  CANCELLABLE_STATUSES,
  describeConsultationError,
  sortByCreatedAtDesc
} from '../../constants/consultationStatus';

const EMPTY_FORM = { subject: '', description: '' };

function validate(form) {
  const errors = {};
  const subject = form.subject.trim();
  const description = form.description.trim();
  if (subject.length < 3) errors.subject = 'Le sujet doit contenir au moins 3 caractères.';
  if (subject.length > 255) errors.subject = 'Le sujet est limité à 255 caractères.';
  if (description.length < 10) errors.description = 'Décrivez votre demande en au moins 10 caractères.';
  if (description.length > 4000) errors.description = 'La description est limitée à 4000 caractères.';
  return errors;
}

export default function ClientConsultationsPage() {
  const { data, loading, error, refetch } = useApi('/api/consultations');
  const { busyKey, feedback, run, clearFeedback } = useAction();

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState({});

  // Le claim clientId est posé par un administrateur : sans lui, le compte n'est lié à aucune fiche client.
  const linkedToClientRecord = Boolean(keycloak.tokenParsed?.clientId);

  const consultations = useMemo(() => sortByCreatedAtDesc(Array.isArray(data) ? data : []), [data]);

  function updateField(name, value) {
    setForm((previous) => ({ ...previous, [name]: value }));
    setFieldErrors((previous) => ({ ...previous, [name]: undefined }));
  }

  function closeForm() {
    setShowForm(false);
    setForm(EMPTY_FORM);
    setFieldErrors({});
  }

  async function handleSubmit(event) {
    event.preventDefault();

    const errors = validate(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    // Le backend déduit le client depuis le token : on n'envoie pas de clientId.
    const created = await run(
      'create',
      () => api.post('/api/consultations', { subject: form.subject.trim(), description: form.description.trim() }),
      'Votre demande a été envoyée. Le cabinet va la traiter.',
      describeConsultationError
    );
    if (created) {
      closeForm();
      refetch();
    }
  }

  async function handleCancel(consultation) {
    if (!window.confirm('Annuler cette demande de consultation ?')) return;
    const cancelled = await run(
      `cancel-${consultation.id}`,
      () => api.put(`/api/consultations/${consultation.id}/cancel`),
      'La demande a été annulée.',
      describeConsultationError
    );
    if (cancelled) refetch();
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Espace client"
        title="Mes consultations"
        description="Posez une question juridique au cabinet et suivez la réponse de votre avocat."
        action={
          !showForm ? (
            <button
              type="button"
              className={primaryButton}
              disabled={!linkedToClientRecord}
              onClick={() => {
                clearFeedback();
                setShowForm(true);
              }}
            >
              Nouvelle demande
            </button>
          ) : null
        }
      />

      {!linkedToClientRecord ? (
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
          Votre compte n’est pas encore lié à une fiche client. Contactez le cabinet pour pouvoir envoyer une demande.
        </div>
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
        <SectionCard title="Nouvelle demande de consultation">
          <form onSubmit={handleSubmit} noValidate className="space-y-4">
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
            <FormField
              label="Votre question"
              htmlFor="description"
              required
              error={fieldErrors.description}
              hint="Donnez le plus de détails possible (dates, personnes concernées, documents). 10 à 4000 caractères."
            >
              <textarea
                id="description"
                rows={5}
                maxLength={4000}
                className={inputClass}
                value={form.description}
                onChange={(event) => updateField('description', event.target.value)}
                disabled={busyKey === 'create'}
              />
            </FormField>
            <div className="flex gap-3">
              <button type="submit" className={primaryButton} disabled={busyKey === 'create'}>
                {busyKey === 'create' ? 'Envoi…' : 'Envoyer la demande'}
              </button>
              <button type="button" className={secondaryButton} onClick={closeForm} disabled={busyKey === 'create'}>
                Annuler
              </button>
            </div>
          </form>
        </SectionCard>
      ) : null}

      <SectionCard title={`Mes demandes${Array.isArray(data) ? ` (${data.length})` : ''}`}>
        <AsyncState
          loading={loading}
          error={error}
          onRetry={refetch}
          isEmpty={consultations.length === 0}
          emptyMessage="Vous n’avez encore envoyé aucune demande de consultation."
          loadingMessage="Chargement de vos consultations…"
        >
          <div className="space-y-4">
            {consultations.map((consultation) => (
              <ConsultationCard key={consultation.id} consultation={consultation}>
                {CANCELLABLE_STATUSES.includes(consultation.status) ? (
                  <button
                    type="button"
                    className={dangerButton}
                    disabled={busyKey === `cancel-${consultation.id}`}
                    onClick={() => handleCancel(consultation)}
                  >
                    Annuler la demande
                  </button>
                ) : null}
              </ConsultationCard>
            ))}
          </div>
        </AsyncState>
      </SectionCard>
    </div>
  );
}
