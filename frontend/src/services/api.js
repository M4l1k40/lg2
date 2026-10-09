import { keycloak } from '../context/AuthContext';

const API_BASE_URL = (import.meta.env.VITE_API_URL || 'http://localhost:9000').replace(/\/$/, '');

export const apiBaseUrl = API_BASE_URL;

export class ApiError extends Error {
  constructor(message, status, details = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
  }
}

/**
 * Retourne le header Authorization avec un token Keycloak valide.
 * Le token est rafraîchi s'il expire dans moins de 30 secondes.
 */
async function getAuthHeaders() {
  if (!keycloak.authenticated) {
    return {};
  }

  try {
    await keycloak.updateToken(30);
  } catch (error) {
    // Session Keycloak expirée : on renvoie l'utilisateur vers la page de connexion
    keycloak.login();
    throw new ApiError('Session expirée, reconnexion en cours…', 401);
  }

  return { Authorization: `Bearer ${keycloak.token}` };
}

async function buildError(response) {
  const text = await response.text();
  let message = text;
  let details = null;

  try {
    details = JSON.parse(text);
    message = details.message || details.error || text;
  } catch {
    // La réponse n'est pas du JSON : on garde le texte brut
  }

  return new ApiError(message || `Erreur ${response.status}`, response.status, details);
}

function isPlainObjectBody(body) {
  return (
    body !== null &&
    typeof body === 'object' &&
    !(body instanceof FormData) &&
    !(body instanceof Blob) &&
    !(body instanceof URLSearchParams) &&
    !(body instanceof ArrayBuffer)
  );
}

export async function apiRequest(endpoint, options = {}) {
  const { body, headers = {}, ...rest } = options;
  const jsonBody = body !== undefined && isPlainObjectBody(body);
  const authHeaders = await getAuthHeaders();

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...rest,
    headers: {
      Accept: 'application/json',
      ...(jsonBody ? { 'Content-Type': 'application/json' } : {}),
      ...authHeaders,
      ...headers
    },
    body: jsonBody ? JSON.stringify(body) : body
  });

  if (!response.ok) {
    throw await buildError(response);
  }

  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return response.json();
  }

  return response.text();
}

export const api = {
  get: (endpoint, options) => apiRequest(endpoint, { ...options, method: 'GET' }),
  post: (endpoint, body, options) => apiRequest(endpoint, { ...options, method: 'POST', body }),
  put: (endpoint, body, options) => apiRequest(endpoint, { ...options, method: 'PUT', body }),
  patch: (endpoint, body, options) => apiRequest(endpoint, { ...options, method: 'PATCH', body }),
  delete: (endpoint, options) => apiRequest(endpoint, { ...options, method: 'DELETE' })
};
