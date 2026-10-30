import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from "axios";
import type { ApiEnvelope, AuthResponse } from "@/types/api";

export const API_URL = (process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080").replace(/\/$/, "");

/** Normalised error for UI code: business code from the backend, or NETWORK_ERROR when the server is unreachable. */
export class ApiError extends Error {
  constructor(public code: string, message: string, public status: number, public fields?: Record<string, string>) {
    super(message);
  }
  get isNetwork() { return this.code === "NETWORK_ERROR"; }
}

// The access token lives only in memory; the refresh token is an HttpOnly cookie the browser JS never sees.
let accessToken: string | null = null;
type Listener = (auth: AuthResponse | null) => void;
const listeners = new Set<Listener>();

export const tokenStore = {
  get: () => accessToken,
  set(auth: AuthResponse | null) {
    accessToken = auth?.accessToken ?? null;
    listeners.forEach((l) => l(auth));
  },
  subscribe(l: Listener) {
    listeners.add(l);
    return () => { listeners.delete(l); };
  },
};

export const http = axios.create({
  baseURL: `${API_URL}/api/v1`,
  withCredentials: true,
  timeout: 20000,
  headers: { "X-Requested-With": "BookMyEvent" },
});

http.interceptors.request.use((config) => {
  if (accessToken) config.headers.set("Authorization", `Bearer ${accessToken}`);
  return config;
});

let refreshing: Promise<AuthResponse | null> | null = null;

/** Rotates the refresh cookie; concurrent callers share one in-flight refresh. */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!refreshing) {
    refreshing = axios
      .post<ApiEnvelope<AuthResponse>>(`${API_URL}/api/v1/auth/refresh`, null, {
        withCredentials: true,
        headers: { "X-Requested-With": "BookMyEvent" },
      })
      .then((r) => { tokenStore.set(r.data.data); return r.data.data; })
      .catch(() => { tokenStore.set(null); return null; })
      .finally(() => { refreshing = null; });
  }
  return refreshing;
}

http.interceptors.response.use(
  (r) => r,
  async (error: AxiosError<ApiEnvelope<unknown>>) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
    const isAuthCall = original?.url?.startsWith("/auth/");
    if (error.response?.status === 401 && original && !original._retried && !isAuthCall && accessToken !== null) {
      original._retried = true;
      const refreshed = await refreshSession();
      if (refreshed) {
        original.headers.set("Authorization", `Bearer ${refreshed.accessToken}`);
        return http(original);
      }
    }
    return Promise.reject(toApiError(error));
  },
);

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  const e = error as AxiosError<ApiEnvelope<unknown>>;
  if (!e.response) {
    return new ApiError("NETWORK_ERROR", "Unable to connect to BookMyEvent servers.", 0);
  }
  const body = e.response.data;
  return new ApiError(body?.error?.code ?? "HTTP_" + e.response.status, body?.error?.message ?? "Something went wrong", e.response.status, body?.error?.fields);
}

/** Unwraps the { success, data } envelope. */
export async function api<T>(config: AxiosRequestConfig): Promise<T> {
  const res = await http.request<ApiEnvelope<T>>(config);
  return res.data.data;
}

export function idempotencyKey(prefix: string) {
  const rand = typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : Math.random().toString(36).slice(2);
  return `${prefix}-${rand}`;
}
