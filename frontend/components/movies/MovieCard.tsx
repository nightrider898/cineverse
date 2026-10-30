"use client";

import Link from "next/link";
import { useState } from "react";
import { motion } from "framer-motion";
import { Play, Ticket } from "lucide-react";
import type { Movie } from "@/types/api";
import { Poster } from "@/components/ui/Poster";
import { RatingPill } from "@/components/ui/Rating";
import { Badge } from "@/components/ui/Badge";
import { formatLabel, releaseLabel } from "@/lib/format";
import { cn } from "@/lib/cn";

/**
 * Hover (desktop): lift, image zoom, gradient, actions and metadata fade in.
 * Touch: first tap reveals the same overlay, the buttons then act.
 */
export function MovieCard({ movie, onTrailer, className, priority }: { movie: Movie; onTrailer?: (m: Movie) => void; className?: string; priority?: boolean }) {
  const [revealed, setRevealed] = useState(false);
  const comingSoon = movie.status === "COMING_SOON";
  return (
    <motion.article whileHover={{ y: -6 }} transition={{ type: "spring", stiffness: 300, damping: 22 }}
      className={cn("group relative w-40 shrink-0 sm:w-48 md:w-auto", className)}>
      <div className="relative aspect-[2/3] overflow-hidden rounded-2xl bg-brand-950 shadow-soft transition-shadow duration-300 group-hover:shadow-lift"
        onTouchStart={() => setRevealed(true)}>
        <Link href={`/movies/${movie.id}`} aria-label={`${movie.title} details`} className="absolute inset-0 z-[1]" onClick={(e) => { if (!revealed && matchMedia("(hover: none)").matches) { e.preventDefault(); setRevealed(true); } }} />
        <Poster src={movie.posterUrl} alt={`${movie.title} poster`} priority={priority} className="transition-transform duration-700 ease-out group-hover:scale-110" />
        <div className="absolute left-2 top-2 flex gap-1">
          {movie.formats.includes("IMAX") && <Badge tone="dark">IMAX</Badge>}
          {movie.formats.includes("4DX") && <Badge tone="dark">4DX</Badge>}
          {movie.status === "CLASSIC" && <Badge tone="dark">Classic</Badge>}
        </div>
        <div className={cn("pointer-events-none absolute inset-0 bg-gradient-to-t from-brand-950 via-brand-950/40 to-transparent transition-opacity duration-300",
          revealed ? "opacity-100" : "opacity-0 group-hover:opacity-100")} />
        <div className={cn("absolute inset-x-0 bottom-0 z-[2] flex flex-col gap-2 p-3 transition-all duration-300",
          revealed ? "translate-y-0 opacity-100" : "translate-y-3 opacity-0 group-hover:translate-y-0 group-hover:opacity-100 group-focus-within:translate-y-0 group-focus-within:opacity-100")}>
          <p className="line-clamp-2 text-[11px] leading-snug text-white/85">{movie.genres.join(" · ")} · {movie.formats.map(formatLabel).join(", ")}</p>
          <div className="flex gap-1.5">
            {movie.trailerUrl && onTrailer && (
              <button onClick={() => onTrailer(movie)} className="flex flex-1 items-center justify-center gap-1 rounded-lg bg-white/15 py-1.5 text-xs font-semibold text-white ring-1 ring-white/25 backdrop-blur hover:bg-white/25" aria-label={`Play ${movie.title} trailer`}>
                <Play className="size-3.5 fill-white" /> Trailer
              </button>
            )}
            <Link href={comingSoon ? `/movies/${movie.id}` : `/movies/${movie.id}/tickets`} className="flex flex-1 items-center justify-center gap-1 rounded-lg bg-white py-1.5 text-xs font-bold text-brand-700 hover:bg-brand-50">
              <Ticket className="size-3.5" /> {comingSoon ? "Details" : "Book"}
            </Link>
          </div>
        </div>
        {!comingSoon && (
          <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/80 to-transparent px-3 pb-2 pt-6 transition-opacity duration-300 group-hover:opacity-0">
            {movie.rating > 0 || !movie.imdbRating ? <RatingPill rating={movie.rating} votes={movie.votes} dark /> : (
              <span className="inline-flex items-center gap-1.5 text-xs font-bold text-white">
                <span className="rounded bg-amber-400 px-1 text-[10px] font-black text-black">IMDb</span>{movie.imdbRating.toFixed(1)}
              </span>
            )}
          </div>
        )}
      </div>
      <div className="mt-2.5 px-0.5">
        <h3 className="truncate font-display text-[15px] font-bold leading-tight"><Link href={`/movies/${movie.id}`} className="hover:text-brand-700">{movie.title}</Link></h3>
        <p className="mt-0.5 truncate text-xs text-muted">{movie.certificate} · {movie.languages.slice(0, 3).join(", ")}{movie.languages.length > 3 ? ` +${movie.languages.length - 3}` : ""}</p>
        {comingSoon && <p className="mt-0.5 text-xs font-semibold text-brand-700">{movie.releaseDateEstimated ? releaseLabel(movie) : `Releasing ${releaseLabel(movie, true)}`}</p>}
      </div>
    </motion.article>
  );
}
