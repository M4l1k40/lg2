import { Fragment, useMemo, useState } from 'react';
import PageHeader from '../../components/common/PageHeader';
import SectionCard from '../../components/common/SectionCard';
import AsyncState from '../../components/common/AsyncState';
import StatusBadge from '../../components/common/StatusBadge';
import FormField, { inputClass } from '../../components/common/FormField';
import { dangerButton, primaryButton, secondaryButton } from '../../components/common/buttonStyles';
import { keycloak } from '../../context/AuthContext';
import useApi from '../../hooks/useApi';
import useAction from '../../hooks/useAction';
import { api } from '../../services/api';
import {
  DEADLINE_PRIORITIES,
  getDeadlineStatusDisplay,
  getPriorityDisplay
} from '../../constants/deadlineStatus';
import {
  buildPayload,
  deadlineActions,
  describeDeadlineError,
  describeDelay,
  emptyForm,
  filterByPriority,
  formatDueDate,
  groupDeadlines,
  isLate,
  nowAsInputValue,
  toInputValue,
  validateForm
} from '../../utils/deadlines';

const TABS = [
  { key: 'upcoming', label: 'À venir', empty: 'Aucune échéance à venir.' },
  { key: 'late', label: 'En retard', empty: 'Aucune échéance en retard.' },
  { key: 'completed', label: 'Terminées', empty: 'Aucune échéance terminée.' },
  { key: 'cancelled', label: 'Annulées', empty: 'Aucune échéance annulée.' }
];

const asList = (value) => (Array.isArray(value) ? value : []);

export default function LawyerDeadlinesPage() {
  const myId = keycloak.tokenParsed?.sub;

  const deadlinesApi = useApi('/api/deadlines');
  const casesApi = useApi('/api/cases');
  const { busyKey, feedback, run, clearFeedback } = useAction();

  const [tab, setTab] = useState('upcoming');
  const [priorityFilter, setPriorityFilter] = useState('');
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm());
  const [errors, setErrors] = useState({});
  const [expandedId, setExpandedId] = useState(null);

  // Recalculé à chaque rechargement : « en retard » et les délais restent justes.
  const now = useMemo(() => new Date(), [deadlinesApi.data]); // eslint-disable-line react-hooks/exhaustive-deps
  const groups = useMemo(() => groupDeadlines(deadlinesApi.data, now), [deadlinesApi.data, now]);
  const visible = filterByPriority(groups[tab], priorityFilter);

  const cases = asList(casesApi.data);
  const casesById = useMemo(
    () => new Map(cases.map((item) => [item.id, item.title ? `${item.title} (${item.reference})` : item.reference])),
    [cases]
  );

  function openCreate() {
    clearFeedback();
    setEditing(null);
    setForm(emptyForm());
    setErrors({});
    setFormOpen(true);
  }

  function openEdit(deadline) {
    clearFeedback();
    setEditing(deadline);
    setForm({
      caseId: deadline.caseId,
      title: deadline.title,
      description: deadline.description || '',
      dueDate: toInputValue(deadline.dueDate),
      priority: deadline.priority
    });
    setErrors({});
    setFormOpen(true);
  }

  function closeForm() {
    setFormOpen(false);
    setEditing(null);
    setErrors({});
  }

  function update(field, value) {
    setForm((previous) => ({ ...previous, [field]: value }));
    setErrors((previous) => ({ ...previous, [field]: undefined }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const found = validateForm(form, new Date());
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    const action = editing
      ? () => api.put(`/api/deadlines/${editing.id}`, buildPayload(form))
      : () => api.post('/api/deadlines', buildPayload(form));
    const ok = await run(
      editing ? `save-${editing.id}` : 'create',
      action,
      editing ? 'Échéance modifiée.' : 'Échéance créée.',
      describeDeadlineError
    );
    if (ok) {
      closeForm();
      deadlinesApi.refetch();
    }
  }

  async function changeStatus(deadline, status) {
    if (status === 'CANCELLED' && !window.confirm('Annuler cette échéance ? Cette action est définitive.')) return;
    const ok = await run(
      `status-${deadline.id}`,
      () => api.patch(`/api/deadlines/${deadline.id}/status?status=${status}`),
      status === 'COMPLETED' ? 'Échéance marquée comme terminée.' : 'Échéance annulée.',
      describeDeadlineError
    );
    if (ok) deadlinesApi.refetch();
  }

  async function remove(deadline) {
    if (!window.confirm('Supprimer définitivement cette échéance ?')) return;
    const ok = await run(
      `delete-${deadline.id}`,
      () => api.delete(`/api/deadlines/${deadline.id}`),
      'Échéance supprimée.',
      describeDeadlineError
    );
    if (ok) deadlinesApi.refetch();
  }

  const saving = busyKey === 'create' || (editing && busyKey === `save-${editing.id}`);

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Espace avocat"
        title="Échéances"
        description={`${groups.late.length} en retard · ${groups.upcoming.length} à venir · suivez les délais de vos dossiers et traitez-les à temps.`}
        action={
          <>
            <button type="button" className={secondaryButton} onClick={deadlinesApi.refetch}>
              Actualiser
            </button>
            <button type="button" className={primaryButton} onClick={openCreate}>
              Nouvelle échéance
            </button>
          </>
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

      {formOpen ? (
        <SectionCard title={editing ? "Modifier l'échéance" : 'Nouvelle échéance'}>
          <form onSubmit={handleSubmit} className="grid gap-4 md:grid-cols-2" noValidate>
            <FormField label="Dossier" htmlFor="deadline-case" required error={errors.caseId}>
              <select id="deadline-case" className={inputClass} value={form.caseId}
                onChange={(event) => update('caseId', event.target.value)}>
                <option value="">— Choisir un dossier —</option>
                {cases.map((item) => (
                  <option key={item.id} value={item.id}>{item.title} ({item.reference})</option>
                ))}
              </select>
            </FormField>

            <FormField label="Intitulé" htmlFor="deadline-title" required error={errors.title}>
              <input id="deadline-title" type="text" className={inputClass} value={form.title} maxLength={255}
                placeholder="Dépôt des conclusions, appel, prescription…" onChange={(event) => update('title', event.target.value)} />
            </FormField>

            <FormField label="Date limite" htmlFor="deadline-due" required error={errors.dueDate}>
              <input id="deadline-due" type="datetime-local" className={inputClass} value={form.dueDate}
                min={nowAsInputValue()} onChange={(event) => update('dueDate', event.target.value)} />
            </FormField>

            <FormField label="Priorité" htmlFor="deadline-priority" required error={errors.priority}>
              <select id="deadline-priority" className={inputClass} value={form.priority}
                onChange={(event) => update('priority', event.target.value)}>
                {DEADLINE_PRIORITIES.map((item) => (
                  <option key={item.value} value={item.value}>{item.label}</option>
                ))}
              </select>
            </FormField>

            <div className="md:col-span-2">
              <FormField label="Description" htmlFor="deadline-description" error={errors.description}>
                <textarea id="deadline-description" rows={3} className={inputClass} value={form.description}
                  onChange={(event) => update('description', event.target.value)} />
              </FormField>
            </div>

            <div className="flex gap-2 md:col-span-2">
              <button type="submit" className={primaryButton} disabled={Boolean(saving)}>
                {saving ? 'Enregistrement…' : editing ? 'Enregistrer les modifications' : "Créer l'échéance"}
              </button>
              <button type="button" className={secondaryButton} onClick={closeForm}>
                Annuler
              </button>
            </div>
          </form>
        </SectionCard>
      ) : null}

      <SectionCard
        title="Échéancier"
        action={
          <div className="flex flex-wrap items-center gap-2">
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
            <select
              aria-label="Filtrer par priorité"
              className={`${inputClass} !w-auto`}
              value={priorityFilter}
              onChange={(event) => setPriorityFilter(event.target.value)}
            >
              <option value="">Toutes les priorités</option>
              {DEADLINE_PRIORITIES.map((item) => (
                <option key={item.value} value={item.value}>{item.label}</option>
              ))}
            </select>
          </div>
        }
      >
        <AsyncState
          loading={deadlinesApi.loading}
          error={deadlinesApi.error}
          onRetry={deadlinesApi.refetch}
          isEmpty={visible.length === 0}
          emptyMessage={priorityFilter ? 'Aucune échéance pour cette priorité.' : TABS.find((item) => item.key === tab).empty}
          loadingMessage="Chargement des échéances…"
        >
          <div className="overflow-x-auto">
            <table className="w-full min-w-[860px] text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-4 font-medium">Échéance</th>
                  <th className="py-2 pr-4 font-medium">Dossier</th>
                  <th className="py-2 pr-4 font-medium">Date limite</th>
                  <th className="py-2 pr-4 font-medium">Priorité</th>
                  <th className="py-2 pr-4 font-medium">Statut</th>
                  <th className="py-2 font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((deadline) => {
                  const late = isLate(deadline, now);
                  const status = getDeadlineStatusDisplay(late ? 'OVERDUE' : deadline.status);
                  const priority = getPriorityDisplay(deadline.priority);
                  const actions = deadlineActions(deadline, now);
                  const delay = describeDelay(deadline, now);
                  const busy = busyKey && busyKey.endsWith(deadline.id);
                  const expanded = expandedId === deadline.id;

                  return (
                    <Fragment key={deadline.id}>
                      <tr className="border-b border-slate-100 align-top last:border-0">
                        <td className="py-3 pr-4 font-medium text-slate-800">{deadline.title}</td>
                        <td className="py-3 pr-4 text-slate-600">{casesById.get(deadline.caseId) || 'Dossier inconnu'}</td>
                        <td className="py-3 pr-4 text-slate-600">
                          <div>{formatDueDate(deadline.dueDate)}</div>
                          {delay ? (
                            <div className={`text-xs ${late ? 'font-medium text-rose-600' : 'text-slate-500'}`}>{delay}</div>
                          ) : null}
                        </td>
                        <td className="py-3 pr-4"><StatusBadge tone={priority.tone}>{priority.label}</StatusBadge></td>
                        <td className="py-3 pr-4"><StatusBadge tone={status.tone}>{status.label}</StatusBadge></td>
                        <td className="py-3">
                          <div className="flex flex-wrap gap-2">
                            <button type="button" className={secondaryButton}
                              onClick={() => setExpandedId(expanded ? null : deadline.id)}>
                              {expanded ? 'Masquer' : 'Détails'}
                            </button>
                            {actions.canEdit ? (
                              <button type="button" className={secondaryButton} disabled={Boolean(busy)} onClick={() => openEdit(deadline)}>
                                Modifier
                              </button>
                            ) : null}
                            {actions.canComplete ? (
                              <button type="button" className={secondaryButton} disabled={Boolean(busy)}
                                onClick={() => changeStatus(deadline, 'COMPLETED')}>
                                Terminer
                              </button>
                            ) : null}
                            {actions.canCancel ? (
                              <button type="button" className={dangerButton} disabled={Boolean(busy)}
                                onClick={() => changeStatus(deadline, 'CANCELLED')}>
                                Annuler
                              </button>
                            ) : null}
                            {actions.canDelete ? (
                              <button type="button" className={dangerButton} disabled={Boolean(busy)} onClick={() => remove(deadline)}>
                                Supprimer
                              </button>
                            ) : null}
                          </div>
                        </td>
                      </tr>
                      {expanded ? (
                        <tr className="border-b border-slate-100 bg-slate-50">
                          <td colSpan={6} className="px-3 py-3 text-sm text-slate-600">
                            <dl className="grid gap-2 md:grid-cols-3">
                              <div><dt className="text-xs uppercase text-slate-500">Créée par</dt>
                                <dd>{deadline.createdBy === myId ? 'Vous' : 'Un autre utilisateur du cabinet'}</dd></div>
                              <div><dt className="text-xs uppercase text-slate-500">Créée le</dt><dd>{formatDueDate(deadline.createdAt)}</dd></div>
                              <div><dt className="text-xs uppercase text-slate-500">Dernière modification</dt><dd>{formatDueDate(deadline.updatedAt)}</dd></div>
                              <div className="md:col-span-3">
                                <dt className="text-xs uppercase text-slate-500">Description</dt>
                                <dd className="whitespace-pre-wrap">{deadline.description || 'Aucune description.'}</dd>
                              </div>
                            </dl>
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
