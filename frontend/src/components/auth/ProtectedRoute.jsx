import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { getHomePath } from '../../utils/roleHome';

export default function ProtectedRoute({ allowedRoles = [], children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-100 text-sm font-medium text-slate-600">
        Chargement de votre espace…
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  const userHomePath = getHomePath(user.roles || []);
  const hasAllowedRole = allowedRoles.length === 0 || allowedRoles.some((role) => user.roles.includes(String(role).toLowerCase()));

  if (!hasAllowedRole) {
    return userHomePath ? <Navigate to={userHomePath} replace /> : <Navigate to="/login" replace />;
  }

  return children || <Outlet />;
}
