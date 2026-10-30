"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { catalogApi, eventApi } from "@/services";
import { useCity } from "@/features/location/CityProvider";
import { EventCard } from "./EventCard";
import { Chip } from "@/components/ui/Chip";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/States";
import { calendarDay, todayIST } from "@/lib/format";

type Vertical = "events" | "sports" | "plays" | "activities";
const VERTICAL_ENUM: Record<Vertical, string> = { events: "EVENT", sports: "SPORTS", plays: "PLAY", activities: "ACTIVITY" };

export function EventListing({ vertical, title, subtitle }: { vertical: Vertical; title: string; subtitle: string }) {
  const { citySlug, city } = useCity();
  const [category, setCategory] = useState<string>();
  const [date, setDate] = useState<string>();
  const cats = useQuery({ queryKey: ["event-cats", vertical], queryFn: () => catalogApi.eventCategories(VERTICAL_ENUM[vertical]), staleTime: 3_600_000 });
  const q = useQuery({ queryKey: [vertical, citySlug, category, date], queryFn: () => eventApi.list(vertical, { city: citySlug, category, date }) });
  const days = Array.from({ length: 7 }, (_, i) => {
    const [y, m, d] = todayIST().split("-").map(Number);
    return new Date(Date.UTC(y, m - 1, d + i)).toISOString().slice(0, 10);
  });

  return (
    <div className="pb-20">
      <section className="relative overflow-hidden border-b border-line bg-gradient-to-br from-white via-white to-brand-50">
        <div className="absolute -right-24 -top-24 size-96 rounded-full bg-brand-100/70 blur-3xl" />
        <div className="container-page relative py-12 md:py-16">
          <motion.h1 initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} className="text-4xl font-extrabold text-ink md:text-5xl">{title}</motion.h1>
          <p className="mt-2 max-w-xl text-ink-soft">{subtitle} in {city?.name ?? "your city"}.</p>
        </div>
      </section>
      <div className="container-page mt-6 space-y-3">
        <div className="no-scrollbar flex gap-2 overflow-x-auto">
          <Chip active={!category} onClick={() => setCategory(undefined)}>All</Chip>
          {cats.data?.map((c) => <Chip key={c.code} active={category === c.code} onClick={() => setCategory(category === c.code ? undefined : c.code)}>{c.name}</Chip>)}
        </div>
        <div className="no-scrollbar flex gap-2 overflow-x-auto">
          <Chip active={!date} onClick={() => setDate(undefined)}>Any date</Chip>
          {days.map((d, i) => { const c = calendarDay(d); return <Chip key={d} active={date === d} onClick={() => setDate(date === d ? undefined : d)}>{i === 0 ? "Today" : i === 1 ? "Tomorrow" : `${c.weekday} ${c.day}`}</Chip>; })}
        </div>
      </div>
      <div className="container-page mt-8">
        {q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} />
          : q.isLoading ? <div className="grid grid-cols-2 gap-5 sm:grid-cols-3 lg:grid-cols-5">{Array.from({ length: 10 }).map((_, i) => <Skeleton key={i} className="aspect-[2/3] rounded-2xl" />)}</div>
          : !q.data?.content.length ? <EmptyState title="Nothing scheduled" message={`No ${vertical} match these filters in ${city?.name ?? "your city"} yet. Try another date or city.`} />
          : <div className="grid grid-cols-2 gap-x-5 gap-y-8 sm:grid-cols-3 lg:grid-cols-5">{q.data.content.map((e) => <EventCard key={e.id} event={e} className="w-auto" />)}</div>}
      </div>
    </div>
  );
}
