"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { AnimatePresence, motion, useMotionValue, useReducedMotion, useSpring, useTransform } from "framer-motion";
import { Clock, Play, Star, Ticket } from "lucide-react";
import type { Movie } from "@/types/api";
import { Poster } from "@/components/ui/Poster";
import { duration } from "@/lib/format";
import { Skeleton } from "@/components/ui/Skeleton";

// Three.js is only fetched on the home page, only on capable devices, and never on the server.
const HeroParticles = dynamic(() => import("./HeroParticles"), { ssr: false, loading: () => null });

function useCapableDevice() {
  const reduce = useReducedMotion();
  const [ok, setOk] = useState(false);
  useEffect(() => {
    const nav = navigator as Navigator & { connection?: { saveData?: boolean }; deviceMemory?: number };
    const strong = (nav.hardwareConcurrency ?? 2) >= 4 && (nav.deviceMemory ?? 4) >= 4 && !nav.connection?.saveData;
    setOk(!reduce && strong && window.matchMedia("(min-width: 768px)").matches);
  }, [reduce]);
  return ok;
}

export function HeroCarousel({ movies, loading, onTrailer }: { movies?: Movie[]; loading?: boolean; onTrailer: (m: Movie) => void }) {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const capable = useCapableDevice();
  const list = movies ?? [];
  const next = useCallback(() => setIndex((i) => (list.length ? (i + 1) % list.length : 0)), [list.length]);

  useEffect(() => {
    if (paused || list.length < 2) return;
    const id = setInterval(next, 7000);
    return () => clearInterval(id);
  }, [paused, next, list.length]);

  // Parallax tilt for the floating poster.
  const mx = useMotionValue(0), my = useMotionValue(0);
  const rx = useSpring(useTransform(my, [-0.5, 0.5], [10, -10]), { stiffness: 120, damping: 14 });
  const ry = useSpring(useTransform(mx, [-0.5, 0.5], [-14, 14]), { stiffness: 120, damping: 14 });
  const bgX = useSpring(useTransform(mx, [-0.5, 0.5], [18, -18]), { stiffness: 80, damping: 20 });

  if (loading) return <div className="container-page pt-4"><Skeleton className="h-[440px] w-full rounded-[2rem] md:h-[560px]" /></div>;
  if (!list.length) return null;
  const m = list[index % list.length];

  return (
    <section aria-roledescription="carousel" aria-label="Featured movies" className="container-page pt-3 md:pt-5"
      onMouseEnter={() => setPaused(true)} onMouseLeave={() => { setPaused(false); mx.set(0); my.set(0); }}
      onMouseMove={(e) => { const r = e.currentTarget.getBoundingClientRect(); mx.set((e.clientX - r.left) / r.width - 0.5); my.set((e.clientY - r.top) / r.height - 0.5); }}>
      <div className="relative h-[520px] overflow-hidden rounded-[2rem] bg-brand-950 shadow-lift md:h-[560px]">
        <AnimatePresence mode="sync">
          <motion.div key={m.id} className="absolute inset-[-24px]" style={{ x: bgX }}
            initial={{ opacity: 0, scale: 1.08 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0 }} transition={{ duration: 1.1, ease: [0.22, 1, 0.36, 1] }}>
            <Poster src={m.backdropUrl ?? m.posterUrl} alt="" sizes="100vw" priority className="animate-[float_18s_ease-in-out_infinite]" />
          </motion.div>
        </AnimatePresence>
        <div className="absolute inset-0 bg-gradient-to-r from-brand-950 via-brand-950/80 to-brand-950/10" />
        <div className="absolute inset-0 bg-gradient-to-t from-brand-950/90 via-transparent to-transparent" />
        {capable && <div className="pointer-events-none absolute inset-0 opacity-70"><HeroParticles /></div>}

        <div className="relative z-10 flex h-full items-end gap-10 p-6 pb-16 md:items-center md:p-12 lg:p-16">
          <AnimatePresence mode="wait">
            <motion.div key={m.id} className="max-w-xl text-white" initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -12 }} transition={{ duration: 0.5, ease: "easeOut" }}>
              <div className="flex flex-wrap items-center gap-2 text-xs font-semibold">
                <span className="rounded-full bg-white/15 px-3 py-1 ring-1 ring-white/20 backdrop-blur">{m.status === "COMING_SOON" ? "Coming soon" : m.status === "CLASSIC" ? "Classic re-release" : "Now showing"}</span>
                {m.formats.filter((f) => f !== "2D").slice(0, 3).map((f) => <span key={f} className="rounded-full bg-brand-500/40 px-3 py-1 ring-1 ring-brand-300/40">{f.replace("_", " ")}</span>)}
              </div>
              <h1 className="mt-4 text-4xl font-extrabold leading-[1.05] md:text-6xl">{m.title}</h1>
              {m.tagline && <p className="mt-3 text-base text-white/80 md:text-lg">{m.tagline}</p>}
              <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-white/85">
                {m.rating > 0 && <span className="flex items-center gap-1 font-bold"><Star className="size-4 fill-amber-400 text-amber-400" />{m.rating.toFixed(1)}/5</span>}
                <span className="flex items-center gap-1"><Clock className="size-4" />{duration(m.durationMin)}</span>
                <span>{m.certificate}</span>
                <span>{m.genres.join(" · ")}</span>
              </div>
              <p className="mt-2 text-sm text-white/70">{m.languages.join(" · ")}</p>
              <div className="mt-7 flex flex-wrap gap-3">
                <Link href={`/movies/${m.id}/tickets`} className="inline-flex h-12 items-center gap-2 rounded-2xl bg-white px-6 font-bold text-brand-700 shadow-lift transition hover:-translate-y-0.5">
                  <Ticket className="size-5" /> Book Tickets
                </Link>
                {m.trailerUrl && (
                  <button onClick={() => onTrailer(m)} className="inline-flex h-12 items-center gap-2 rounded-2xl bg-white/10 px-6 font-semibold text-white ring-1 ring-white/30 backdrop-blur transition hover:bg-white/20">
                    <Play className="size-5 fill-white" /> Watch Trailer
                  </button>
                )}
              </div>
            </motion.div>
          </AnimatePresence>

          <div className="ml-auto hidden [perspective:1200px] lg:block">
            <motion.div key={m.id} style={{ rotateX: rx, rotateY: ry, transformStyle: "preserve-3d" }}
              initial={{ opacity: 0, rotateY: -25, x: 40 }} animate={{ opacity: 1, rotateY: 0, x: 0 }} transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1] }}
              className="relative h-[400px] w-[268px] animate-[float_7s_ease-in-out_infinite]">
              <div className="absolute inset-0 overflow-hidden rounded-3xl shadow-[0_40px_80px_-20px_rgba(0,0,0,0.7)] ring-1 ring-white/20" style={{ transform: "translateZ(40px)" }}>
                <Poster src={m.posterUrl} alt={`${m.title} poster`} sizes="280px" priority />
                <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/10 to-white/25" />
              </div>
              <div className="absolute -inset-6 -z-10 rounded-[2.5rem] bg-brand-500/30 blur-3xl" />
            </motion.div>
          </div>
        </div>

        <div className="absolute bottom-5 left-6 z-10 flex gap-2 md:left-12 lg:left-16" role="tablist" aria-label="Choose featured movie">
          {list.map((mv, i) => (
            <button key={mv.id} role="tab" aria-selected={i === index} aria-label={mv.title} onClick={() => setIndex(i)}
              className={`h-1.5 rounded-full transition-all duration-500 ${i === index ? "w-10 bg-white" : "w-4 bg-white/35 hover:bg-white/60"}`} />
          ))}
        </div>
      </div>
    </section>
  );
}
