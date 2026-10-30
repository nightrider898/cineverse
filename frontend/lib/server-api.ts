import "server-only";

const BASE = (process.env.API_INTERNAL_URL || process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080").replace(/\/$/, "");

/** Server-side fetch for metadata/SEO. Returns null (never throws) so pages still render if the API is down. */
export async function serverGet<T>(path: string, revalidate = 300): Promise<T | null> {
  try {
    const res = await fetch(`${BASE}/api/v1${path}`, { next: { revalidate }, signal: AbortSignal.timeout(4000) });
    if (!res.ok) return null;
    const body = await res.json();
    return body?.data ?? null;
  } catch {
    return null;
  }
}
