import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { api } from '../../services/api';

// Émis par la page Notifications après « marquer comme lu » pour rafraîchir le compteur sans attendre le prochain cycle.
export const NOTIFICATIONS_CHANGED_EVENT = 'legalflow:notifications-changed';

const REFRESH_INTERVAL_MS = 30000;

/**
 * Cloche de l'en-tête : nombre de notifications non lues, rafraîchi toutes les 30 secondes (polling).
 * Une erreur réseau est ignorée : la cloche ne doit jamais perturber la page.
 */
export default function NotificationBell({ to }) {
  const { loading: authLoading, isAuthenticated } = useAuth();
  const [unread, setUnread] = useState(0);

  const refresh = useCallback(async () => {
    try {
      const items = await api.get('/api/notifications/unread');
      setUnread(Array.isArray(items) ? items.length : 0);
    } catch {
      // volontairement silencieux
    }
  }, []);

  useEffect(() => {
    if (authLoading || !isAuthenticated) {
      return undefined;
    }
    refresh();
    const timer = setInterval(refresh, REFRESH_INTERVAL_MS);
    window.addEventListener(NOTIFICATIONS_CHANGED_EVENT, refresh);
    return () => {
      clearInterval(timer);
      window.removeEventListener(NOTIFICATIONS_CHANGED_EVENT, refresh);
    };
  }, [authLoading, isAuthenticated, refresh]);

  return (
    <Link
      to={to}
      aria-label={unread > 0 ? `Notifications : ${unread} non lue(s)` : 'Notifications'}
      className="relative rounded-lg border border-slate-200 bg-white p-2 text-slate-700 shadow-sm transition hover:border-slate-300 hover:bg-slate-50"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="h-5 w-5" aria-hidden="true">
        <path strokeLinecap="round" strokeLinejoin="round" d="M14.857 17.082a23.848 23.848 0 0 0 5.454-1.31A8.967 8.967 0 0 1 18 9.75V9A6 6 0 0 0 6 9v.75a8.967 8.967 0 0 1-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 0 1-5.714 0m5.714 0a3 3 0 1 1-5.714 0" />
      </svg>
      {unread > 0 ? (
        <span className="absolute -right-1.5 -top-1.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-rose-600 px-1 text-[11px] font-semibold text-white">
          {unread > 99 ? '99+' : unread}
        </span>
      ) : null}
    </Link>
  );
}
