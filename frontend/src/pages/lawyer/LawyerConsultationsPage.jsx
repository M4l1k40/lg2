import { useMemo, useState } from 'react';
import PageHeader from '../../components/common/PageHeader';
import SectionCard from '../../components/common/SectionCard';
import AsyncState from '../../components/common/AsyncState';
import FormField, { inputClass } from '../../components/common/FormField';
import ConsultationCard from '../../components/consultations/ConsultationCard';
import { primaryButton, secondaryButton } from '../../components/common/buttonStyles';
import useApi from '../../hooks/useApi';
import useAction from '../../hooks/useAction';
import { api } from '../../services/api';
import { keycloak } from '../../context/AuthContext';
import { describeConsultationError, sortByCreatedAtDesc } from '../../constants/consultationStatus';

const TABS = [
  { key: 'toTake', label: 'À prendre en charge' },
  { key: 'mine', label: 'Mes consultations' },
  { key: 'done', label: 'Terminées' }
];

function AnswerForm({ busy, onSubmit }) {
  const [answer, setAnswer] = useState('');
  const [error, setError] = useState(null);

  async function handleSubmit(event) {
    event.preventDefault();
    const text = answer.trim();
    if (text.length < 5) {
      setError('La réponse doit contenir au moins 5 caractères.');
      return;
    }
    setError(null);
    const sent = await onSubmit(text);
    if (sent) setAnswer('');
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="w-full space-y-3">
      <FormField label="Votre réponse au client" htmlFor="answer" required error={error}>
        <textarea
          id="answer"
          rows={4}
          maxLength={4000}
          className={inputClass}
          value={answer}
          onChange={(event) => {
            setAnswer(event.target.value);
            setError(null);
          }}
          disabled={busy}
        />
      </FormField>
      <button type="submit" className={primaryButton} disabled={busy}>
        {busy ? 'Envoi…' : 'Envoyer la réponse'}
      </button>
    </form>
  );
}

export default function LawyerConsultationsPage() {
  const consultationsApi = useApi('/api/consultations');
  const clientsApi = useApi('/api/clients');
  const { busyKey, feedback, run } = useAction();
  const [tab, setTab] = useState('toTake');

  // Identifiant Keycloak de l'avocat connecté : c'est lui qui est enregistré comme avocat assigné.
  const myId = keycloak.tokenParsed?.sub;

  const consultations = useMemo(
    () => sortByCreatedAtDesc(Array.isArray(consultationsApi.data) ? consultationsApi.data : []),
    [consultationsApi.data]
  );

  const clientNames = useMemo(() => {
    const names = {};
    (Array.isArray(clientsApi.data) ? clientsApi.data : []).forEach((client) => {
      names[client.id] = `${client.firstName} ${client.lastName}`;
    });
    return names;
  }, [clientsApi.data]);

  const groups = useMemo(
    () => ({
      toTake: consultations.filter((item) => item.status === 'PENDING'),
      mine: consultations.filter(
        (item) => item.lawyerId === myId && ['ASSIGNED', 'IN_PROGRESS', 'ANSWERED'].includes(item.status)
      ),
      done: consultations.filter((item) => item.lawyerId === myId && ['CLOSED', 'CANCELLED'].includes(item.status))
    }),
    [consultations, myId]
  );

  const visible = groups[tab];

  async function act(consultation, verb, request, successMessage) {
    const ok = await run(`${verb}-${consultation.id}`, request, successMessage, describeConsultationError);
    if (ok) consultationsApi.refetch();
    return ok;
  }

  const takeCharge = (consultation) =>
    act(consultation, 'assign', () => api.put(`/api/consultations/${consultation.id}/assign`, { lawyerId: myId }),
      'Consultation prise en charge.');
  const start = (consultation) =>
    act(consultation, 'start', () => api.put(`/api/consultations/${consultation.id}/start`), 'Consultation démarrée.');
  const answer = (consultation, text) =>
    act(consultation, 'answer', () => api.put(`/api/consultations/${consultation.id}/answer`, { answer: text }),
      'Votre réponse a été envoyée au client.');
  const close = (consultation) =>
    act(consultation, 'close', () => api.put(`/api/consultations/${consultation.id}/close`), 'Consultation clôturée.');

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Espace avocat"
        title="Consultations"
        description="Prenez en charge les demandes des clients, répondez-y, puis clôturez-les."
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

      <SectionCard
        title="Demandes"
        action={
          <div className="flex flex-wrap gap-2">
            {TABS.map((item) => (
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
          loading={consultationsApi.loading}
          error={consultationsApi.error}
          onRetry={consultationsApi.refetch}
          isEmpty={visible.length === 0}
          emptyMessage="Aucune consultation dans cette liste."
          loadingMessage="Chargement des consultations…"
        >
          <div className="space-y-4">
            {visible.map((consultation) => {
              const isMine = consultation.lawyerId === myId;
              const busy = busyKey && busyKey.endsWith(consultation.id);

              return (
                <ConsultationCard
                  key={consultation.id}
                  consultation={consultation}
                  clientName={clientNames[consultation.clientId] || 'Client inconnu'}
                >
                  {consultation.status === 'PENDING' ? (
                    <button type="button" className={primaryButton} disabled={busy} onClick={() => takeCharge(consultation)}>
                      Prendre en charge
                    </button>
                  ) : null}

                  {isMine && consultation.status === 'ASSIGNED' ? (
                    <button type="button" className={primaryButton} disabled={busy} onClick={() => start(consultation)}>
                      Démarrer
                    </button>
                  ) : null}

                  {isMine && consultation.status === 'IN_PROGRESS' ? (
                    <AnswerForm busy={busy} onSubmit={(text) => answer(consultation, text)} />
                  ) : null}

                  {isMine && consultation.status === 'ANSWERED' ? (
                    <button type="button" className={primaryButton} disabled={busy} onClick={() => close(consultation)}>
                      Clôturer
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
