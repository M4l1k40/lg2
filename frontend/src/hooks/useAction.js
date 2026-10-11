import { useCallback, useState } from 'react';

/**
 * Exécute une action asynchrone (POST/PUT...) en suivant son état :
 *   const { busyKey, feedback, run } = useAction();
 *   run(`start-${id}`, () => api.put(...), 'Consultation démarrée.', describeError);
 * `busyKey` permet de désactiver uniquement les boutons de la carte concernée.
 */
export default function useAction() {
  const [busyKey, setBusyKey] = useState(null);
  const [feedback, setFeedback] = useState(null);

  const clearFeedback = useCallback(() => setFeedback(null), []);

  const run = useCallback(async (key, action, successMessage, describeError) => {
    setBusyKey(key);
    setFeedback(null);
    try {
      await action();
      setFeedback({ type: 'success', message: successMessage });
      return true;
    } catch (error) {
      setFeedback({ type: 'error', message: describeError ? describeError(error) : error.message });
      return false;
    } finally {
      setBusyKey(null);
    }
  }, []);

  return { busyKey, feedback, run, clearFeedback };
}
