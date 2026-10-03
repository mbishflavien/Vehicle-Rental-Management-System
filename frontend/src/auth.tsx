import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, setUnauthorizedHandler, tokenStore, type AuthResponse, type Permission, type UserView } from "./api";

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

/** Whether the signed-in user holds a permission. The API enforces the same rules; this only hides UI. */
export function useCan(): (permission: Permission) => boolean {
  const { user } = useAuth();
  return (permission) => !!user?.permissions?.includes(permission);
}

export const isStaff = (user: UserView | null) => user?.role === "ADMIN" || user?.role === "AGENT";

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
