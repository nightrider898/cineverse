"use client";

import Link from "next/link";
import { use, useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { type LucideIcon, Car, ChevronLeft, Info, MapPin, Utensils, Accessibility, Smartphone, Armchair } from "lucide-react";
import { cinemaApi, movieApi } from "@/services";
import { useCity } from "@/features/location/CityProvider";
import { BookingProgress } from "@/components/ui/BookingProgress";
import { Chip } from "@/components/ui/Chip";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/States";
import { calendarDay, formatLabel, inr, time, todayIST } from "@/lib/format";
import type { ShowtimeSlot } from "@/types/api";
import { cn } from "@/lib/cn";

const FACILITY_ICON: Record<string, LucideIcon> = { PARKING: Car, FOOD_COURT: Utensils, WHEELCHAIR: Accessibility, M_TICKET: Smartphone, RECLINER: Armchair };
const AVAIL_STYLE: Record<ShowtimeSlot["availability"], string> = {
  AVAILABLE: "border-emerald-300 text-emerald-700 hover:bg-emerald-50",
  FILLING_FAST: "border-amber-300 text-amber-700 hover:bg-amber-50",
  ALMOST_FULL: "border-rose-300 text-rose-600 hover:bg-rose-50",
  SOLD_OUT: "border-line text-muted/60 line-through cursor-not-allowed",
};

export default function MovieTicketsPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const movieId = Number(id);
  const { citySlug, city } = useCity();
  const [date, setDate] = useState<string | undefined>();
  const [language, setLanguage] = useState<string | undefined>();
  const [format, setFormat] = useState<string | undefined>();
  const [coords, setCoords] = useState<{ lat: number; lng: number }>();

  useEffect(() => {
    // Distance sorting is optional: only use a position the browser already granted.
    navigator.permissions?.query({ name: "geolocation" as PermissionName }).then((p) => {
      if (p.state === "granted") navigator.geolocation.getCurrentPosition((pos) => setCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude }));
    }).catch(() => undefined);
  }, []);

  const q = useQuery({
    queryKey: ["showtimes", movieId, citySlug, date, language, format, coords],
    queryFn: () => movieApi.showtimes(movieId, { city: citySlug, date, language, format, ...coords }),
    refetchInterval: 60_000,
  });
  const data = q.data;
  const dates = useMemo(() => {
    const avail = new Set(data?.availableDates ?? []);
    const start = todayIST();
    const [y, m, d] = start.split("-").map(Number);
    return Array.from({ length: 7 }, (_, i) => {
      const dt = new Date(Date.UTC(y, m - 1, d + i));
      const ymd = dt.toISOString().slice(0, 10);
      return { ymd, enabled: avail.has(ymd), today: i === 0 };
    });
  }, [data?.availableDates]);

  return (
    <div className="pb-16">
      <div className="border-b border-line bg-gradient-to-b from-brand-50/70 to-white">
        <div className="container-page py-5">
          <Link href={`/movies/${movieId}`} className="inline-flex items-center gap-1 text-sm font-semibold text-muted hover:text-brand-700"><ChevronLeft className="size-4" /> Back to movie</Link>
          <div className="mt-3 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
            <div>
              <h1 className="text-2xl font-extrabold md:text-3xl">{data?.movie.title ?? <Skeleton className="h-8 w-64" />}</h1>
              {data && <p className="mt-1 text-sm text-muted">{data.movie.certificate} · {data.movie.genres.join(", ")} · {city?.name}</p>}
            </div>
            <BookingProgress current="Show" className="md:max-w-md" />
          </div>
        </div>
        <div className="container-page no-scrollbar flex gap-2 overflow-x-auto pb-4" role="tablist" aria-label="Choose date">
          {dates.map(({ ymd, enabled, today }) => {
            const c = calendarDay(ymd);
            const active = (date ?? data?.date) === ymd;
            return (
              <button key={ymd} role="tab" aria-selected={active} disabled={!enabled} onClick={() => setDate(ymd)} aria-label={c.long}
                className={cn("flex w-16 shrink-0 flex-col items-center rounded-2xl border py-2 transition",
                  active ? "border-transparent bg-brand-gradient text-white shadow-glow" : enabled ? "border-line bg-white hover:border-brand-300" : "border-transparent bg-transparent text-muted/40")}>
                <span className="text-[11px] font-bold">{today ? "TODAY" : c.weekday}</span>
                <span className="font-display text-xl font-extrabold">{c.day}</span>
                <span className="text-[10px] font-semibold opacity-80">{c.month}</span>
              </button>
            );
          })}
        </div>
      </div>

      <div className="container-page mt-5 flex flex-wrap gap-2">
        {data?.languages.map((l) => <Chip key={l} active={language === l} onClick={() => setLanguage(language === l ? undefined : l)}>{l}</Chip>)}
        <span className="mx-1 hidden w-px bg-line sm:block" />
        {data?.formats.map((f) => <Chip key={f} active={format === f} onClick={() => setFormat(format === f ? undefined : f)}>{formatLabel(f)}</Chip>)}
        <div className="ml-auto hidden items-center gap-4 text-xs text-muted md:flex">
          <span className="flex items-center gap-1.5"><span className="size-2.5 rounded-full bg-emerald-500" />Available</span>
          <span className="flex items-center gap-1.5"><span className="size-2.5 rounded-full bg-amber-500" />Filling fast</span>
          <span className="flex items-center gap-1.5"><span className="size-2.5 rounded-full bg-rose-500" />Almost full</span>
        </div>
      </div>

      <div className="container-page mt-5 space-y-4">
        {q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} />
          : q.isLoading ? Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-36 w-full rounded-3xl" />)
          : !data?.cinemas.length ? <EmptyState title="No shows available" message={`No shows for this date in ${city?.name ?? "your city"}. Try another date or city.`} />
          : data.cinemas.map(({ cinema, shows }, i) => (
            <motion.article key={cinema.id} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.04 }}
              className="rounded-3xl border border-line bg-white p-5 transition hover:shadow-soft md:grid md:grid-cols-[300px_1fr] md:gap-6">
              <div>
                <Link href={`/cinemas/${cinema.id}`} className="font-display text-lg font-bold hover:text-brand-700">{cinema.name}</Link>
                <p className="mt-1 flex items-center gap-1 text-sm text-muted"><MapPin className="size-3.5" />{cinema.locality ?? cinema.city}{cinema.distanceKm != null && ` · ${cinema.distanceKm} km`}</p>
                <div className="mt-3 flex flex-wrap gap-2">
                  {cinema.facilities.map((f) => {
                    const Icon = FACILITY_ICON[f] ?? Info;
                    return <span key={f} title={f.replace("_", " ")} className="grid size-8 place-items-center rounded-lg bg-mist text-ink-soft"><Icon className="size-4" aria-label={f.replace("_", " ").toLowerCase()} /></span>;
                  })}
                  {cinema.allowPayAtVenue && <span className="rounded-lg bg-brand-50 px-2 py-1.5 text-[11px] font-semibold text-brand-700">Pay at venue</span>}
                </div>
              </div>
              <div className="mt-4 flex flex-wrap content-start items-start gap-2.5 md:mt-0">
                {shows.map((s) => (
                  <Link key={s.showId} href={s.availability === "SOLD_OUT" ? "#" : `/booking/${s.showId}/seats`} aria-disabled={s.availability === "SOLD_OUT"}
                    aria-label={`${time(s.startTime)}, ${formatLabel(s.format)}, ${s.language}, from ${inr(s.minPrice)}, ${s.availability.replace("_", " ").toLowerCase()}`}
                    className={cn("group relative min-w-28 rounded-xl border bg-white px-3 py-2 text-center transition hover:-translate-y-0.5", AVAIL_STYLE[s.availability])}>
                    <span className="block text-sm font-bold">{time(s.startTime)}</span>
                    <span className="block text-[10px] font-semibold uppercase tracking-wide opacity-80">{formatLabel(s.format)} · {s.language}</span>
                    <span className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-2 hidden w-44 -translate-x-1/2 rounded-xl bg-ink p-3 text-left text-xs text-white shadow-lift group-hover:block group-focus-visible:block">
                      {s.prices.map((p) => <span key={p.seatType} className="flex justify-between"><span>{p.label}</span><b>{inr(p.price)}</b></span>)}
                      <span className="mt-1 block text-white/60">{s.availableSeats} of {s.totalSeats} seats left · {s.screenName}</span>
                    </span>
                  </Link>
                ))}
              </div>
            </motion.article>
          ))}
      </div>
    </div>
  );
}
