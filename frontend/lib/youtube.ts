/** Accepts any YouTube link shape (watch, youtu.be, shorts, embed) and returns the 11-character video id. */
export function youTubeId(url?: string | null): string | null {
  if (!url) return null;
  const s = url.trim();
  if (/^[A-Za-z0-9_-]{11}$/.test(s)) return s;
  const m = s.match(/(?:youtube(?:-nocookie)?\.com\/(?:embed|shorts|live|v)\/|youtube\.com\/watch\?(?:.*&)?v=|youtu\.be\/)([A-Za-z0-9_-]{11})/);
  return m ? m[1] : null;
}

/** Embeddable player URL; non-YouTube URLs are returned unchanged. */
export function trailerEmbedUrl(url: string, autoplay = true): string {
  const id = youTubeId(url);
  if (!id) return url;
  const params = new URLSearchParams({ rel: "0", modestbranding: "1", playsinline: "1", ...(autoplay ? { autoplay: "1" } : {}) });
  return `https://www.youtube-nocookie.com/embed/${id}?${params}`;
}

export const youTubeWatchUrl = (url: string) => {
  const id = youTubeId(url);
  return id ? `https://www.youtube.com/watch?v=${id}` : url;
};
