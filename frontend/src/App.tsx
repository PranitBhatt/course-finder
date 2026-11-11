import { Route, Routes } from 'react-router';
import { RequireAuth } from './components/auth/RequireAuth';
import { Footer } from './components/layout/Footer';
import { Header } from './components/layout/Header';
import { AUTH_ENABLED } from './context/AuthContext';
import { useTheme } from './hooks/useTheme';
import { AccountPage } from './pages/AccountPage/AccountPage';
import { LearningPage } from './pages/LearningPage/LearningPage';
import { LoginPage } from './pages/LoginPage/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage/NotFoundPage';
import { PrivacyPage } from './pages/PrivacyPage/PrivacyPage';
import { RegisterPage } from './pages/RegisterPage/RegisterPage';
import { SearchPage } from './pages/SearchPage/SearchPage';
import styles from './App.module.css';

export function App() {
  const { theme, toggleTheme } = useTheme();

  return (
    <div className={styles.shell}>
      <Header theme={theme} onToggleTheme={toggleTheme} />
      <Routes>
        {/* Search is public and never wrapped in RequireAuth. */}
        <Route path="/" element={<SearchPage />} />
        <Route path="/privacy" element={<PrivacyPage />} />
        {/* With the flag off these routes don't exist, so /login etc. fall through to the 404 page. */}
        {AUTH_ENABLED && (
          <>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route
              path="/account"
              element={
                <RequireAuth>
                  <AccountPage />
                </RequireAuth>
              }
            />
            <Route
              path="/learning"
              element={
                <RequireAuth>
                  <LearningPage />
                </RequireAuth>
              }
            />
          </>
        )}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
      <Footer />
    </div>
  );
}
