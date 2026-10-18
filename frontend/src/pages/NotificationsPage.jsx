import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import PageHeader from '../components/common/PageHeader';
import SectionCard from '../components/common/SectionCard';
import AsyncState from '../components/common/AsyncState';
import StatusBadge from '../components/common/StatusBadge';
import { secondaryButton } from '../components/common/buttonStyles';
import { NOTIFICATIONS_CHANGED_EVENT } from '../components/notifications/NotificationBell';
import useAction from '../hooks/useAction';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';

const REFRESH_INTERVAL_MS = 15000;

const TYPE_LABELS = {
  CASE_CREATED: 'Nouveau dossier',
  CONSULTATION_CREATED: 'Consultation créée',
  CONSULTATION_ASSIGNED: 'Consultation assignée',
  CONSULTATION_ANSWERED: 'Consultation répondue',
  CONSULTATION_CLOSED: 'Consultation clôturée',
  CONSULTATION_CANCELLED: 'Consultation annulée',
  APPOINTMENT_CREATED: 'Rendez-vous créé',
  APPOINTMENT_UPDATED: 'Rendez-vous modifié',
  APPOINTMENT_CANCELLED: 'Rendez-vous annulé',
  APPOINTMENT_REMINDER: 'Rappel de rendez-vous',
  DEADLINE_CREATED: 'Échéance créée',
  DEADLINE_APPROACHING: 'Échéance proche',
  DEADLINE_OVERDUE: 'Échéance dépassée',
  INVOICE_CREATED: 'Facture créée',
  INVOICE_ISSUED: 'Facture émise',
  INVOICE_OVERDUE: 'Facture en retard',
  PAYMENT_RECEIVED: 'Paiement reçu',
  JUDICIAL_CASE_REGISTERED: 'Affaire enregistrée',
  JUDICIAL_STATUS_CHANGED: 'Statut judiciaire modifié',
  HEARING_CREATED: 'Audience créée',
  HEARING_STATUS_CHANGED: 'Audience modifiée'
};

const CHANNEL_LABELS = { EMAIL: 'Email', WHATSAPP: 'WhatsApp' };
const DELIVERY_LABELS = { SENT: 'Envoyée', FAILED: 'Envoi échoué', PENDING: 'En attente' };
const DELIVERY_TONES = { SENT: 'emerald', FAILED: 'rose', PENDING: 'amber' };

// Le serveur renvoie des dates sans fuseau (UTC) : on ajoute « Z » pour les afficher à l'heure locale.
function formatDate(value) {
  if (!value) return '';
  const date = new Date(/[zZ]|[+-]\d{2}:?\d{2}$/.test(value) ? value : `${value}Z`);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('fr-FR');
}

/** Charge la liste et la rafraîchit en arrière-plan, sans repasser par l'état « chargement » à chaque cycle. */
function useNotificationList() {
  const { loading: authLoading, isAuthenticated } = useAuth();
  const [state, setState] = useState({ items: [], loading: true, error: null });
  const mounted = useRef(true);

  const load = useCallback(async () => {
    try {
      const items = await api.get('/api/notifications');
      if (mounted.current) setState({ items: Array.isArray(items) ? items : [], loading: false, error: null });
    } catch (error) {
      if (mounted.current) setState((previous) => ({ ...previous, loading: false, error }));
    }
  }, []);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  useEffect(() => {
    if (authLoading || !isAuthenticated) return undefined;
    load();
    const timer = setInterval(load, REFRESH_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [authLoading, isAuthenticated, load]);

  return { ...state, refetch: load };
}

export default function NotificationsPage() {
  const { items, loading, error, refetch } = useNotificationList();
  const { busyKey, feedback, run } = useAction();

  const notifications = useMemo(
    () => [...items].sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt))),
    [items]
  );
  const unreadCount = notifications.filter((notification) => !notification.read).length;

  async function changed() {
    await refetch();
    window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED_EVENT));
  }

  async function markRead(id) {
    if (await run(`read-${id}`, () => api.patch(`/api/notifications/${id}/read`), 'Notification marquée comme lue.')) {
      await changed();
    }
  }

  async function markAllRead() {
    if (await run('read-all', () => api.patch('/api/notifications/read-all'), 'Toutes les notifications sont lues.')) {
      await changed();
    }
  }

  return (
    <div>
      <PageHeader
        eyebrow="Notifications"
        title="Mes notifications"
        description={unreadCount > 0 ? `${unreadCount} non lue(s) · actualisation automatique toutes les 15 secondes` : 'Aucune notification non lue · actualisation automatique toutes les 15 secondes'}
        action={
          <>
            <button type="button" className={secondaryButton} onClick={refetch}>
              Actualiser
            </button>
            <button
              type="button"
              className={secondaryButton}
              disabled={unreadCount === 0 || busyKey === 'read-all'}
              onClick={markAllRead}
            >
              Tout marquer comme lu
            </button>
          </>
        }
      />

      {feedback ? (
        <div
          className={`mb-4 rounded-xl border px-4 py-3 text-sm ${
            feedback.type === 'success'
              ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
              : 'border-rose-200 bg-rose-50 text-rose-700'
          }`}
        >
          {feedback.message}
        </div>
      ) : null}

      <SectionCard title="Boîte de réception">
        <AsyncState
          loading={loading}
          error={error && notifications.length === 0 ? error : null}
          onRetry={refetch}
          isEmpty={notifications.length === 0}
          emptyMessage="Aucune notification pour le moment."
        >
          <ul className="divide-y divide-slate-100">
            {notifications.map((notification) => (
              <li
                key={notification.id}
                className={`flex flex-col gap-3 py-4 pl-4 sm:flex-row sm:items-start sm:justify-between ${
                  notification.read ? 'border-l-4 border-transparent' : 'border-l-4 border-blue-500 bg-blue-50/40'
                }`}
              >
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className={`text-sm text-slate-900 ${notification.read ? 'font-medium' : 'font-semibold'}`}>
                      {TYPE_LABELS[notification.type] || notification.title || notification.type}
                    </span>
                    <StatusBadge tone={DELIVERY_TONES[notification.status] || 'slate'}>
                      {DELIVERY_LABELS[notification.status] || notification.status}
                      {notification.channel ? ` · ${CHANNEL_LABELS[notification.channel] || notification.channel}` : ''}
                    </StatusBadge>
                  </div>
                  <p className="mt-1 text-sm text-slate-700">{notification.message}</p>
                  {notification.status === 'FAILED' && notification.errorMessage ? (
                    <p className="mt-1 text-xs text-rose-600">{notification.errorMessage}</p>
                  ) : null}
                  <p className="mt-1 text-xs text-slate-500">{formatDate(notification.createdAt)}</p>
                </div>
                {!notification.read ? (
                  <button
                    type="button"
                    className={`${secondaryButton} shrink-0`}
                    disabled={busyKey === `read-${notification.id}`}
                    onClick={() => markRead(notification.id)}
                  >
                    Marquer comme lue
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        </AsyncState>
      </SectionCard>
    </div>
  );
}
