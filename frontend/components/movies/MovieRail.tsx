"use client";

import Link from "next/link";
import { useRef } from "react";
import { motion } from "framer-motion";
import { ChevronLeft, ChevronRight, ChevronRight as Arrow } from "lucide-react";
import type { Movie } from "@/types/api";
import { MovieCard } from "./MovieCard";
import { MovieCardSkeleton } from "@/components/ui/Skeleton";

export function MovieRail({ title, subtitle, movies, loading, href, onTrailer }: {
  title: string; subtitle?: string; movies?: Movie[]; loading?: boolean; href?: string; onTrailer?: (m: Movie) => void;
}) {
  const scroller = useRef<HTMLDivElement>(null);
  const scroll = (dir: number) => scroller.current?.scrollBy({ left: dir * scroller.current.clientWidth * 0.8, behavior: "smooth" });
  if (!loading && (!movies || movies.length === 0)) return null;
  return (
    <motion.section initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, margin: "-80px" }} transition={{ duration: 0.5 }}
      className="py-6" aria-label={title}>
      <div className="container-page mb-4 flex items-end justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold md:text-2xl">{title}</h2>
          {subtitle && <p className="mt-0.5 text-sm text-muted">{subtitle}</p>}
        </div>
        <div className="flex items-center gap-2">
          {href && <Link href={href} className="flex items-center text-sm font-semibold text-brand-700 hover:underline">See all <Arrow className="size-4" /></Link>}
          <div className="hidden gap-1.5 md:flex">
            <button onClick={() => scroll(-1)} className="grid size-9 place-items-center rounded-full border border-line hover:border-brand-300 hover:text-brand-700" aria-label={`Scroll ${title} left`}><ChevronLeft className="size-4" /></button>
            <button onClick={() => scroll(1)} className="grid size-9 place-items-center rounded-full border border-line hover:border-brand-300 hover:text-brand-700" aria-label={`Scroll ${title} right`}><ChevronRight className="size-4" /></button>
          </div>
        </div>
      </div>
      <div ref={scroller} className="no-scrollbar flex snap-x snap-mandatory gap-4 overflow-x-auto scroll-smooth px-4 pb-4 md:gap-5 md:px-6 xl:px-[max(2rem,calc((100vw-1320px)/2+2rem))]">
        {loading
          ? Array.from({ length: 6 }).map((_, i) => <MovieCardSkeleton key={i} />)
          : movies!.map((m, i) => <MovieCard key={m.id} movie={m} onTrailer={onTrailer} priority={i < 2} className="snap-start md:w-52 lg:w-56" />)}
      </div>
    </motion.section>
  );
}
