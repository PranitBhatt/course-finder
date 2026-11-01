import { Link } from 'react-router';
import { StatePanel } from '../../components/feedback/StatePanel';
import buttonStyles from '../../components/feedback/Button.module.css';
import styles from './NotFoundPage.module.css';

export function NotFoundPage() {
  return (
    <main className={styles.page}>
      <StatePanel label="Error · 404" title="This page doesn't exist.">
        <Link to="/" className={buttonStyles.primary}>
          Back to search
        </Link>
      </StatePanel>
    </main>
  );
}
