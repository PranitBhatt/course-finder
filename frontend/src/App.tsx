import { Route, Routes } from 'react-router';
import { Header } from './components/layout/Header';
import { useTheme } from './hooks/useTheme';
import { NotFoundPage } from './pages/NotFoundPage/NotFoundPage';
import { SearchPage } from './pages/SearchPage/SearchPage';
import styles from './App.module.css';

export function App() {
  const { theme, toggleTheme } = useTheme();

  return (
    <div className={styles.shell}>
      <Header theme={theme} onToggleTheme={toggleTheme} />
      <Routes>
        <Route path="/" element={<SearchPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </div>
  );
}
