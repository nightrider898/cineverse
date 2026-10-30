"use client";

import { ExternalLink } from "lucide-react";
import { Modal } from "@/components/ui/Modal";
import { trailerEmbedUrl, youTubeWatchUrl } from "@/lib/youtube";
import type { Movie } from "@/types/api";

/** Plays the official trailer with YouTube's privacy-enhanced embed. */
export function TrailerModal({ movie, onClose }: { movie: Movie | null; onClose: () => void }) {
  return (
    <Modal open={!!movie} onClose={onClose} title={movie ? `${movie.title} · Trailer` : "Trailer"} wide>
      {movie?.trailerUrl && (
        <>
          <div className="aspect-video overflow-hidden rounded-2xl bg-black">
            <iframe src={trailerEmbedUrl(movie.trailerUrl)} title={`${movie.title} trailer`} className="size-full"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
              allowFullScreen referrerPolicy="strict-origin-when-cross-origin" />
          </div>
          <a href={youTubeWatchUrl(movie.trailerUrl)} target="_blank" rel="noopener noreferrer"
            className="mt-3 inline-flex items-center gap-1.5 text-xs font-semibold text-muted hover:text-brand-700">
            Watch on YouTube <ExternalLink className="size-3.5" />
          </a>
        </>
      )}
    </Modal>
  );
}
