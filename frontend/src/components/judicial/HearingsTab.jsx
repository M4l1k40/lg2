import { useMemo, useState } from 'react';
import SectionCard from '../common/SectionCard';
import AsyncState from '../common/AsyncState';
import StatusBadge from '../common/StatusBadge';
import { dangerButton, primaryButton, secondaryButton } from '../common/buttonStyles';
import {
  HEARING_ACTION_LABELS,
  getHearingStatusDisplay,
  getHearingTypeLabel
} from '../../constants/judicialStatus';
import { flattenHearings, formatDateTime, groupHearings, hearingTransitions } from '../../utils/judicial';

const GROUPS = [
  { key: 'upcoming', label: 'À venir', empty: 'Aucune audience à venir.' },
  { key: 'toHandle', label: 'À traiter', empty: 'Aucune audience à traiter (passée non clôturée ou reportée).' },
  { key: 'history', label: 'Historique', empty: 'Aucune audience terminée ou annulée.' }
];
const EMPTY_ARRAY = [];

export default function HearingsTab({ judicialCases, details, casesById, canWrite, action, onHearingStatus, now }) {
  const [group, setGroup] = useState('upcoming');
  const { busyKey } = action;

  const list = judicialCases.ok && Array.isArray(judicialCases.value) ? judicialCases.value : EMPTY_ARRAY;
  const hearingsByCase = useMemo(
    () => Object.fromEntries(Object.entries(details).map(([id, value]) => [id, value.hearings])),
    [details]
  );
  const judicialCaseById = useMemo(() => new Map(list.map((item) => [item.id, item])), [list]);
  const groups = useMemo(() => groupHearings(flattenHearings(list, hearingsByCase), now), [list, hearingsByCase, now]);
  const failedCount = Object.values(details).filter((value) => !value.ok).length;
  const visible = groups[group];

  return (
    <SectionCard
      title="Audiences"
      action={
        <div className="flex flex-wrap gap-2">
          {GROUPS.map((item) => (
            <button key={item.key} type="button" onClick={() => setGroup(item.key)}
              className={group === item.key ? primaryButton : secondaryButton}>
              {item.label} ({groups[item.key].length})
            </button>
          ))}
        </div>
      }
    >
      {failedCount > 0 ? (
        <div className="mb-4 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
          Les audiences de {failedCount} procédure(s) n'ont pas pu être chargées : cette liste est incomplète. Utilisez « Actualiser ».
        </div>
      ) : null}
      <AsyncState
        loading={false}
        error={judicialCases.ok ? null : judicialCases.error}
        isEmpty={visible.length === 0}
        emptyMessage={GROUPS.find((item) => item.key === group).empty}
      >
        <div className="overflow-x-auto">
          <table className="w-full min-w-[820px] text-left text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
                <th className="py-2 pr-4 font-medium">Date et heure</th>
                <th className="py-2 pr-4 font-medium">Procédure</th>
                <th className="py-2 pr-4 font-medium">Dossier</th>
                <th className="py-2 pr-4 font-medium">Type</th>
                <th className="py-2 pr-4 font-medium">Lieu</th>
                <th className="py-2 pr-4 font-medium">Statut</th>
                <th className="py-2 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((hearing) => {
                const judicialCase = judicialCaseById.get(hearing.judicialCaseId);
                const linked = casesById.get(judicialCase?.caseId);
                const status = getHearingStatusDisplay(hearing.status);
                const busy = busyKey === `hearing-${hearing.id}`;
                return (
                  <tr key={hearing.id} className="border-b border-slate-100 align-top last:border-0">
                    <td className="py-3 pr-4 font-medium text-slate-800">{formatDateTime(hearing.dateTime)}</td>
                    <td className="py-3 pr-4 text-slate-600">{judicialCase?.courtCaseNumber || '—'}</td>
                    <td className="py-3 pr-4 text-slate-600">{linked ? `${linked.title} (${linked.reference})` : 'Dossier inconnu'}</td>
                    <td className="py-3 pr-4 text-slate-600">{getHearingTypeLabel(hearing.type)}</td>
                    <td className="py-3 pr-4 text-slate-600">{hearing.location || '—'}</td>
                    <td className="py-3 pr-4"><StatusBadge tone={status.tone}>{status.label}</StatusBadge></td>
                    <td className="py-3">
                      <div className="flex flex-wrap gap-2">
                        {canWrite ? hearingTransitions(hearing.status).map((next) => (
                          <button key={next} type="button" disabled={busy}
                            className={next === 'CANCELLED' ? dangerButton : secondaryButton}
                            onClick={() => onHearingStatus(hearing, next)}>
                            {HEARING_ACTION_LABELS[next]}
                          </button>
                        )) : null}
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
  );
}
