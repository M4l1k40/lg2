import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../context/AuthContext';

// Charge plusieurs sources en parallèle et indépendamment : si l'une échoue, seules les cartes qui en dépendent
// affichent « Indisponible ». Aucune valeur de remplacement n'est jamais inventée.
// `loaders` doit être un objet défini hors du composant : { nom: () => Promise }.
async function settle(loader) {
  try {
    return { ok: true, value: await loader() };
  } catch (error) {
    return { ok: false, error };
  }
}

export default function useDashboardSources(loaders) {
  const { loading: authLoading, isAuthenticated } = useAuth();
  const [state, setState] = useState({ loading: true, sources: null });
  const loadersRef = useRef(loaders);
  const mounted = useRef(true);

  const load = useCallback(async () => {
    const entries = Object.entries(loadersRef.current);
    const results = await Promise.all(entries.map(([, loader]) => settle(loader)));
    if (mounted.current) {
      setState({
        loading: false,
        sources: Object.fromEntries(entries.map(([name], index) => [name, results[index]]))
      });
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
