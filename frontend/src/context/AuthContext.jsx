import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import Keycloak from 'keycloak-js';
import { getHomePath } from '../utils/roleHome';

const keycloakConfig = {
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8080',
  realm: import.meta.env.VITE_KEYCLOAK_REALM || 'legalflow',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'legalflow-frontend'
};

export const keycloak = new Keycloak(keycloakConfig);

// keycloak.init() must run exactly once per page load (it consumes the ?code/#code callback).
// React.StrictMode runs effects twice in dev, so the shared promise lives at module level:
// every mount awaits the same init instead of the 2nd one being skipped.
let keycloakInitPromise = null;

function initKeycloak() {
  if (!keycloakInitPromise) {
    keycloakInitPromise = keycloak.init({
      onLoad: 'check-sso',
      checkLoginIframe: false,
      pkceMethod: 'S256'
    });
  }

  return keycloakInitPromise;
}

export { getHomePath };

const AuthContext = createContext(null);

function normalizeRoles(tokenParsed = {}) {
  const realmRoles = tokenParsed.realm_access?.roles || [];
  const resourceRoles = Object.values(tokenParsed.resource_access || {}).flatMap((resource) => resource.roles || []);

  return [...new Set([...realmRoles, ...resourceRoles].map((role) => role.replace(/^ROLE_/, '').toLowerCase()))];
}

function buildUser(parsed) {
  return {
    id: parsed.sub || null,
    email: parsed.email || null,
    name: parsed.name || parsed.preferred_username || null,
    username: parsed.preferred_username || null,
    roles: normalizeRoles(parsed),
    // lawFirmId comes exclusively from the JWT claim, never from user input.
    lawFirmId: parsed.lawFirmId || parsed.lawfirmid || null,
    token: keycloak.token || null,
    tokenParsed: parsed
  };
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;

    initKeycloak()
      .then((authenticated) => {
        if (!isMounted) {
          return;
        }

        setUser(authenticated && keycloak.tokenParsed ? buildUser(keycloak.tokenParsed) : null);
      })
      .catch((error) => {
        console.warn('Keycloak initialization failed:', error);
        if (isMounted) {
          setUser(null);
        }
      })
      .finally(() => {
        if (isMounted) {
          setLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  const login = async () => {
    try {
      await keycloak.login({
        redirectUri: `${window.location.origin}/login`
      });
    } catch (error) {
      console.warn('Keycloak login failed:', error);
    }
  };

  const logout = async () => {
    try {
      await keycloak.logout({
        redirectUri: `${window.location.origin}/login`
      });
    } catch (error) {
      console.warn('Keycloak logout failed:', error);
    }
  };

  const value = useMemo(() => ({
    keycloak,
    user,
    loading,
    login,
    logout,
    isAuthenticated: !!user,
    hasRole: (role) => user?.roles?.includes(String(role).toLowerCase()) || false,
    roles: user?.roles || []
  }), [user, loading]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }

  return context;
}

export async function getAuthToken() {
  if (!keycloak.authenticated) {
    return null;
  }

  await keycloak.updateToken(30);
  return keycloak.token || null;
}
