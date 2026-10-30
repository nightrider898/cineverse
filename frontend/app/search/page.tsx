"use client";

import Link from "next/link";
import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Clapperboard, MapPin, UserRound } from "lucide-react";
import { searchApi } from "@/services";
import { useCity } from "@/features/location/CityProvider";
import { SearchBox } from "@/components/layout/SearchBox";
import { MovieCard } from "@/components/movies/MovieCard";
import { EventCard } from "@/components/events/EventCard";
import { MovieCardSkeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/States";

function Results() {
  const q = useSearchParams().get("q")?.trim() ?? "";
  const { citySlug } = useCity();
  const r = useQuery({ queryKey: ["search-page", q, citySlug], queryFn: () => searchApi.search(q, citySlug, true, 20), enabled: q.length >= 2 });
  const d = r.data;
  const total = d ? d.movies.length + d.events.length + d.cinemas.length + d.people.length + d.venues.length : 0;

  return (
    <div className="container-page py-8 pb-24">
      <SearchBox className="md:hidden" autoFocus={!q} />
      {q.length < 2 ? (
        <EmptyState className="mt-8" title="Search BookMyEvent" message="Find events, venues and experiences." />
      ) : (
        <>
          <h1 className="mt-6 text-2xl font-extrabold md:mt-0">Results for “{q}”</h1>
          {r.isError ? <ErrorState className="mt-6" error={r.error} onRetry={() => r.refetch()} />
            : r.isLoading ? <div className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-6">{Array.from({ length: 6 }).map((_, i) => <MovieCardSkeleton key={i} />)}</div>
            : total === 0 ? <EmptyState className="mt-8" title="No search results" message="Check the spelling or try a broader term." />
            : (
              <div className="mt-6 space-y-10">
                {d!.movies.length > 0 && <Group title="Movies" count={d!.movies.length}><div className="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-6">{d!.movies.map((m) => <MovieCard key={m.id} movie={m} className="w-auto" />)}</div></Group>}
                {d!.events.length > 0 && <Group title="Events" count={d!.events.length}><div className="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-6">{d!.events.map((e) => <EventCard key={e.id} event={e} className="w-auto" />)}</div></Group>}
                {d!.cinemas.length > 0 && <Group title="Cinemas" count={d!.cinemas.length}>
                  <div className="grid gap-3 md:grid-cols-2">{d!.cinemas.map((c) => (
                    <Link key={c.id} href={`/cinemas/${c.id}`} className="flex items-center gap-3 rounded-2xl border border-line p-4 hover:border-brand-300 hover:shadow-soft">
                      <span className="grid size-10 place-items-center rounded-xl bg-brand-50 text-brand-600"><Clapperboard className="size-5" /></span>
                      <span><span className="block font-semibold">{c.name}</span><span className="text-sm text-muted">{c.locality}, {c.city}</span></span>
                    </Link>))}</div>
                </Group>}
                {d!.people.length > 0 && <Group title="People" count={d!.people.length}>
                  <div className="flex flex-wrap gap-3">{d!.people.map((p) => <span key={p.id} className="flex items-center gap-2 rounded-full border border-line px-4 py-2 text-sm font-semibold"><UserRound className="size-4 text-brand-600" />{p.name}</span>)}</div>
                </Group>}
                {d!.venues.length > 0 && <Group title="Venues" count={d!.venues.length}>
                  <div className="grid gap-3 md:grid-cols-2">{d!.venues.map((v) => <div key={v.id} className="flex items-center gap-3 rounded-2xl border border-line p-4"><MapPin className="size-5 text-brand-600" /><span><span className="block font-semibold">{v.name}</span><span className="text-sm text-muted">{v.address}</span></span></div>)}</div>
                </Group>}
              </div>
            )}
        </>
      )}
    </div>
  );
}

function Group({ title, count, children }: { title: string; count: number; children: React.ReactNode }) {
  return <section aria-label={title}><h2 className="mb-4 text-xs font-bold uppercase tracking-[0.2em] text-muted">{title} <span className="text-brand-600">{count}</span></h2>{children}</section>;
}

export default function SearchPage() {
  return <Suspense><Results /></Suspense>;
}
