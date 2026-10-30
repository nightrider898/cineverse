const MEDIA_BASE = (process.env.NEXT_PUBLIC_MEDIA_BASE_URL || "").replace(/\/$/, "");

/** Resolves media paths returned by the API ("/media/posters/x.svg") against a configurable CDN/base. */
export function media(path?: string | null, fallback = "/media/og-default.svg") {
  if (!path) return fallback;
  if (/^https?:\/\//.test(path)) return path;
  return `${MEDIA_BASE}${path}`;
}

export const isSvg = (src: string) => src.split("?")[0].endsWith(".svg");
