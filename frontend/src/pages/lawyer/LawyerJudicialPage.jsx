import { useMemo, useState } from 'react';
import PageHeader from '../../components/common/PageHeader';
import { primaryButton, secondaryButton } from '../../components/common/buttonStyles';
import JudicialCasesTab from '../../components/judicial/JudicialCasesTab';
import HearingsTab from '../../components/judicial/HearingsTab';
import CourtsTab from '../../components/judicial/CourtsTab';
import { keycloak } from '../../context/AuthContext';
import useAction from '../../hooks/useAction';
import useJudicialData from '../../hooks/useJudicialData';
import { api } from '../../services/api';
import { describeJudicialError } from '../../utils/judicial';

const TABS = [
  { key: 'cases', label: 'Procédures' },
  { key: 'hearings', label: 'Audiences' },
  { key: 'courts', label: 'Tribunaux' }
];

export default function LawyerJudicialPage() {
  // Seuls l'administrateur et l'avocat peuvent écrire ; la secrétaire (si elle accède à la page) lit seulement.
  const canWrite = Boolean(keycloak.hasRealmRole?.('ADMIN') || keycloak.hasRealmRole?.('LAWYER'));
  const lawFirmId = keycloak.tokenParsed?.lawFirmId || keycloak.tokenParsed?.lawfirmid;

  const data = useJudicialData();
  const action = useAction();
  const [tab, setTab] = useState('cases');

  // Recalculé à chaque rechargement : « à venir » et « prochaine audience » restent justes.
  const now = useMemo(() => new Date(), [data.details]); // eslint-disable-line react-hooks/exhaustive-deps

  async function changeCaseStatus(judicialCase, status) {
    if (status === 'CLOSED' && !window.confirm(`Clôturer la procédure ${judicialCase.courtCaseNumber} ? Cette action est définitive.`)) return;
    const ok = await action.run(
      `case-${judicialCase.id}`,
      () => api.patch(`/api/judicial-cases/${judicialCase.id}/status?status=${status}`),
      'Statut de la procédure mis à jour.',
      describeJudicialError
    );
    if (ok) data.refetch();
  }

  async function changeHearingStatus(hearing, status) {
    if (status === 'CANCELLED' && !window.confirm('Annuler cette audience ? Cette action est définitive.')) return;
    const ok = await action.run(
      `hearing-${hearing.id}`,
      () => api.patch(`/api/hearings/${hearing.id}/status?status=${status}`),
      "Statut de l'audience mis à jour.",
      describeJudicialError
    );
    if (ok) data.refetch();
  }

  const casesById = useMemo(() => {
    const list = data.cases?.ok && Array.isArray(data.cases.value) ? data.cases.value : [];
    return new Map(list.map((item) => [item.id, item]));
  }, [data.cases]);

  const { feedback } = action;

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow={keycloak.hasRealmRole?.('ADMIN') ? 'Administration' : 'Espace avocat'}
        title="Judiciaire"
        description="Suivez vos procédures, leurs audiences et les tribunaux concernés."
        action={
          <button type="button" className={secondaryButton} onClick={data.refetch}>
            Actualiser
          </button>
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

      <div className="flex flex-wrap gap-2">
        {TABS.map((item) => (
          <button key={item.key} type="button" onClick={() => setTab(item.key)} className={tab === item.key ? primaryButton : secondaryButton}>
            {item.label}
          </button>
        ))}
      </div>

      {data.loading ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500">Chargement des données judiciaires…</div>
      ) : (
        <>
          {tab === 'cases' ? (
            <JudicialCasesTab
              judicialCases={data.judicialCases}
              details={data.details}
              courts={data.courts}
              cases={data.cases}
              canWrite={canWrite}
              lawFirmId={lawFirmId}
              action={action}
              onChanged={data.refetch}
              onCaseStatus={changeCaseStatus}
              onHearingStatus={changeHearingStatus}
              now={now}
            />
          ) : null}
          {tab === 'hearings' ? (
            <HearingsTab
              judicialCases={data.judicialCases}
              details={data.details}
              casesById={casesById}
              canWrite={canWrite}
              action={action}
              onHearingStatus={changeHearingStatus}
              now={now}
            />
          ) : null}
          {tab === 'courts' ? (
            <CourtsTab courts={data.courts} canWrite={canWrite} action={action} onChanged={data.refetch} />
          ) : null}
        </>
      )}
    </div>
  );
}
