"use client";

import Link from "next/link";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Bookmark, BookmarkCheck, CalendarDays, Clock, Heart, Play, Share2, Star, Ticket } from "lucide-react";
import { toast } from "sonner";
import { engagementApi, movieApi } from "@/services";
import { useAuth } from "@/features/auth/AuthProvider";
import type { MovieDetail } from "@/types/api";
import { Poster } from "@/components/ui/Poster";
import { Badge } from "@/components/ui/Badge";
import { Button, ButtonLink } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorState } from "@/components/ui/States";
import { TrailerModal } from "@/components/movies/TrailerModal";
import { MovieRail } from "@/components/movies/MovieRail";
import { Field, Input, Textarea } from "@/components/ui/Field";
import { ApiError } from "@/lib/api-client";
import { compact, date, duration, formatLabel, releaseLabel } from "@/lib/format";
import { youTubeId } from "@/lib/youtube";
import { cn } from "@/lib/cn";

export function MovieDetailView({ id, initial }: { id: number; initial?: MovieDetail }) {
  const { status } = useAuth();
  const qc = useQueryClient();
  const [trailerOpen, setTrailerOpen] = useState(false);
  const detail = useQuery({ queryKey: ["movie", id], queryFn: () => movieApi.detail(id), initialData: initial });
  const similar = useQuery({ queryKey: ["movie", id, "similar"], queryFn: () => movieApi.similar(id) });
  const reviews = useQuery({ queryKey: ["movie", id, "reviews"], queryFn: () => movieApi.reviews(id) });
  const watchlist = useQuery({ queryKey: ["watchlist"], queryFn: engagementApi.watchlist, enabled: status === "authenticated" });
  const favorites = useQuery({ queryKey: ["favorites"], queryFn: engagementApi.favorites, enabled: status === "authenticated" });

  const inWatchlist = watchlist.data?.some((w) => w.movie.id === id);
  const favorite = favorites.data?.find((f) => f.targetType === "MOVIE" && f.targetId === id);

  const toggleWatch = useMutation({
    mutationFn: async () => { if (inWatchlist) await engagementApi.removeWatchlist(id); else await engagementApi.addWatchlist(id); },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["watchlist"] }); toast.success(inWatchlist ? "Removed from watchlist" : "Added to watchlist — we'll remind you on release"); },
    onError: (e: ApiError) => toast.error(e.message),
  });
  const toggleFav = useMutation({
    mutationFn: async () => { if (favorite) await engagementApi.removeFavorite(favorite.id); else await engagementApi.addFavorite("MOVIE", id); },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["favorites"] }),
  });

  const requireLogin = (fn: () => void) => () => (status === "authenticated" ? fn() : toast.info("Sign in to save movies", { action: { label: "Sign in", onClick: () => (window.location.href = `/login?next=/movies/${id}`) } }));

  async function share() {
    const url = window.location.href;
    const title = detail.data?.movie.title ?? "BookMyEvent";
    if (navigator.share) { try { await navigator.share({ title, url }); } catch { /* dismissed */ } }
    else { await navigator.clipboard.writeText(url); toast.success("Link copied"); }
  }

  if (detail.isError) return <div className="container-page py-16"><ErrorState error={detail.error} onRetry={() => detail.refetch()} /></div>;
  if (!detail.data) return <div className="container-page py-6"><Skeleton className="h-[480px] w-full rounded-[2rem]" /></div>;
  const d = detail.data, m = d.movie;
  const comingSoon = m.status === "COMING_SOON";

  return (
    <div className="pb-28 md:pb-10">
      <section className="relative isolate overflow-hidden bg-brand-950 text-white">
        <div className="absolute inset-0 -z-10 opacity-60"><Poster src={m.backdropUrl} alt="" sizes="100vw" priority /></div>
        <div className="absolute inset-0 -z-10 bg-gradient-to-r from-brand-950 via-brand-950/85 to-brand-950/40" />
        <div className="container-page grid gap-8 py-8 md:grid-cols-[260px_1fr] md:py-14">
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="relative mx-auto aspect-[2/3] w-48 overflow-hidden rounded-3xl shadow-[0_30px_60px_-20px_rgba(0,0,0,0.7)] ring-1 ring-white/15 md:w-full">
            <Poster src={m.posterUrl} alt={`${m.title} poster`} priority sizes="260px" />
            {m.trailerUrl && (
              <button onClick={() => setTrailerOpen(true)} className="absolute inset-x-3 bottom-3 flex items-center justify-center gap-2 rounded-xl bg-black/55 py-2 text-sm font-semibold backdrop-blur hover:bg-black/70">
                <Play className="size-4 fill-white" /> Trailer
              </button>
            )}
          </motion.div>
          <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.08 }} className="flex flex-col justify-center">
            <div className="flex flex-wrap gap-2">
              {m.formats.map((f) => <span key={f} className="rounded-lg bg-white/15 px-2.5 py-1 text-xs font-bold ring-1 ring-white/20">{formatLabel(f)}</span>)}
            </div>
            <h1 className="mt-4 text-4xl font-extrabold md:text-5xl">{m.title}</h1>
            {m.tagline && <p className="mt-2 text-lg text-white/75">{m.tagline}</p>}
            {m.rating > 0 ? (
              <div className="mt-5 inline-flex w-fit items-center gap-3 rounded-2xl bg-white/10 px-4 py-3 ring-1 ring-white/15 backdrop-blur">
                <Star className="size-6 fill-amber-400 text-amber-400" />
                <div><p className="text-xl font-extrabold">{m.rating.toFixed(1)}/5</p><p className="text-xs text-white/70">{compact(m.votes)} ratings</p></div>
              </div>
            ) : m.imdbRating ? (
              <div className="mt-5 inline-flex w-fit items-center gap-3 rounded-2xl bg-white/10 px-4 py-3 ring-1 ring-white/15 backdrop-blur">
                <span className="rounded-md bg-amber-400 px-1.5 py-0.5 text-xs font-black tracking-tight text-black">IMDb</span>
                <div><p className="text-xl font-extrabold">{m.imdbRating.toFixed(1)}/10</p>{m.imdbVotes ? <p className="text-xs text-white/70">{compact(m.imdbVotes)} votes</p> : null}</div>
              </div>
            ) : <p className="mt-5 text-sm text-white/70">{comingSoon ? "Releasing soon" : "Be the first to rate after watching"}</p>}
            <div className="mt-5 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-white/85">
              <span className="flex items-center gap-1.5"><Clock className="size-4" />{duration(m.durationMin)}</span>
              <span>{m.genres.join(", ")}</span>
              <Badge tone="dark">{m.certificate}</Badge>
              <span className="flex items-center gap-1.5"><CalendarDays className="size-4" />{releaseLabel(m)}</span>
              {m.status === "CLASSIC" && <Badge tone="dark">Classic re-release</Badge>}
            </div>
            <p className="mt-2 text-sm text-white/70">{m.languages.join(" · ")}</p>
            <div className="mt-7 flex flex-wrap gap-3">
              {!comingSoon && <ButtonLink href={`/movies/${m.id}/tickets`} size="lg" className="hidden md:inline-flex" icon={<Ticket className="size-5" />}>Book Tickets</ButtonLink>}
              {m.trailerUrl && <Button variant="light" size="lg" onClick={() => setTrailerOpen(true)} icon={<Play className="size-5 fill-white" />}>Watch Trailer</Button>}
              <Button variant="light" size="lg" onClick={requireLogin(() => toggleWatch.mutate())} loading={toggleWatch.isPending}
                icon={inWatchlist ? <BookmarkCheck className="size-5" /> : <Bookmark className="size-5" />} aria-pressed={!!inWatchlist}>
                {inWatchlist ? "In watchlist" : "Add to Watchlist"}
              </Button>
              <Button variant="light" size="lg" onClick={requireLogin(() => toggleFav.mutate())} aria-label={favorite ? "Remove from favorites" : "Add to favorites"} aria-pressed={!!favorite}>
                <Heart className={cn("size-5", favorite && "fill-rose-500 text-rose-500")} />
              </Button>
              <Button variant="light" size="lg" onClick={share} aria-label="Share"><Share2 className="size-5" /></Button>
            </div>
          </motion.div>
        </div>
      </section>

      <div className="container-page grid gap-12 py-10 lg:grid-cols-[1fr_340px]">
        <div className="min-w-0 space-y-12">
          <section aria-labelledby="about"><h2 id="about" className="text-2xl font-bold">About the movie</h2><p className="mt-3 leading-relaxed text-ink-soft">{d.synopsis}</p></section>

          <PeopleSection title="Cast" people={d.cast.map((c) => ({ key: c.actorId + c.name, name: c.name, sub: c.character ? `as ${c.character}` : "Actor", photo: c.photoUrl }))} />
          <PeopleSection title="Crew" people={d.crew.map((c) => ({ key: c.role + c.name, name: c.name, sub: c.role, photo: c.photoUrl }))} />

          {m.trailerUrl && (
            <section aria-labelledby="trailers">
              <h2 id="trailers" className="text-2xl font-bold">Trailers</h2>
              <button onClick={() => setTrailerOpen(true)} className="group relative mt-4 block aspect-video w-full max-w-xl overflow-hidden rounded-2xl">
                <Poster src={m.backdropUrl ?? (youTubeId(m.trailerUrl) ? `https://i.ytimg.com/vi/${youTubeId(m.trailerUrl)}/hqdefault.jpg` : null)}
                  alt={`${m.title} trailer thumbnail`} title={m.title} sizes="600px" className="transition-transform duration-700 group-hover:scale-105" />
                <span className="absolute inset-0 grid place-items-center bg-black/30"><span className="grid size-16 place-items-center rounded-full bg-white/90 text-brand-700 shadow-lift transition group-hover:scale-110"><Play className="size-7 fill-current" /></span></span>
              </button>
            </section>
          )}

          {d.photos.length > 0 && (
            <section aria-labelledby="photos">
              <h2 id="photos" className="text-2xl font-bold">Photos</h2>
              <div className="no-scrollbar mt-4 flex gap-3 overflow-x-auto">
                {d.photos.map((p, i) => (
                  <div key={p} className="relative aspect-video w-72 shrink-0 overflow-hidden rounded-2xl">
                    <Poster src={p} alt={`${m.title} still ${i + 1}`} sizes="300px" />
                  </div>
                ))}
              </div>
            </section>
          )}

          <section aria-labelledby="reviews">
            <div className="flex items-center justify-between">
              <h2 id="reviews" className="text-2xl font-bold">Reviews</h2>
              <span className="text-sm text-muted">{reviews.data?.totalElements ?? 0} verified reviews</span>
            </div>
            <ReviewForm movieId={m.id} disabled={comingSoon} />
            <div className="mt-5 grid gap-4 sm:grid-cols-2">
              {reviews.data?.content.map((r) => (
                <article key={r.id} className="rounded-2xl border border-line p-4">
                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-2 text-sm font-semibold">
                      <span className="grid size-8 place-items-center rounded-full bg-brand-50 text-xs font-bold text-brand-700">{r.userName[0]}</span>{r.userName}
                    </span>
                    <span className="flex items-center gap-1 text-sm font-bold"><Star className="size-4 fill-amber-400 text-amber-400" />{r.rating}/5</span>
                  </div>
                  {r.title && <h3 className="mt-3 font-semibold">{r.title}</h3>}
                  {r.body && <p className="mt-1 text-sm leading-relaxed text-ink-soft">{r.body}</p>}
                  <p className="mt-3 text-xs text-muted">{date(r.createdAt)}{r.verifiedBooking && " · Booked on BookMyEvent"}</p>
                </article>
              ))}
            </div>
          </section>
        </div>

        <aside className="space-y-6">
          <div className="rounded-3xl border border-line p-6">
            <h2 className="text-lg font-bold">Ratings</h2>
            <div className="mt-4 flex items-end gap-2">
              <span className="font-display text-5xl font-extrabold">{d.ratingSummary.average ? Number(d.ratingSummary.average).toFixed(1) : "–"}</span>
              <span className="pb-2 text-sm text-muted">/ 5 · {d.ratingSummary.totalVotes} votes</span>
            </div>
            <div className="mt-4 space-y-2">
              {[5, 4, 3, 2, 1].map((s) => {
                const n = d.ratingSummary.distribution[String(s)] ?? 0;
                const pct = d.ratingSummary.totalVotes ? (n / d.ratingSummary.totalVotes) * 100 : 0;
                return (
                  <div key={s} className="flex items-center gap-3 text-xs">
                    <span className="w-6 font-semibold">{s}★</span>
                    <div className="h-2 flex-1 overflow-hidden rounded-full bg-mist"><motion.div className="h-full bg-brand-gradient" initial={{ width: 0 }} whileInView={{ width: `${pct}%` }} viewport={{ once: true }} transition={{ duration: 0.8 }} /></div>
                    <span className="w-6 text-right text-muted">{n}</span>
                  </div>
                );
              })}
            </div>
            <p className="mt-4 text-xs text-muted">Ratings are calculated by BookMyEvent from moderated reviews.</p>
          </div>
        </aside>
      </div>

      <MovieRail title="You might also like" movies={similar.data} />

      {!comingSoon && (
        <div className="glass fixed inset-x-0 bottom-0 z-40 border-t border-line p-3 pb-[max(0.75rem,env(safe-area-inset-bottom))] md:hidden">
          <ButtonLink href={`/movies/${m.id}/tickets`} size="lg" className="w-full" icon={<Ticket className="size-5" />}>Book Tickets</ButtonLink>
        </div>
      )}
      <TrailerModal movie={trailerOpen ? m : null} onClose={() => setTrailerOpen(false)} />
    </div>
  );
}

function PeopleSection({ title, people }: { title: string; people: { key: string; name: string; sub: string; photo?: string }[] }) {
  if (!people.length) return null;
  return (
    <section aria-label={title}>
      <h2 className="text-2xl font-bold">{title}</h2>
      <div className="no-scrollbar mt-4 flex gap-5 overflow-x-auto pb-2">
        {people.map((p) => (
          <div key={p.key} className="w-24 shrink-0 text-center">
            <div className="relative mx-auto grid size-20 place-items-center overflow-hidden rounded-full bg-gradient-to-br from-brand-100 to-brand-200 font-display text-xl font-bold text-brand-700 ring-4 ring-white shadow-soft">
              {p.photo ? <Poster src={p.photo} alt="" sizes="80px" /> : p.name.split(" ").map((x) => x[0]).slice(0, 2).join("")}
            </div>
            <p className="mt-2 text-sm font-semibold leading-tight">{p.name}</p>
            <p className="mt-0.5 text-xs text-muted">{p.sub}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

function ReviewForm({ movieId, disabled }: { movieId: number; disabled?: boolean }) {
  const { status } = useAuth();
  const [open, setOpen] = useState(false);
  const [rating, setRating] = useState(0);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const submit = useMutation({
    mutationFn: () => engagementApi.review({ movieId, rating, title: title || undefined, body: body || undefined }),
    onSuccess: () => { toast.success("Thanks! Your review will appear after moderation."); setOpen(false); },
    onError: (e: ApiError) => toast.error(e.message),
  });
  if (disabled) return null;
  if (!open) {
    return (
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-mist p-4">
        <p className="text-sm text-ink-soft">Watched it with a BookMyEvent ticket? Tell others what you thought.</p>
        {status === "authenticated" ? <Button size="sm" variant="secondary" onClick={() => setOpen(true)}>Rate & review</Button>
          : <ButtonLink size="sm" variant="secondary" href={`/login?next=/movies/${movieId}`}>Sign in to review</ButtonLink>}
      </div>
    );
  }
  return (
    <form className="mt-4 space-y-4 rounded-2xl border border-line p-5" onSubmit={(e) => { e.preventDefault(); if (rating) submit.mutate(); }}>
      <fieldset>
        <legend className="mb-2 text-sm font-semibold">Your rating</legend>
        <div className="flex gap-1" role="radiogroup">
          {[1, 2, 3, 4, 5].map((s) => (
            <button key={s} type="button" role="radio" aria-checked={rating === s} aria-label={`${s} star${s > 1 ? "s" : ""}`} onClick={() => setRating(s)}>
              <Star className={cn("size-8 transition", s <= rating ? "fill-amber-400 text-amber-400" : "text-line hover:text-amber-300")} />
            </button>
          ))}
        </div>
      </fieldset>
      <Field label="Headline" htmlFor="rv-title"><Input id="rv-title" maxLength={120} value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
      <Field label="Review" htmlFor="rv-body"><Textarea id="rv-body" maxLength={2000} value={body} onChange={(e) => setBody(e.target.value)} /></Field>
      <div className="flex gap-2">
        <Button type="submit" disabled={!rating} loading={submit.isPending}>Submit review</Button>
        <Button type="button" variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
      </div>
    </form>
  );
}
