"use client";

import { Suspense, useMemo, useState } from "react";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { useRouter, useSearchParams } from "next/navigation";
import { SlidersHorizontal, X } from "lucide-react";
import { catalogApi, movieApi } from "@/services";
import { useCity } from "@/features/location/CityProvider";
import { MovieCard } from "@/components/movies/MovieCard";
import { TrailerModal } from "@/components/movies/TrailerModal";
import { MovieCardSkeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/States";
import { Tabs } from "@/components/ui/Tabs";
import { Chip } from "@/components/ui/Chip";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Field";
import { formatLabel } from "@/lib/format";
import type { Movie } from "@/types/api";
import { cn } from "@/lib/cn";

const LANGUAGES = ["Telugu", "Hindi", "Tamil", "Malayalam", "Kannada", "English", "Japanese", "Korean", "French"];
const FORMATS = ["2D", "3D", "IMAX", "4DX", "DOLBY_ATMOS"];
const RATINGS = [{ v: "4", l: "4★ & up" }, { v: "3", l: "3★ & up" }];
const SORTS = [{ v: "popularity", l: "Popularity" }, { v: "rating,desc", l: "Top rated" }, { v: "releasedate,desc", l: "Latest release" }, { v: "title", l: "A – Z" }];

function MoviesInner() {
  const params = useSearchParams();
  const router = useRouter();
  const { citySlug, city } = useCity();
  const [sheet, setSheet] = useState(false);
  const [trailer, setTrailer] = useState<Movie | null>(null);
  const status = params.get("status") ?? "NOW_SHOWING";
  const filters = {
    language: params.get("language") ?? undefined,
    genre: params.get("genre") ?? undefined,
    format: params.get("format") ?? undefined,
    minRating: params.get("rating") ? Number(params.get("rating")) : undefined,
    sort: params.get("sort") ?? "popularity",
  };
  const { data: genres = [] } = useQuery({ queryKey: ["genres"], queryFn: catalogApi.genres, staleTime: 3_600_000 });

  const set = (key: string, value?: string) => {
    const next = new URLSearchParams(params.toString());
    if (!value || next.get(key) === value) next.delete(key); else next.set(key, value);
    router.replace(`/movies?${next.toString()}`, { scroll: false });
  };

  const q = useInfiniteQuery({
    queryKey: ["movies", citySlug, status, filters],
    queryFn: ({ pageParam }) => movieApi.list({ city: status === "NOW_SHOWING" ? citySlug : undefined, status, ...filters, page: pageParam, size: 18 }),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
  const movies = useMemo(() => q.data?.pages.flatMap((p) => p.content) ?? [], [q.data]);
  const activeCount = [filters.language, filters.genre, filters.format, filters.minRating].filter(Boolean).length;

  const FilterPanel = (
    <div className="space-y-6">
      <FilterGroup title="Languages">{LANGUAGES.map((l) => <Chip key={l} active={filters.language === l} onClick={() => set("language", l)}>{l}</Chip>)}</FilterGroup>
      <FilterGroup title="Genres">{genres.map((g) => <Chip key={g.id} active={filters.genre === g.slug} onClick={() => set("genre", g.slug)}>{g.name}</Chip>)}</FilterGroup>
      <FilterGroup title="Format">{FORMATS.map((f) => <Chip key={f} active={filters.format === f} onClick={() => set("format", f)}>{formatLabel(f)}</Chip>)}</FilterGroup>
      <FilterGroup title="Rating">{RATINGS.map((r) => <Chip key={r.v} active={params.get("rating") === r.v} onClick={() => set("rating", r.v)}>{r.l}</Chip>)}</FilterGroup>
      {activeCount > 0 && <Button variant="ghost" size="sm" onClick={() => router.replace(`/movies?status=${status}`)} icon={<X className="size-4" />}>Clear filters</Button>}
    </div>
  );

  return (
    <div className="container-page py-6 md:py-10">
      <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-3xl font-extrabold md:text-4xl">Movies {status === "NOW_SHOWING" && <span className="text-brand-gradient">in {city?.name ?? "your city"}</span>}</h1>
          <p className="mt-1 text-muted">{q.data?.pages[0]?.totalElements ?? "…"} titles · filter by language, genre and format</p>
        </div>
        <div className="flex items-center gap-2">
          <Select aria-label="Sort movies" value={filters.sort} onChange={(e) => set("sort", e.target.value)} className="w-44">
            {SORTS.map((s) => <option key={s.v} value={s.v}>{s.l}</option>)}
          </Select>
          <Button variant="outline" className="md:hidden" onClick={() => setSheet(true)} icon={<SlidersHorizontal className="size-4" />}>
            Filters{activeCount ? ` (${activeCount})` : ""}
          </Button>
        </div>
      </div>
      <Tabs id="movie-status" className="mt-6 w-fit" value={status} onChange={(v) => set("status", v === "NOW_SHOWING" ? undefined : v)}
        tabs={[{ value: "NOW_SHOWING", label: "Now Showing" }, { value: "COMING_SOON", label: "Coming Soon" }, { value: "CLASSIC", label: "Classics" }]} />

      <div className="mt-6 grid gap-8 md:grid-cols-[250px_1fr]">
        <aside className="hidden md:block" aria-label="Filters">
          <div className="sticky top-32 rounded-3xl border border-line p-5">{FilterPanel}</div>
        </aside>
        <section aria-live="polite">
          {q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} />
            : q.isLoading ? <div className="grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">{Array.from({ length: 10 }).map((_, i) => <MovieCardSkeleton key={i} />)}</div>
            : movies.length === 0 ? <EmptyState title="No movies found" message="Try removing a filter or switching city." />
            : (
              <>
                <div className="grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
                  {movies.map((m, i) => <MovieCard key={m.id} movie={m} onTrailer={setTrailer} className="w-auto" priority={i < 4} />)}
                </div>
                {q.hasNextPage && <div className="mt-10 text-center"><Button variant="secondary" loading={q.isFetchingNextPage} onClick={() => q.fetchNextPage()}>Load more</Button></div>}
              </>
            )}
        </section>
      </div>

      <div className={cn("fixed inset-0 z-[70] md:hidden", sheet ? "" : "pointer-events-none")} aria-hidden={!sheet}>
        <div className={cn("absolute inset-0 bg-ink/40 transition-opacity", sheet ? "opacity-100" : "opacity-0")} onClick={() => setSheet(false)} />
        <div className={cn("absolute inset-x-0 bottom-0 max-h-[80vh] overflow-y-auto rounded-t-3xl bg-white p-5 pb-8 transition-transform duration-300", sheet ? "translate-y-0" : "translate-y-full")}
          role="dialog" aria-label="Filters">
          <div className="mx-auto mb-4 h-1.5 w-12 rounded-full bg-line" />
          {FilterPanel}
          <Button className="mt-6 w-full" onClick={() => setSheet(false)}>Show results</Button>
        </div>
      </div>
      <TrailerModal movie={trailer} onClose={() => setTrailer(null)} />
    </div>
  );
}

function FilterGroup({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <fieldset>
      <legend className="mb-2.5 text-xs font-bold uppercase tracking-wider text-muted">{title}</legend>
      <div className="flex flex-wrap gap-2">{children}</div>
    </fieldset>
  );
}

export default function MoviesPage() {
  return <Suspense><MoviesInner /></Suspense>;
}
