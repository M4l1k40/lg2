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
  APPOINTMENT_TYPES,
  getAppointmentStatusDisplay,
  getAppointmentTypeLabel
} from '../../constants/appointmentStatus';
import {
  appointmentActions,
  buildCreatePayload,
  buildUpdatePayload,
  describeAppointmentError,
  emptyForm,
  formatDateTime,
  groupAppointments,
  nowAsInputValue,
  toInputValue,
  validateForm
} from '../../utils/appointments';

const TABS = [
  { key: 'upcoming', label: 'À venir', empty: 'Aucun rendez-vous à venir.' },
  { key: 'toClose', label: 'À clôturer', empty: "Aucun rendez-vous passé en attente d'être clôturé." },
  { key: 'completed', label: 'Terminés', empty: 'Aucun rendez-vous terminé.' },
  { key: 'cancelled', label: 'Annulés', empty: 'Aucun rendez-vous annulé.' }
];

const asList = (value) => (Array.isArray(value) ? value : []);

export default function LawyerAppointmentsPage() {
  const isOffice = Boolean(keycloak.hasRealmRole?.('ADMIN') || keycloak.hasRealmRole?.('SECRETARY'));
  const myId = keycloak.tokenParsed?.sub;

  const appointmentsApi = useApi('/api/appointments');
  const clientsApi = useApi('/api/clients');
  const casesApi = useApi('/api/cases');
  const lawyersApi = useApi('/api/users/lawyers', { enabled: isOffice });
  const { busyKey, feedback, run, clearFeedback } = useAction();

  const [tab, setTab] = useState('upcoming');
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm());
  const [errors, setErrors] = useState({});
  const [expandedId, setExpandedId] = useState(null);

  // Recalculé à chaque rechargement des rendez-vous : « à venir » et « à clôturer » restent justes.
  const now = useMemo(() => new Date(), [appointmentsApi.data]); // eslint-disable-line react-hooks/exhaustive-deps
  const groups = useMemo(() => groupAppointments(appointmentsApi.data, now), [appointmentsApi.data, now]);
  const visible = groups[tab];

  const clients = asList(clientsApi.data);
  const cases = asList(casesApi.data);
  const lawyers = asList(lawyersApi.data);
  const clientsById = useMemo(
    () => new Map(clients.map((item) => [item.id, `${item.firstName} ${item.lastName}`.trim()])),
    [clients]
  );
  const casesById = useMemo(
    () => new Map(cases.map((item) => [item.id, item.title ? `${item.title} (${item.reference})` : item.reference])),
    [cases]
  );
  const lawyerName = (id) => {
    if (id === myId) return 'Vous';
    const found = lawyers.find((item) => item.id === id);
    return found ? `${found.firstName ?? ''} ${found.lastName ?? ''}`.trim() || found.username : null;
  };
  const casesOfSelectedClient = cases.filter((item) => item.clientId === form.clientId);

  function openCreate() {
    clearFeedback();
    setEditing(null);
    setForm(emptyForm());
    setErrors({});
    setFormOpen(true);
  }

  function openEdit(appointment) {
    clearFeedback();
    setEditing(appointment);
    setForm({
      ...emptyForm(),
      clientId: appointment.clientId,
      caseId: appointment.caseId || '',
      lawyerId: appointment.lawyerId,
      type: appointment.type,
      dateTime: toInputValue(appointment.dateTime),
      location: appointment.location || '',
      notes: appointment.notes || ''
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
    setForm((previous) => {
      const next = { ...previous, [field]: value };
      if (field === 'clientId') next.caseId = '';
      if (field === 'caseId' && value && !previous.lawyerId) {
        const selected = cases.find((item) => item.id === value);
        if (selected?.lawyerId) next.lawyerId = selected.lawyerId;
      }
      return next;
    });
    setErrors((previous) => ({ ...previous, [field]: undefined }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const found = validateForm(form, { editing: Boolean(editing), isOffice, now: new Date() });
    if (!editing && !isOffice && !myId) {
      found.clientId = found.clientId || 'Votre identifiant utilisateur est introuvable : reconnectez-vous.';
    }
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }

    const action = editing
      ? () => api.put(`/api/appointments/${editing.id}`, buildUpdatePayload(form))
      : () => api.post('/api/appointments', buildCreatePayload(form, isOffice ? form.lawyerId : myId));
    const ok = await run(
      editing ? `save-${editing.id}` : 'create',
      action,
      editing ? 'Rendez-vous modifié.' : 'Rendez-vous créé.',
      describeAppointmentError
    );
    if (ok) {
      closeForm();
      appointmentsApi.refetch();
    }
  }

  async function changeStatus(appointment, status) {
    if (status === 'CANCELLED' && !window.confirm('Annuler ce rendez-vous ? Cette action est définitive.')) return;
    const ok = await run(
      `status-${appointment.id}`,
      () => api.patch(`/api/appointments/${appointment.id}/status?status=${status}`),
      status === 'COMPLETED' ? 'Rendez-vous marqué comme terminé.' : 'Rendez-vous annulé.',
      describeAppointmentError
    );
    if (ok) appointmentsApi.refetch();
  }

  async function remove(appointment) {
    if (!window.confirm('Supprimer définitivement ce rendez-vous ?')) return;
    const ok = await run(
      `delete-${appointment.id}`,
      () => api.delete(`/api/appointments/${appointment.id}`),
      'Rendez-vous supprimé.',
      describeAppointmentError
    );
    if (ok) appointmentsApi.refetch();
  }

  const saving = busyKey === 'create' || (editing && busyKey === `save-${editing.id}`);

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow={isOffice ? 'Administration' : 'Espace avocat'}
        title="Rendez-vous"
        description={
          isOffice
            ? 'Planifiez et suivez les rendez-vous de tous les avocats du cabinet.'
            : 'Planifiez vos rendez-vous, modifiez-les et clôturez-les une fois passés.'
        }
        action={
          <>
            <button type="button" className={secondaryButton} onClick={appointmentsApi.refetch}>
              Actualiser
            </button>
            <button type="button" className={primaryButton} onClick={openCreate}>
              Nouveau rendez-vous
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
        <SectionCard title={editing ? 'Modifier le rendez-vous' : 'Nouveau rendez-vous'}>
          <form onSubmit={handleSubmit} className="grid gap-4 md:grid-cols-2" noValidate>
            <FormField label="Client" htmlFor="appointment-client" required error={errors.clientId}
              hint={editing ? 'Le client ne peut pas être modifié : annulez ce rendez-vous et créez-en un nouveau.' : undefined}>
              <select id="appointment-client" className={inputClass} value={form.clientId} disabled={Boolean(editing)}
                onChange={(event) => update('clientId', event.target.value)}>
                <option value="">— Choisir un client —</option>
                {clients.map((item) => (
                  <option key={item.id} value={item.id}>{item.firstName} {item.lastName}</option>
                ))}
              </select>
            </FormField>

            <FormField label="Dossier" htmlFor="appointment-case" hint="Facultatif.">
              <select id="appointment-case" className={inputClass} value={form.caseId}
                disabled={Boolean(editing) || !form.clientId} onChange={(event) => update('caseId', event.target.value)}>
                <option value="">{form.clientId ? '— Aucun dossier —' : "Choisissez d'abord un client"}</option>
                {(editing ? cases : casesOfSelectedClient).map((item) => (
                  <option key={item.id} value={item.id}>{item.title} ({item.reference})</option>
                ))}
              </select>
            </FormField>

            {isOffice ? (
              <FormField label="Avocat" htmlFor="appointment-lawyer" required error={errors.lawyerId}>
                <select id="appointment-lawyer" className={inputClass} value={form.lawyerId} disabled={Boolean(editing)}
                  onChange={(event) => update('lawyerId', event.target.value)}>
                  <option value="">— Choisir un avocat —</option>
                  {lawyers.map((item) => (
                    <option key={item.id} value={item.id}>{`${item.firstName ?? ''} ${item.lastName ?? ''}`.trim() || item.username}</option>
                  ))}
                </select>
              </FormField>
            ) : null}

            <FormField label="Type" htmlFor="appointment-type" required error={errors.type}>
              <select id="appointment-type" className={inputClass} value={form.type}
                onChange={(event) => update('type', event.target.value)}>
                {APPOINTMENT_TYPES.map((item) => (
                  <option key={item.value} value={item.value}>{item.label}</option>
                ))}
              </select>
            </FormField>

            <FormField label="Date et heure" htmlFor="appointment-date" required error={errors.dateTime}>
              <input id="appointment-date" type="datetime-local" className={inputClass} value={form.dateTime}
                min={nowAsInputValue()} onChange={(event) => update('dateTime', event.target.value)} />
            </FormField>

            <FormField label="Lieu" htmlFor="appointment-location" error={errors.location}>
              <input id="appointment-location" type="text" className={inputClass} value={form.location}
                placeholder="Cabinet, tribunal, visioconférence…" onChange={(event) => update('location', event.target.value)} />
            </FormField>

            <div className="md:col-span-2">
              <FormField label="Notes" htmlFor="appointment-notes" error={errors.notes}>
                <textarea id="appointment-notes" rows={3} className={inputClass} value={form.notes}
                  onChange={(event) => update('notes', event.target.value)} />
              </FormField>
            </div>

            <div className="flex gap-2 md:col-span-2">
              <button type="submit" className={primaryButton} disabled={Boolean(saving)}>
                {saving ? 'Enregistrement…' : editing ? 'Enregistrer les modifications' : 'Créer le rendez-vous'}
              </button>
              <button type="button" className={secondaryButton} onClick={closeForm}>
                Annuler
              </button>
            </div>
          </form>
        </SectionCard>
      ) : null}

      <SectionCard
        title="Agenda"
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
          loading={appointmentsApi.loading}
          error={appointmentsApi.error}
          onRetry={appointmentsApi.refetch}
          isEmpty={visible.length === 0}
          emptyMessage={TABS.find((item) => item.key === tab).empty}
          loadingMessage="Chargement des rendez-vous…"
        >
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                  <th className="py-2 pr-4 font-medium">Date et heure</th>
                  <th className="py-2 pr-4 font-medium">Type</th>
                  <th className="py-2 pr-4 font-medium">Client</th>
                  <th className="py-2 pr-4 font-medium">Dossier</th>
                  <th className="py-2 pr-4 font-medium">Lieu</th>
                  <th className="py-2 pr-4 font-medium">Statut</th>
                  <th className="py-2 font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((appointment) => {
                  const status = getAppointmentStatusDisplay(appointment.status);
                  const actions = appointmentActions(appointment, { isOffice, now });
                  const busy = busyKey && busyKey.endsWith(appointment.id);
                  const expanded = expandedId === appointment.id;
                  const lawyer = lawyerName(appointment.lawyerId);

                  return (
                    <Fragment key={appointment.id}>
                      <tr className="border-b border-slate-100 align-top last:border-0">
                        <td className="py-3 pr-4 font-medium text-slate-800">{formatDateTime(appointment.dateTime)}</td>
                        <td className="py-3 pr-4 text-slate-600">{getAppointmentTypeLabel(appointment.type)}</td>
                        <td className="py-3 pr-4 text-slate-600">{clientsById.get(appointment.clientId) || 'Client inconnu'}</td>
                        <td className="py-3 pr-4 text-slate-600">
                          {appointment.caseId ? casesById.get(appointment.caseId) || 'Dossier inconnu' : '—'}
                        </td>
                        <td className="py-3 pr-4 text-slate-600">{appointment.location || '—'}</td>
                        <td className="py-3 pr-4"><StatusBadge tone={status.tone}>{status.label}</StatusBadge></td>
                        <td className="py-3">
                          <div className="flex flex-wrap gap-2">
                            <button type="button" className={secondaryButton}
                              onClick={() => setExpandedId(expanded ? null : appointment.id)}>
                              {expanded ? 'Masquer' : 'Détails'}
                            </button>
                            {actions.canEdit ? (
                              <button type="button" className={secondaryButton} disabled={Boolean(busy)} onClick={() => openEdit(appointment)}>
                                Modifier
                              </button>
                            ) : null}
                            {actions.canComplete ? (
                              <button type="button" className={secondaryButton} disabled={Boolean(busy)}
                                onClick={() => changeStatus(appointment, 'COMPLETED')}>
                                Terminé
                              </button>
                            ) : null}
                            {actions.canCancel ? (
                              <button type="button" className={dangerButton} disabled={Boolean(busy)}
                                onClick={() => changeStatus(appointment, 'CANCELLED')}>
                                Annuler
                              </button>
                            ) : null}
                            {actions.canDelete ? (
                              <button type="button" className={dangerButton} disabled={Boolean(busy)} onClick={() => remove(appointment)}>
                                Supprimer
                              </button>
                            ) : null}
                          </div>
                        </td>
                      </tr>
                      {expanded ? (
                        <tr className="border-b border-slate-100 bg-slate-50">
                          <td colSpan={7} className="px-3 py-3 text-sm text-slate-600">
                            <dl className="grid gap-2 md:grid-cols-3">
                              <div><dt className="text-xs uppercase text-slate-500">Avocat</dt><dd>{lawyer || '—'}</dd></div>
                              <div><dt className="text-xs uppercase text-slate-500">Créé le</dt><dd>{formatDateTime(appointment.createdAt)}</dd></div>
                              <div className="md:col-span-3">
                                <dt className="text-xs uppercase text-slate-500">Notes</dt>
                                <dd className="whitespace-pre-wrap">{appointment.notes || 'Aucune note.'}</dd>
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
