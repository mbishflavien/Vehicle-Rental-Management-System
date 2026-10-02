import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, setUnauthorizedHandler, tokenStore, type AuthResponse, type UserView } from "./api";

interface AuthState {
  user: UserView | null;
  /** True until the stored token has been checked against /api/auth/me. */
  loading: boolean;
  signIn: (res: AuthResponse) => void;
  signOut: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserView | null>(null);
  const [loading, setLoading] = useState(() => tokenStore.get() !== null);

  const signOut = useCallback(() => {
    tokenStore.set(null);
    setUser(null);
  }, []);

  const signIn = useCallback((res: AuthResponse) => {
    tokenStore.set(res.token);
    setUser(res.user);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(signOut);
    if (!tokenStore.get()) return;
    api.me()
      .then(setUser)
      .catch(() => signOut())
      .finally(() => setLoading(false));
  }, [signOut]);

  const value = useMemo(() => ({ user, loading, signIn, signOut }), [user, loading, signIn, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
