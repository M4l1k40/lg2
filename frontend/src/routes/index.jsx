import { Navigate, Route, Routes } from 'react-router-dom';
import MainLayout from '../layouts/MainLayout';
import DashboardPage from '../pages/DashboardPage';
import LoginPage from '../pages/LoginPage';
import PageNotFound from '../pages/PageNotFound';
import PlaceholderPage from '../pages/PlaceholderPage';

export default function AppRoutes() {
  return (
    <MainLayout>
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/clients" element={<PlaceholderPage titleKey="nav.clients" />} />
        <Route path="/cases" element={<PlaceholderPage titleKey="nav.cases" />} />
        <Route path="/judicial" element={<PlaceholderPage titleKey="nav.judicial" />} />
        <Route path="/deadlines" element={<PlaceholderPage titleKey="nav.deadlines" />} />
        <Route path="/appointments" element={<PlaceholderPage titleKey="nav.appointments" />} />
        <Route path="/documents" element={<PlaceholderPage titleKey="nav.documents" />} />
        <Route path="/notifications" element={<PlaceholderPage titleKey="nav.notifications" />} />
        <Route path="/billing" element={<PlaceholderPage titleKey="nav.billing" />} />
        <Route path="/consultations" element={<PlaceholderPage titleKey="nav.consultations" />} />
        <Route path="*" element={<PageNotFound />} />
      </Routes>
    </MainLayout>
  );
}
