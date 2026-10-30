"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { refreshSession, tokenStore } from "@/lib/api-client";
import { authApi } from "@/services";
import type { AuthResponse, User } from "@/types/api";

type Status = "loading" | "authenticated" | "anonymous";

interface AuthContextValue {
  user: User | null;
  status: Status;
  login: (email: string, password: string) => Promise<User>;
  register: (data: { fullName: string; email: string; password: string; phone?: string }) => Promise<User>;
  logout: () => Promise<void>;
  setUser: (u: User) => void;
  hasRole: (...roles: string[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [status, setStatus] = useState<Status>("loading");
  const qc = useQueryClient();

  useEffect(() => {
    const unsub = tokenStore.subscribe((auth: AuthResponse | null) => {
      setUser(auth?.user ?? null);
      setStatus(auth ? "authenticated" : "anonymous");
    });
    // Restore the session from the HttpOnly refresh cookie (if any).
    refreshSession().then((auth) => {
      if (!auth) setStatus("anonymous");
    });
    return unsub;
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const auth = await authApi.login(email, password);
    tokenStore.set(auth);
    qc.invalidateQueries();
    return auth.user;
  }, [qc]);

  const register = useCallback(async (data: { fullName: string; email: string; password: string; phone?: string }) => {
    const auth = await authApi.register(data);
    tokenStore.set(auth);
    return auth.user;
  }, []);

  const logout = useCallback(async () => {
    try { await authApi.logout(); } catch { /* the cookie is cleared server-side when reachable */ }
    tokenStore.set(null);
    qc.clear();
  }, [qc]);

  const value = useMemo<AuthContextValue>(() => ({
    user, status, login, register, logout,
    setUser: (u) => setUser(u),
    hasRole: (...roles) => !!user && roles.some((r) => user.roles.includes(r)),
  }), [user, status, login, register, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
