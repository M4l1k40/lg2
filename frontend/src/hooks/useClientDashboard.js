import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { api } from '../services/api';

// Chaque source est chargée indépendamment : si l'une échoue, seules ses cartes affichent « Indisponible »
// et les autres restent exactes. Aucune valeur de remplacement n'est jamais inventée.
async function settle(promise) {
  try {
    return { ok: true, value: await promise };
  } catch (error) {
    return { ok: false, error };
  }
}

const asList = (value) => (Array.isArray(value) ? value : []);

// Un seul échec rend toute la source « indisponible » : un total partiel serait un chiffre faux.
async function loadDocuments(cases) {
  const lists = await Promise.all(asList(cases).map((item) => api.get(`/api/documents/case/${item.id}`)));
  return lists.flatMap(asList);
}

async function loadHearings() {
  const judicialCases = asList(await api.get('/api/judicial-cases'));
  const lists = await Promise.all(judicialCases.map((item) => api.get(`/api/judicial-cases/${item.id}/hearings`)));
  return lists.flatMap(asList);
}

export default function useClientDashboard() {
  const { loading: authLoading, isAuthenticated } = useAuth();
  const [state, setState] = useState({ loading: true, sources: null });
  const mounted = useRef(true);

  const load = useCallback(async () => {
    const [cases, invoices, appointments, notifications, hearings] = await Promise.all([
      settle(api.get('/api/cases')),
      settle(api.get('/api/invoices')),
      settle(api.get('/api/appointments')),
      settle(api.get('/api/notifications')),
      settle(loadHearings())
    ]);
    const documents = cases.ok ? await settle(loadDocuments(cases.value)) : { ok: false };
    if (mounted.current) {
      setState({ loading: false, sources: { cases, invoices, appointments, notifications, hearings, documents } });
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
