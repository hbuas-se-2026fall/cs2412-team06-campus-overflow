import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth';
import { AppShell } from './shared/AppShell';
import { QuestionListPage } from './pages/QuestionListPage';
import { QuestionDetailPage } from './pages/QuestionDetailPage';
import { AskPage } from './pages/AskPage';
import { LoginPage } from './pages/LoginPage';
import { LeaderboardPage } from './pages/LeaderboardPage';
import { NotificationsPage } from './pages/NotificationsPage';

function RequireAuth({ children }: { children: JSX.Element }) {
  const { me, loading } = useAuth();
  if (loading) return <p className="muted">加载中…</p>;
  return me ? children : <Navigate to="/login" replace />;
}

export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<AppShell />}>
            <Route path="/" element={<QuestionListPage />} />
            <Route path="/questions/:id" element={<QuestionDetailPage />} />
            <Route path="/ask" element={<RequireAuth><AskPage /></RequireAuth>} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/leaderboard" element={<LeaderboardPage />} />
            <Route path="/notifications" element={<RequireAuth><NotificationsPage /></RequireAuth>} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
