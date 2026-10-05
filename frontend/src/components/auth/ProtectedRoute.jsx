import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

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

  if (allowedRoles.length > 0) {
    const hasAccess = allowedRoles.some((role) => user.roles.includes(String(role).toLowerCase()));

    if (!hasAccess) {
      return <Navigate to="/login" replace />;
    }
  }

  return children || <Outlet />;
}
