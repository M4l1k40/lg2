import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { LanguageProvider } from './context/LanguageContext';
import ProtectedRoute from './components/auth/ProtectedRoute';
import ClientLayout from './layouts/ClientLayout';
import LawyerLayout from './layouts/LawyerLayout';
import SecretaryLayout from './layouts/SecretaryLayout';
import AdminLayout from './layouts/AdminLayout';
import LoginPage from './pages/LoginPage';
import PageNotFound from './pages/PageNotFound';
import PlaceholderPage from './pages/PlaceholderPage';
import ClientDashboardPage from './pages/client/ClientDashboardPage';
import ClientProfilePage from './pages/client/ClientProfilePage';
import ClientCasesPage from './pages/client/ClientCasesPage';
import LawyerDashboardPage from './pages/lawyer/LawyerDashboardPage';
import SecretaryDashboardPage from './pages/secretary/SecretaryDashboardPage';
import AdminDashboardPage from './pages/admin/AdminDashboardPage';

export default function App() {
  return (
    <LanguageProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<Navigate to="/login" replace />} />
            <Route path="/login" element={<LoginPage />} />

            <Route element={<ProtectedRoute allowedRoles={['client']} />}>
              <Route path="/client" element={<ClientLayout />}>
                <Route index element={<Navigate to="dashboard" replace />} />
                <Route path="dashboard" element={<ClientDashboardPage />} />
                <Route path="profile" element={<ClientProfilePage />} />
                <Route path="cases" element={<ClientCasesPage />} />
                <Route path="documents" element={<PlaceholderPage titleKey="client.nav.documents" />} />
                <Route path="judicial" element={<PlaceholderPage titleKey="client.nav.judicial" />} />
                <Route path="payments" element={<PlaceholderPage titleKey="client.nav.payments" />} />
                <Route path="appointments" element={<PlaceholderPage titleKey="client.nav.appointments" />} />
                <Route path="notifications" element={<PlaceholderPage titleKey="client.nav.notifications" />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute allowedRoles={['lawyer', 'admin']} />}>
              <Route path="/lawyer" element={<LawyerLayout />}>
                <Route index element={<Navigate to="dashboard" replace />} />
                <Route path="dashboard" element={<LawyerDashboardPage />} />
                <Route path="clients" element={<PlaceholderPage titleKey="lawyer.nav.clients" />} />
                <Route path="cases" element={<PlaceholderPage titleKey="lawyer.nav.cases" />} />
                <Route path="judicial" element={<PlaceholderPage titleKey="lawyer.nav.judicial" />} />
                <Route path="deadlines" element={<PlaceholderPage titleKey="lawyer.nav.deadlines" />} />
                <Route path="appointments" element={<PlaceholderPage titleKey="lawyer.nav.appointments" />} />
                <Route path="documents" element={<PlaceholderPage titleKey="lawyer.nav.documents" />} />
                <Route path="consultations" element={<PlaceholderPage titleKey="lawyer.nav.consultations" />} />
                <Route path="notifications" element={<PlaceholderPage titleKey="lawyer.nav.notifications" />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute allowedRoles={['secretary', 'admin']} />}>
              <Route path="/secretary" element={<SecretaryLayout />}>
                <Route index element={<Navigate to="dashboard" replace />} />
                <Route path="dashboard" element={<SecretaryDashboardPage />} />
                <Route path="lawyers" element={<PlaceholderPage titleKey="secretary.nav.lawyers" />} />
                <Route path="clients" element={<PlaceholderPage titleKey="secretary.nav.clients" />} />
                <Route path="cases" element={<PlaceholderPage titleKey="secretary.nav.cases" />} />
                <Route path="appointments" element={<PlaceholderPage titleKey="secretary.nav.appointments" />} />
                <Route path="documents" element={<PlaceholderPage titleKey="secretary.nav.documents" />} />
                <Route path="payments" element={<PlaceholderPage titleKey="secretary.nav.payments" />} />
                <Route path="notifications" element={<PlaceholderPage titleKey="secretary.nav.notifications" />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute allowedRoles={['admin']} />}>
              <Route path="/admin" element={<AdminLayout />}>
                <Route index element={<Navigate to="dashboard" replace />} />
                <Route path="dashboard" element={<AdminDashboardPage />} />
                <Route path="users" element={<PlaceholderPage titleKey="admin.nav.users" />} />
                <Route path="lawyers" element={<PlaceholderPage titleKey="admin.nav.lawyers" />} />
                <Route path="secretaries" element={<PlaceholderPage titleKey="admin.nav.secretaries" />} />
                <Route path="clients" element={<PlaceholderPage titleKey="admin.nav.clients" />} />
                <Route path="cases" element={<PlaceholderPage titleKey="admin.nav.cases" />} />
                <Route path="billing" element={<PlaceholderPage titleKey="admin.nav.billing" />} />
                <Route path="settings" element={<PlaceholderPage titleKey="admin.nav.settings" />} />
              </Route>
            </Route>

            <Route path="*" element={<PageNotFound />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </LanguageProvider>
  );
}
