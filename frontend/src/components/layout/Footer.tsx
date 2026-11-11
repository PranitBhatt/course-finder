import { Link } from 'react-router';
import styles from './Footer.module.css';

/** Small print on every page, below results and pagination. Quiet, but never hidden. */
export function Footer() {
  return (
    <footer className={styles.footer}>
      <p>
        Course Finder is an independent project and is not affiliated with, endorsed by, or sponsored by Coursera or
        YouTube. Course information is collected automatically and may be outdated or incomplete — always check the
        course page for current details and pricing. This site uses YouTube API Services; see the{' '}
        <a href="https://www.youtube.com/t/terms" target="_blank" rel="noopener noreferrer">
          YouTube Terms of Service
        </a>{' '}
        and the{' '}
        <a href="https://policies.google.com/privacy" target="_blank" rel="noopener noreferrer">
          Google Privacy Policy
        </a>
        .
      </p>
      <nav aria-label="Legal">
        <Link to="/privacy">Privacy</Link>
      </nav>
    </footer>
  );
}
