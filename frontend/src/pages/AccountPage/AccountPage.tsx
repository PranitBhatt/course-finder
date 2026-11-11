import styles from '../../components/auth/AuthForm.module.css';
import { useAuth } from '../../hooks/useAuth';

/** Always rendered inside <RequireAuth>, so `user` is set by the time we get here. */
export function AccountPage() {
  const { user } = useAuth();
  if (!user) return null;

  return (
    <main className={styles.page}>
      <section className={styles.card}>
        <h1 className={styles.title}>Your account</h1>
        <dl className={styles.details}>
          <dt>Name</dt>
          <dd>{user.displayName}</dd>
          <dt>Email</dt>
          <dd>{user.email}</dd>
          <dt>Member since</dt>
          <dd>{new Date(user.createdAt).toLocaleDateString()}</dd>
        </dl>
      </section>
    </main>
  );
}
