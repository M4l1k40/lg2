import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';

// Charge tribunaux, procédures et dossiers, puis les audiences et événements de chaque procédure.
// Chaque source est indépendante : si l'une échoue, seules les parties qui en dépendent sont « indisponibles ».
async function settle(promise) {
  try {
    return { ok: true, value: await promise };
  } catch (error) {
    return { ok: false, error };
  }
}

const asList = (value) => (Array.isArray(value) ? value : []);

async function loadDetails(judicialCase) {
  try {
    const [hearings, events] = await Promise.all([
      api.get(`/api/judicial-cases/${judicialCase.id}/hearings`),
      api.get(`/api/judicial-cases/${judicialCase.id}/events`)
    ]);
    return [judicialCase.id, { ok: true, hearings: asList(hearings), events: asList(events) }];
  } catch (error) {
    return [judicialCase.id, { ok: false, error, hearings: [], events: [] }];
  }
}

export default function useJudicialData() {
  const { loading: authLoading, isAuthenticated } = useAuth();
  const [state, setState] = useState({ loading: true, courts: null, judicialCases: null, cases: null, details: {} });
  const mounted = useRef(true);

  const load = useCallback(async () => {
    const [courts, judicialCases, cases] = await Promise.all([
      settle(api.get('/api/courts')),
      settle(api.get('/api/judicial-cases')),
      settle(api.get('/api/cases'))
    ]);
    const entries = judicialCases.ok ? await Promise.all(asList(judicialCases.value).map(loadDetails)) : [];
    if (mounted.current) {
      setState({ loading: false, courts, judicialCases, cases, details: Object.fromEntries(entries) });
    }
  }, []);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  useEffect(() => {
    if (!authLoading && isAuthenticated) {
      load();
    }
  }, [authLoading, isAuthenticated, load]);

  return { ...state, refetch: load };
}
