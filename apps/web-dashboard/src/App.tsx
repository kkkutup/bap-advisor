import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import { AppLayout } from './components/layout/AppLayout';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { SubmissionsPage } from './pages/SubmissionsPage';
import { NewSubmissionPage } from './pages/NewSubmissionPage';
import { ExtractionReviewPage } from './pages/ExtractionReviewPage';
import { AuditReportPage } from './pages/AuditReportPage';
import { VersionDiffPage } from './pages/VersionDiffPage';
import { RegulationsAdminPage } from './pages/RegulationsAdminPage';
import { SystemSettingsPage } from './pages/SystemSettingsPage';

const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="w-8 h-8 border-4 border-meu-600 border-t-transparent rounded-full animate-spin"></div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
};

export const App: React.FC = () => {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route
        path="/"
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="submissions" element={<SubmissionsPage />} />
        <Route path="submissions/new" element={<NewSubmissionPage />} />
        <Route path="submissions/:id/versions/:versionNo/review" element={<ExtractionReviewPage />} />
        <Route path="submissions/:id/versions/:versionNo/report" element={<AuditReportPage />} />
        <Route path="submissions/:id/diff" element={<VersionDiffPage />} />
        <Route path="admin/regulations" element={<RegulationsAdminPage />} />
        <Route path="admin/settings" element={<SystemSettingsPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
};

export default App;
