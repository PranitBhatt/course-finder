import { useEffect } from 'react';
import { SITE_NAME } from '../../utils/site';
import styles from './PrivacyPage.module.css';

/** Plain-language summary of what we store. Factual only: no promises we can't back up. */
export function PrivacyPage() {
  useEffect(() => {
    document.title = `Privacy | ${SITE_NAME}`;
  }, []);

  return (
    <main className={styles.page}>
      <article className={styles.card}>
        <h1 className={styles.title}>Privacy</h1>

        <section>
          <h2>If you search without an account</h2>
          <p>
            You can search without signing in. Your searches are not linked to an account. To see which topics are
            popular, we keep the search text itself, without your name or email.
          </p>
        </section>

        <section>
          <h2>If you sign in</h2>
          <p>For signed-in users we store:</p>
          <ul>
            <li>your email address and display name;</li>
            <li>your password, only as a one-way hash (we can't read it);</li>
            <li>
              your login sessions, including the device/browser description and IP address of each one, so sessions
              can be kept safe and signed out;
            </li>
            <li>the courses you track in My Learning, with their status, progress and any notes you write.</li>
          </ul>
        </section>

        <section>
          <h2>Other services</h2>
          <p>
            Course results come from Coursera and YouTube. YouTube results use YouTube API Services, covered by the{' '}
            <a href="https://www.youtube.com/t/terms" target="_blank" rel="noopener noreferrer">
              YouTube Terms of Service
            </a>{' '}
            and the{' '}
            <a href="https://policies.google.com/privacy" target="_blank" rel="noopener noreferrer">
              Google Privacy Policy
            </a>
            . Opening a course takes you to that platform, whose own privacy policy then applies.
          </p>
        </section>
      </article>
    </main>
  );
}
