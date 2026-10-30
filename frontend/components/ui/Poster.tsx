import Image from "next/image";
import { media, isSvg } from "@/lib/media";
import { cn } from "@/lib/cn";

/**
 * next/image wrapper: raster posters (TMDB or uploaded) are optimised & responsive; local SVG art is served as-is.
 * When a title has no poster yet (e.g. TMDB has none, or no TMDB key is configured) a branded title card is shown.
 */
export function Poster({ src, alt, className, sizes = "(max-width: 768px) 45vw, 220px", priority, fill = true, title }: {
  src?: string | null; alt: string; className?: string; sizes?: string; priority?: boolean; fill?: boolean; title?: string;
}) {
  if (!src) {
    const label = title ?? alt.replace(/ poster$/i, "");
    return (
      <div role="img" aria-label={alt || label} className={cn("absolute inset-0 flex flex-col justify-end overflow-hidden bg-gradient-to-br from-brand-700 via-brand-600 to-brand-400 p-3", !fill && "relative aspect-[2/3]", className)}>
        <svg viewBox="0 0 40 40" className="absolute -right-6 -top-6 size-28 opacity-15" aria-hidden><rect x="8" y="10" width="24" height="21" rx="4" fill="#fff" /><circle cx="20" cy="23" r="3" fill="#c2410c" /></svg>
        {label && <span className="line-clamp-4 font-display text-base font-extrabold leading-tight text-white drop-shadow-sm">{label}</span>}
      </div>
    );
  }
  const url = media(src);
  return (
    <Image src={url} alt={alt} fill={fill} sizes={sizes} priority={priority} unoptimized={isSvg(url)}
      className={cn("object-cover", className)} {...(!fill ? { width: 400, height: 600 } : {})} />
  );
}
