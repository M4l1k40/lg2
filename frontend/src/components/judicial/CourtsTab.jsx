import { Fragment, useState } from 'react';
import SectionCard from '../common/SectionCard';
import AsyncState from '../common/AsyncState';
import FormField, { inputClass } from '../common/FormField';
import { primaryButton, secondaryButton } from '../common/buttonStyles';
import { api } from '../../services/api';
import { COURT_TYPE_SUGGESTIONS, JUDGE_ROLE_SUGGESTIONS } from '../../constants/judicialStatus';
import {
  buildCourtPayload,
  buildJudgePayload,
  describeJudicialError,
  emptyCourtForm,
  emptyJudgeForm,
  judgeName,
  validateCourt,
  validateJudge
} from '../../utils/judicial';

export default function CourtsTab({ courts, canWrite, action, onChanged }) {
  const { busyKey, run, clearFeedback } = action;
  const [mode, setMode] = useState(null); // 'court' | 'judge' | null
  const [courtForm, setCourtForm] = useState(emptyCourtForm());
  const [judgeForm, setJudgeForm] = useState(emptyJudgeForm());
  const [errors, setErrors] = useState({});
  const [expandedId, setExpandedId] = useState(null);

  const list = courts.ok && Array.isArray(courts.value) ? courts.value : [];

  function open(nextMode, courtId = '') {
    clearFeedback();
    setErrors({});
    setCourtForm(emptyCourtForm());
    setJudgeForm({ ...emptyJudgeForm(), courtId });
    setMode(nextMode);
  }

  async function submitCourt(event) {
    event.preventDefault();
    const found = validateCourt(courtForm);
    if (Object.keys(found).length > 0) return setErrors(found);
    const ok = await run('court', () => api.post('/api/courts', buildCourtPayload(courtForm)), 'Tribunal créé.', describeJudicialError);
    if (ok) {
      setMode(null);
      onChanged();
    }
  }

  async function submitJudge(event) {
    event.preventDefault();
    const found = validateJudge(judgeForm);
    if (Object.keys(found).length > 0) return setErrors(found);
    const ok = await run('judge', () => api.post('/api/judges', buildJudgePayload(judgeForm)), 'Juge ajouté.', describeJudicialError);
    if (ok) {
      setMode(null);
      onChanged();
    }
  }

  const setCourt = (field, value) => {
    setCourtForm((previous) => ({ ...previous, [field]: value }));
    setErrors((previous) => ({ ...previous, [field]: undefined }));
  };
  const setJudge = (field, value) => {
    setJudgeForm((previous) => ({ ...previous, [field]: value }));
    setErrors((previous) => ({ ...previous, [field]: undefined }));
  };

  return (
    <div className="space-y-6">
      {mode === 'court' ? (
        <SectionCard title="Nouveau tribunal">
          <form onSubmit={submitCourt} className="grid gap-4 md:grid-cols-2" noValidate>
            <FormField label="Nom" htmlFor="court-name" required error={errors.name}>
              <input id="court-name" className={inputClass} value={courtForm.name} onChange={(event) => setCourt('name', event.target.value)} />
            </FormField>
            <FormField label="Type" htmlFor="court-type" required error={errors.type}>
              <input id="court-type" list="court-types" className={inputClass} value={courtForm.type} onChange={(event) => setCourt('type', event.target.value)} />
              <datalist id="court-types">{COURT_TYPE_SUGGESTIONS.map((item) => <option key={item} value={item} />)}</datalist>
            </FormField>
            <FormField label="Ville" htmlFor="court-city" required error={errors.city}>
              <input id="court-city" className={inputClass} value={courtForm.city} onChange={(event) => setCourt('city', event.target.value)} />
            </FormField>
            <FormField label="Adresse" htmlFor="court-address">
              <input id="court-address" className={inputClass} value={courtForm.address} onChange={(event) => setCourt('address', event.target.value)} />
            </FormField>
            <div className="flex gap-2 md:col-span-2">
              <button type="submit" className={primaryButton} disabled={busyKey === 'court'}>{busyKey === 'court' ? 'Enregistrement…' : 'Créer le tribunal'}</button>
              <button type="button" className={secondaryButton} onClick={() => setMode(null)}>Annuler</button>
            </div>
          </form>
        </SectionCard>
      ) : null}

      {mode === 'judge' ? (
        <SectionCard title="Nouveau juge">
          <form onSubmit={submitJudge} className="grid gap-4 md:grid-cols-2" noValidate>
            <FormField label="Tribunal" htmlFor="judge-court" required error={errors.courtId}>
              <select id="judge-court" className={inputClass} value={judgeForm.courtId} onChange={(event) => setJudge('courtId', event.target.value)}>
                <option value="">— Choisir un tribunal —</option>
                {list.map((court) => <option key={court.id} value={court.id}>{court.name} — {court.city}</option>)}
              </select>
            </FormField>
            <FormField label="Fonction" htmlFor="judge-role" required error={errors.role}>
              <input id="judge-role" list="judge-roles" className={inputClass} value={judgeForm.role} onChange={(event) => setJudge('role', event.target.value)} />
              <datalist id="judge-roles">{JUDGE_ROLE_SUGGESTIONS.map((item) => <option key={item} value={item} />)}</datalist>
            </FormField>
            <FormField label="Prénom" htmlFor="judge-first" required error={errors.firstName}>
              <input id="judge-first" className={inputClass} value={judgeForm.firstName} onChange={(event) => setJudge('firstName', event.target.value)} />
            </FormField>
            <FormField label="Nom" htmlFor="judge-last" required error={errors.lastName}>
              <input id="judge-last" className={inputClass} value={judgeForm.lastName} onChange={(event) => setJudge('lastName', event.target.value)} />
            </FormField>
            <div className="flex gap-2 md:col-span-2">
              <button type="submit" className={primaryButton} disabled={busyKey === 'judge'}>{busyKey === 'judge' ? 'Enregistrement…' : 'Ajouter le juge'}</button>
              <button type="button" className={secondaryButton} onClick={() => setMode(null)}>Annuler</button>
            </div>
          </form>
        </SectionCard>
      ) : null}

      <SectionCard
        title="Tribunaux et juges"
        action={canWrite ? (
          <div className="flex gap-2">
            <button type="button" className={secondaryButton} onClick={() => open('court')}>Nouveau tribunal</button>
            <button type="button" className={secondaryButton} onClick={() => open('judge')}>Nouveau juge</button>
          </div>
        ) : null}
      >
        <p className="mb-4 text-sm text-slate-500">
          Les tribunaux et les juges forment une liste commune à tous les cabinets : ce que vous ajoutez ici est visible par les autres cabinets.
        </p>
        <AsyncState
          loading={false}
          error={courts.ok ? null : courts.error}
          onRetry={onChanged}
          isEmpty={list.length === 0}
          emptyMessage="Aucun tribunal enregistré."
        >
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-4 font-medium">Tribunal</th>
                  <th className="py-2 pr-4 font-medium">Type</th>
                  <th className="py-2 pr-4 font-medium">Ville</th>
                  <th className="py-2 pr-4 font-medium">Juges</th>
                  <th className="py-2 font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {list.map((court) => {
                  const judges = Array.isArray(court.judges) ? court.judges : [];
                  const expanded = expandedId === court.id;
                  return (
                    <Fragment key={court.id}>
                      <tr className="border-b border-slate-100 align-top last:border-0">
                        <td className="py-3 pr-4 font-medium text-slate-800">{court.name}</td>
                        <td className="py-3 pr-4 text-slate-600">{court.type}</td>
                        <td className="py-3 pr-4 text-slate-600">{court.city}</td>
                        <td className="py-3 pr-4 text-slate-600">{judges.length}</td>
                        <td className="py-3">
                          <div className="flex flex-wrap gap-2">
                            <button type="button" className={secondaryButton} onClick={() => setExpandedId(expanded ? null : court.id)}>
                              {expanded ? 'Masquer' : 'Détails'}
                            </button>
                            {canWrite ? (
                              <button type="button" className={secondaryButton} onClick={() => open('judge', court.id)}>Ajouter un juge</button>
                            ) : null}
                          </div>
                        </td>
                      </tr>
                      {expanded ? (
                        <tr className="border-b border-slate-100 bg-slate-50">
                          <td colSpan={5} className="px-3 py-3 text-sm text-slate-600">
                            <div className="mb-2"><span className="text-xs uppercase text-slate-500">Adresse : </span>{court.address || '—'}</div>
                            {judges.length === 0 ? 'Aucun juge enregistré.' : (
                              <ul className="space-y-1">
                                {judges.map((judge) => (
                                  <li key={judge.id}>{judgeName(judge)} <span className="text-slate-500">— {judge.role}</span></li>
                                ))}
                              </ul>
                            )}
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
