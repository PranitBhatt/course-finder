import { useContext } from 'react';
import { AuthContext, type AuthContextValue } from '../context/AuthContext';

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  // Fail loudly if someone forgets to wrap the app in <AuthProvider>, instead of
  // silently behaving like a logged-out user.
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>');
  return value;
}
