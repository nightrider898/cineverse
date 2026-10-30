"use client";

import Link from "next/link";
import { use, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Heart, MapPin } from "lucide-react";
import { cinemaApi, engagementApi } from "@/services";
import { useAuth } from "@/features/auth/AuthProvider";
import { Poster } from "@/components/ui/Poster";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { RowSkeleton, Skeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/States";
import { calendarDay, formatLabel, inr, time, todayIST } from "@/lib/format";
import { cn } from "@/lib/cn";

export default function CinemaPage({ params }: { params: Promise<{ id: string }> }) {
  const id = Number(use(params).id);
  const { status } = useAuth();
  const qc = useQueryClient();
  const days = useMemo(() => { const [y, m, d] = todayIST().split("-").map(Number); return Array.from({ length: 5 }, (_, i) => new Date(Date.UTC(y, m - 1, d + i)).toISOString().slice(0, 10)); }, []);
  const [date, setDate] = useState(days[0]);
  const info = useQuery({ queryKey: ["cinema", id], queryFn: () => cinemaApi.detail(id) });
  const shows = useQuery({ queryKey: ["cinema", id, "shows", date], queryFn: () => cinemaApi.showtimes(id, date) });
  const favs = useQuery({ queryKey: ["favorites"], queryFn: engagementApi.favorites, enabled: status === "authenticated" });
  const fav = favs.data?.find((f) => f.targetType === "CINEMA" && f.targetId === id);
  const toggle = useMutation({
    mutationFn: async () => { if (fav) await engagementApi.removeFavorite(fav.id); else await engagementApi.addFavorite("CINEMA", id); },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["favorites"] }),
  });

  if (info.isError) return <div className="container-page py-16"><ErrorState error={info.error} onRetry={() => info.refetch()} /></div>;
  return (
    <div className="pb-24">
      <div className="bg-gradient-to-br from-brand-900 to-brand-700 text-white">
        <div className="container-page py-10">
          {info.data ? (
            <div className="flex flex-wrap items-end justify-between gap-4">
              <div>
                <h1 className="text-3xl font-extrabold md:text-4xl">{info.data.cinema.name}</h1>
                <p className="mt-2 flex items-center gap-1.5 text-white/80"><MapPin className="size-4" />{info.data.cinema.address}</p>
                <div className="mt-3 flex flex-wrap gap-2">{info.data.screens.map((s) => <Badge key={s.id} tone="dark">{s.name} · {s.screenType}</Badge>)}</div>
              </div>
              {status === "authenticated" && <Button variant="light" onClick={() => toggle.mutate()} icon={<Heart className={cn("size-5", fav && "fill-rose-400 text-rose-400")} />}>{fav ? "Favourite" : "Add to favourites"}</Button>}
            </div>
          ) : <Skeleton className="h-20 w-2/3 bg-white/10" />}
        </div>
      </div>
      <div className="container-page no-scrollbar mt-6 flex gap-2 overflow-x-auto">
        {days.map((d, i) => { const c = calendarDay(d); return (
          <button key={d} onClick={() => setDate(d)} aria-pressed={date === d} className={cn("w-16 shrink-0 rounded-2xl border py-2 text-center", date === d ? "border-transparent bg-brand-gradient text-white" : "border-line hover:border-brand-300")}>
            <span className="block text-[11px] font-bold">{i === 0 ? "TODAY" : c.weekday}</span><span className="font-display text-xl font-extrabold">{c.day}</span>
          </button>); })}
      </div>
      <div className="container-page mt-6 space-y-4">
        {shows.isError ? <ErrorState error={shows.error} onRetry={() => shows.refetch()} /> : shows.isLoading ? <RowSkeleton /> :
          !shows.data?.movies.length ? <EmptyState title="No shows available" message="Nothing scheduled for this date." /> :
          shows.data.movies.map(({ movie, shows: slots }) => (
            <article key={movie.id} className="flex gap-4 rounded-3xl border border-line p-4">
              <Link href={`/movies/${movie.id}`} className="relative aspect-[2/3] w-20 shrink-0 overflow-hidden rounded-2xl md:w-24"><Poster src={movie.posterUrl} alt={movie.title} sizes="96px" /></Link>
              <div className="min-w-0">
                <h2 className="font-display text-lg font-bold">{movie.title}</h2>
                <p className="text-sm text-muted">{movie.certificate} · {movie.genres.join(", ")}</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  {slots.map((s) => (
                    <Link key={s.showId} href={`/booking/${s.showId}/seats`} className={cn("rounded-xl border px-3 py-1.5 text-center text-sm transition hover:-translate-y-0.5",
                      s.availability === "SOLD_OUT" ? "pointer-events-none border-line text-muted line-through" : s.availability === "AVAILABLE" ? "border-emerald-300 text-emerald-700" : "border-amber-300 text-amber-700")}>
                      <b>{time(s.startTime)}</b><span className="block text-[10px] uppercase">{formatLabel(s.format)} · {s.language} · {inr(s.minPrice)}+</span>
                    </Link>
                  ))}
                </div>
              </div>
            </article>
          ))}
      </div>
    </div>
  );
}
