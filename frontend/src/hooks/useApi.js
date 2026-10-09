import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { apiRequest } from '../services/api';

/**
 * Charge une ressource GET via la gateway (avec le token Keycloak).
 *
 *   const { data, loading, error, refetch } = useApi('/api/cases');
 *
 * Options : { enabled: false } pour différer l'appel.
 */
export default function useApi(endpoint, { enabled = true } = {}) {
  const { loading: authLoading, isAuthenticated } = useAuth();
  const [state, setState] = useState({ data: null, loading: true, error: null });
  const [reloadKey, setReloadKey] = useState(0);

  const refetch = useCallback(() => setReloadKey((key) => key + 1), []);

  useEffect(() => {
    if (!enabled || !endpoint) {
      setState((previous) => ({ ...previous, loading: false }));
      return undefined;
    }

    // On attend que Keycloak ait fini son initialisation
    if (authLoading) {
      return undefined;
    }

    if (!isAuthenticated) {
      setState({ data: null, loading: false, error: new Error('Utilisateur non authentifié') });
      return undefined;
    }

    const controller = new AbortController();
    setState((previous) => ({ ...previous, loading: true, error: null }));

    apiRequest(endpoint, { signal: controller.signal })
      .then((data) => setState({ data, loading: false, error: null }))
      .catch((error) => {
        if (error.name === 'AbortError') {
          return;
        }
        setState({ data: null, loading: false, error });
      });

    return () => controller.abort();
  }, [endpoint, enabled, authLoading, isAuthenticated, reloadKey]);

  return { ...state, refetch };
}
