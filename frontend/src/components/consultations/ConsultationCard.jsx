import StatusBadge from '../common/StatusBadge';
import { formatDateTime, getConsultationStatus } from '../../constants/consultationStatus';

export default function ConsultationCard({ consultation, clientName, lawyerLabel, children }) {
  const status = getConsultationStatus(consultation.status);

  return (
    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <div className="font-medium text-slate-800">{consultation.subject}</div>
          <div className="text-sm text-slate-500">
            Demandée le {formatDateTime(consultation.createdAt)}
            {clientName ? ` · Client : ${clientName}` : ''}
            {lawyerLabel ? ` · Avocat : ${lawyerLabel}` : ''}
          </div>
        </div>
        <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
      </div>

      <p className="mt-3 whitespace-pre-line text-sm text-slate-700">{consultation.description}</p>

      {consultation.answer ? (
        <div className="mt-3 rounded-lg border border-violet-200 bg-violet-50 p-3">
          <div className="text-xs font-semibold uppercase tracking-wide text-violet-700">Réponse de l’avocat</div>
          <p className="mt-1 whitespace-pre-line text-sm text-slate-800">{consultation.answer}</p>
          {consultation.answeredAt ? (
            <div className="mt-2 text-xs text-slate-500">Répondu le {formatDateTime(consultation.answeredAt)}</div>
          ) : null}
        </div>
      ) : null}

      {children ? <div className="mt-3 flex flex-wrap items-start gap-2">{children}</div> : null}
    </div>
  );
}
