import { Fragment, useMemo, useState } from 'react';
import SectionCard from '../common/SectionCard';
import AsyncState from '../common/AsyncState';
import StatusBadge from '../common/StatusBadge';
import FormField, { inputClass } from '../common/FormField';
import { dangerButton, primaryButton, secondaryButton } from '../common/buttonStyles';
import { api } from '../../services/api';
import {
  CASE_ACTION_LABELS,
  HEARING_ACTION_LABELS,
  HEARING_TYPES,
  PROCEDURE_TYPES,
  getCaseStatusDisplay,
  getEventLabel,
  getHearingStatusDisplay,
  getHearingTypeLabel,
  getProcedureLabel
} from '../../constants/judicialStatus';
import {
  buildHearingPayload,
  buildJudicialCasePayload,
  caseTransitions,
  courtLabel,
  describeJudicialError,
  emptyHearingForm,
  emptyJudicialCaseForm,
  formatDateTime,
  formatDay,
  hearingTransitions,
  judgeName,
  nextHearing,
  toInputDateTime,
  validateHearing,
  validateJudicialCase
} from '../../utils/judicial';

const EMPTY_ARRAY = [];

export default function JudicialCasesTab({
  judicialCases, details, courts, cases, canWrite, lawFirmId, action, onChanged, onCaseStatus, onHearingStatus, now
}) {
  const { busyKey, run, clearFeedback } = action;
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState(emptyJudicialCaseForm());
  const [errors, setErrors] = useState({});
  const [expandedId, setExpandedId] = useState(null);
  const [hearingFormFor, setHearingFormFor] = useState(null);
  const [hearingForm, setHearingForm] = useState(emptyHearingForm());
  const [hearingErrors, setHearingErrors] = useState({});

  const list = judicialCases.ok && Array.isArray(judicialCases.value) ? judicialCases.value : EMPTY_ARRAY;
  const courtList = courts.ok && Array.isArray(courts.value) ? courts.value : EMPTY_ARRAY;
  const caseList = cases.ok && Array.isArray(cases.value) ? cases.value : EMPTY_ARRAY;
  const casesById = useMemo(() => new Map(caseList.map((item) => [item.id, item])), [caseList]);
  const courtsById = useMemo(() => new Map(courtList.map((item) => [item.id, item])), [courtList]);
  const judgesById = useMemo(
    () => new Map(courtList.flatMap((court) => (Array.isArray(court.judges) ? court.judges : []).map((judge) => [judge.id, judge]))),
    [courtList]
  );
  const judgesOfSelectedCourt = Array.isArray(courtsById.get(form.courtId)?.judges) ? courtsById.get(form.courtId).judges : [];

  function openForm() {
    clearFeedback();
    setForm(emptyJudicialCaseForm());
    setErrors({});
    setFormOpen(true);
  }

  function setField(field, value) {
    setForm((previous) => {
      const next = { ...previous, [field]: value };
      if (field === 'courtId') next.judgeId = '';
      return next;
    });
    setErrors((previous) => ({ ...previous, [field]: undefined }));
  }

  async function submit(event) {
    event.preventDefault();
    const found = validateJudicialCase(form);
    if (!lawFirmId) found.caseId = found.caseId || 'Votre cabinet est introuvable dans votre session : reconnectez-vous.';
    if (Object.keys(found).length > 0) return setErrors(found);
    const ok = await run('create-case', () => api.post('/api/judicial-cases', buildJudicialCasePayload(form, lawFirmId)),
      'Procédure enregistrée.', describeJudicialError);
    if (ok) {
      setFormOpen(false);
      onChanged();
    }
  }

  function openHearingForm(judicialCase) {
    clearFeedback();
    setHearingForm(emptyHearingForm());
    setHearingErrors({});
    setHearingFormFor(judicialCase.id);
  }

  async function submitHearing(event, judicialCase) {
    event.preventDefault();
    const found = validateHearing(hearingForm);
    if (Object.keys(found).length > 0) return setHearingErrors(found);
    const ok = await run(`hearing-create-${judicialCase.id}`,
      () => api.post(`/api/judicial-cases/${judicialCase.id}/hearings`, buildHearingPayload(hearingForm)),
      'Audience planifiée.', describeJudicialError);
    if (ok) {
      setHearingFormFor(null);
      onChanged();
    }
  }

  const setHearingField = (field, value) => {
    setHearingForm((previous) => ({ ...previous, [field]: value }));
    setHearingErrors((previous) => ({ ...previous, [field]: undefined }));
  };

  return (
    <div className="space-y-6">
      {formOpen ? (
        <SectionCard title="Nouvelle procédure judiciaire">
          <form onSubmit={submit} className="grid gap-4 md:grid-cols-2" noValidate>
            <FormField label="Dossier" htmlFor="jc-case" required error={errors.caseId}>
              <select id="jc-case" className={inputClass} value={form.caseId} onChange={(event) => setField('caseId', event.target.value)}>
                <option value="">— Choisir un dossier —</option>
                {caseList.map((item) => <option key={item.id} value={item.id}>{item.title} ({item.reference})</option>)}
              </select>
            </FormField>
            <FormField label="Numéro d'affaire du tribunal" htmlFor="jc-number" required error={errors.courtCaseNumber}>
              <input id="jc-number" className={inputClass} value={form.courtCaseNumber} maxLength={255}
                onChange={(event) => setField('courtCaseNumber', event.target.value)} />
            </FormField>
            <FormField label="Tribunal" htmlFor="jc-court" required error={errors.courtId}
              hint={courtList.length === 0 ? "Aucun tribunal : créez-en un dans l'onglet « Tribunaux »." : undefined}>
              <select id="jc-court" className={inputClass} value={form.courtId} onChange={(event) => setField('courtId', event.target.value)}>
                <option value="">— Choisir un tribunal —</option>
                {courtList.map((court) => <option key={court.id} value={court.id}>{courtLabel(court)}</option>)}
              </select>
            </FormField>
            <FormField label="Juge" htmlFor="jc-judge" required error={errors.judgeId}
              hint={form.courtId && judgesOfSelectedCourt.length === 0 ? "Ce tribunal n'a pas de juge : ajoutez-en un dans l'onglet « Tribunaux »." : undefined}>
              <select id="jc-judge" className={inputClass} value={form.judgeId} disabled={!form.courtId}
                onChange={(event) => setField('judgeId', event.target.value)}>
                <option value="">{form.courtId ? '— Choisir un juge —' : "Choisissez d'abord un tribunal"}</option>
                {judgesOfSelectedCourt.map((judge) => <option key={judge.id} value={judge.id}>{judgeName(judge)} — {judge.role}</option>)}
              </select>
            </FormField>
            <FormField label="Type de procédure" htmlFor="jc-type" required error={errors.procedureType}>
              <select id="jc-type" className={inputClass} value={form.procedureType} onChange={(event) => setField('procedureType', event.target.value)}>
                {PROCEDURE_TYPES.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
              </select>
            </FormField>
            <FormField label="Date d'ouverture" htmlFor="jc-opening" required error={errors.openingDate}>
              <input id="jc-opening" type="date" className={inputClass} value={form.openingDate}
                onChange={(event) => setField('openingDate', event.target.value)} />
            </FormField>
            <div className="flex gap-2 md:col-span-2">
              <button type="submit" className={primaryButton} disabled={busyKey === 'create-case'}>
                {busyKey === 'create-case' ? 'Enregistrement…' : 'Enregistrer la procédure'}
              </button>
              <button type="button" className={secondaryButton} onClick={() => setFormOpen(false)}>Annuler</button>
            </div>
          </form>
        </SectionCard>
      ) : null}

      <SectionCard
        title="Procédures judiciaires"
        action={canWrite ? <button type="button" className={primaryButton} onClick={openForm}>Nouvelle procédure</button> : null}
      >
        <AsyncState
          loading={false}
          error={judicialCases.ok ? null : judicialCases.error}
          onRetry={onChanged}
          isEmpty={list.length === 0}
          emptyMessage="Aucune procédure judiciaire enregistrée."
        >
          <div className="overflow-x-auto">
            <table className="w-full min-w-[980px] text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-4 font-medium">N° d'affaire</th>
                  <th className="py-2 pr-4 font-medium">Dossier</th>
                  <th className="py-2 pr-4 font-medium">Tribunal</th>
                  <th className="py-2 pr-4 font-medium">Type</th>
                  <th className="py-2 pr-4 font-medium">Prochaine audience</th>
                  <th className="py-2 pr-4 font-medium">Statut</th>
                  <th className="py-2 font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {list.map((judicialCase) => {
                  const detail = details[judicialCase.id];
                  const linked = casesById.get(judicialCase.caseId);
                  const court = courtsById.get(judicialCase.courtId);
                  const judge = judgesById.get(judicialCase.judgeId);
                  const status = getCaseStatusDisplay(judicialCase.status);
                  const upcoming = detail?.ok ? nextHearing(detail.hearings, now) : null;
                  const expanded = expandedId === judicialCase.id;
                  const busy = busyKey === `case-${judicialCase.id}`;
                  return (
                    <Fragment key={judicialCase.id}>
                      <tr className="border-b border-slate-100 align-top last:border-0">
                        <td className="py-3 pr-4 font-medium text-slate-800">{judicialCase.courtCaseNumber}</td>
                        <td className="py-3 pr-4 text-slate-600">{linked ? `${linked.title} (${linked.reference})` : 'Dossier inconnu'}</td>
                        <td className="py-3 pr-4 text-slate-600">{court ? courtLabel(court) : 'Tribunal inconnu'}</td>
                        <td className="py-3 pr-4 text-slate-600">{getProcedureLabel(judicialCase.procedureType)}</td>
                        <td className="py-3 pr-4 text-slate-600">
                          {!detail ? '…' : !detail.ok ? 'Indisponible' : upcoming ? formatDateTime(upcoming.dateTime) : 'Aucune'}
                        </td>
                        <td className="py-3 pr-4"><StatusBadge tone={status.tone}>{status.label}</StatusBadge></td>
                        <td className="py-3">
                          <div className="flex flex-wrap gap-2">
                            <button type="button" className={secondaryButton} onClick={() => setExpandedId(expanded ? null : judicialCase.id)}>
                              {expanded ? 'Masquer' : 'Détails'}
                            </button>
                            {canWrite ? caseTransitions(judicialCase.status).map((next) => (
                              <button key={next} type="button" disabled={busy}
                                className={next === 'CLOSED' ? dangerButton : secondaryButton}
                                onClick={() => onCaseStatus(judicialCase, next)}>
                                {CASE_ACTION_LABELS[next]}
                              </button>
                            )) : null}
                          </div>
                        </td>
                      </tr>
                      {expanded ? (
                        <tr className="border-b border-slate-100 bg-slate-50">
                          <td colSpan={7} className="px-3 py-4 text-sm text-slate-600">
                            <dl className="mb-4 grid gap-2 md:grid-cols-3">
                              <div><dt className="text-xs uppercase text-slate-500">Juge</dt><dd>{judge ? `${judgeName(judge)} — ${judge.role}` : '—'}</dd></div>
                              <div><dt className="text-xs uppercase text-slate-500">Ouverte le</dt><dd>{formatDay(judicialCase.openingDate)}</dd></div>
                              <div><dt className="text-xs uppercase text-slate-500">Tribunal</dt><dd>{court ? `${court.name} (${court.type})` : '—'}</dd></div>
                            </dl>

                            <div className="mb-2 flex items-center justify-between">
                              <h4 className="font-semibold text-slate-800">Audiences</h4>
                              {canWrite && judicialCase.status !== 'CLOSED' ? (
                                <button type="button" className={secondaryButton} onClick={() => openHearingForm(judicialCase)}>Planifier une audience</button>
                              ) : null}
                            </div>

                            {hearingFormFor === judicialCase.id ? (
                              <form onSubmit={(event) => submitHearing(event, judicialCase)} className="mb-4 grid gap-3 rounded-xl border border-slate-200 bg-white p-3 md:grid-cols-2" noValidate>
                                <FormField label="Date et heure" htmlFor={`h-date-${judicialCase.id}`} required error={hearingErrors.dateTime}>
                                  <input id={`h-date-${judicialCase.id}`} type="datetime-local" className={inputClass} value={hearingForm.dateTime}
                                    min={toInputDateTime()} onChange={(event) => setHearingField('dateTime', event.target.value)} />
                                </FormField>
                                <FormField label="Type" htmlFor={`h-type-${judicialCase.id}`} required error={hearingErrors.type}>
                                  <select id={`h-type-${judicialCase.id}`} className={inputClass} value={hearingForm.type} onChange={(event) => setHearingField('type', event.target.value)}>
                                    {HEARING_TYPES.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
                                  </select>
                                </FormField>
                                <FormField label="Lieu" htmlFor={`h-loc-${judicialCase.id}`}>
                                  <input id={`h-loc-${judicialCase.id}`} className={inputClass} value={hearingForm.location} onChange={(event) => setHearingField('location', event.target.value)} />
                                </FormField>
                                <FormField label="Prochaine action" htmlFor={`h-next-${judicialCase.id}`}>
                                  <input id={`h-next-${judicialCase.id}`} className={inputClass} value={hearingForm.nextAction} onChange={(event) => setHearingField('nextAction', event.target.value)} />
                                </FormField>
                                <div className="md:col-span-2">
                                  <FormField label="Notes" htmlFor={`h-notes-${judicialCase.id}`}>
                                    <textarea id={`h-notes-${judicialCase.id}`} rows={2} className={inputClass} value={hearingForm.notes} onChange={(event) => setHearingField('notes', event.target.value)} />
                                  </FormField>
                                </div>
                                <div className="flex gap-2 md:col-span-2">
                                  <button type="submit" className={primaryButton} disabled={busyKey === `hearing-create-${judicialCase.id}`}>
                                    {busyKey === `hearing-create-${judicialCase.id}` ? 'Enregistrement…' : "Planifier l'audience"}
                                  </button>
                                  <button type="button" className={secondaryButton} onClick={() => setHearingFormFor(null)}>Annuler</button>
                                </div>
                              </form>
                            ) : null}

                            {!detail ? <p>Chargement…</p> : !detail.ok ? (
                              <p className="text-rose-600">Les audiences et l'historique de cette procédure n'ont pas pu être chargés. Utilisez « Actualiser ».</p>
                            ) : (
                              <>
                                {detail.hearings.length === 0 ? <p className="mb-4">Aucune audience planifiée.</p> : (
                                  <ul className="mb-4 space-y-2">
                                    {detail.hearings.map((hearing) => {
                                      const hearingStatus = getHearingStatusDisplay(hearing.status);
                                      const hearingBusy = busyKey === `hearing-${hearing.id}`;
                                      return (
                                        <li key={hearing.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-200 bg-white p-2">
                                          <div>
                                            <div className="font-medium text-slate-800">{formatDateTime(hearing.dateTime)} · {getHearingTypeLabel(hearing.type)}</div>
                                            <div className="text-xs text-slate-500">
                                              {[hearing.location, hearing.nextAction && `Prochaine action : ${hearing.nextAction}`, hearing.notes].filter(Boolean).join(' · ') || '—'}
                                            </div>
                                          </div>
                                          <div className="flex flex-wrap items-center gap-2">
                                            <StatusBadge tone={hearingStatus.tone}>{hearingStatus.label}</StatusBadge>
                                            {canWrite ? hearingTransitions(hearing.status).map((next) => (
                                              <button key={next} type="button" disabled={hearingBusy}
                                                className={next === 'CANCELLED' ? dangerButton : secondaryButton}
                                                onClick={() => onHearingStatus(hearing, next)}>
                                                {HEARING_ACTION_LABELS[next]}
                                              </button>
                                            )) : null}
                                          </div>
                                        </li>
                                      );
                                    })}
                                  </ul>
                                )}

                                <h4 className="mb-2 font-semibold text-slate-800">Historique de la procédure</h4>
                                {detail.events.length === 0 ? <p>Aucun événement.</p> : (
                                  <ul className="space-y-1">
                                    {detail.events.map((event) => (
                                      <li key={event.id}>
                                        <span className="text-slate-500">{formatDateTime(event.eventDate ?? event.createdAt)}</span>
                                        {' — '}<span className="font-medium text-slate-700">{getEventLabel(event.type)}</span>
                                        {event.description ? <span className="text-slate-500"> · {event.description}</span> : null}
                                      </li>
                                    ))}
                                  </ul>
                                )}
                              </>
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
