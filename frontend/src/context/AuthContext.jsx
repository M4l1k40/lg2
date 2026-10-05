import { createContext, useContext, useEffect, useMemo, useRef, useState } from 'react';
import Keycloak from 'keycloak-js';

const keycloakConfig = {
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8080',
  realm: import.meta.env.VITE_KEYCLOAK_REALM || 'legalflow',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'legalflow-frontend'
};

export const keycloak = new Keycloak(keycloakConfig);

const AuthContext = createContext(null);

function normalizeRoles(tokenParsed = {}) {
  const realmRoles = tokenParsed.realm_access?.roles || [];
  const resourceRoles = Object.values(tokenParsed.resource_access || {}).flatMap((resource) => resource.roles || []);

  return [...new Set([...realmRoles, ...resourceRoles].map((role) => role.replace(/^ROLE_/, '').toLowerCase()))];
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const initializedRef = useRef(false);

  useEffect(() => {
    let isMounted = true;

    const initAuth = async () => {
      try {
        if (initializedRef.current) {
          return;
        }

        initializedRef.current = true;

        const authenticated = await keycloak.init({
          onLoad: 'check-sso',
          checkLoginIframe: false,
          pkceMethod: 'S256'
        });

        if (!isMounted) {
          return;
        }

        if (authenticated && keycloak.tokenParsed) {
          const parsed = keycloak.tokenParsed;

          setUser({
            id: parsed.sub || null,
            email: parsed.email || null,
            name: parsed.name || parsed.preferred_username || null,
            username: parsed.preferred_username || null,
            roles: normalizeRoles(parsed),
            lawFirmId: parsed.lawFirmId || parsed.lawfirmid || null,
            token: keycloak.token || null,
            tokenParsed: parsed
          });
        } else {
          setUser(null);
        }
      } catch (error) {
        console.warn('Keycloak initialization failed:', error);
        if (isMounted) {
          setUser(null);
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    initAuth();

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

export function getAuthToken() {
  return keycloak.token || null;
}
