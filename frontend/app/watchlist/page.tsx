"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, BellOff, Bookmark, Trash2 } from "lucide-react";
import { engagementApi } from "@/services";
import { useRequireAuth } from "@/features/auth/useRequireAuth";
import { MovieCard } from "@/components/movies/MovieCard";
import { ButtonLink } from "@/components/ui/Button";
import { MovieCardSkeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/States";

export default function WatchlistPage() {
  const { ready } = useRequireAuth();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ["watchlist"], queryFn: engagementApi.watchlist, enabled: ready });
  const refresh = () => qc.invalidateQueries({ queryKey: ["watchlist"] });
  const remind = useMutation({ mutationFn: ({ id, on }: { id: number; on: boolean }) => engagementApi.addWatchlist(id, on), onSuccess: refresh });
  const remove = useMutation({ mutationFn: engagementApi.removeWatchlist, onSuccess: refresh });
  return (
    <div className="container-page py-8 pb-24 md:py-12">
      <h1 className="text-3xl font-extrabold">Watchlist</h1>
      <p className="mt-1 text-muted">Upcoming releases you're waiting for. We'll notify you on release day.</p>
      <div className="mt-8">
        {!ready || q.isLoading ? <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">{Array.from({ length: 4 }).map((_, i) => <MovieCardSkeleton key={i} />)}</div>
          : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} />
          : !q.data?.length ? <EmptyState icon={<Bookmark className="size-7" />} title="Your watchlist is empty" message="Add coming-soon movies to get a reminder when bookings open." action={<ButtonLink href="/movies?status=COMING_SOON">See coming soon</ButtonLink>} />
          : (
            <div className="grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-5">
              {q.data.map((w) => (
                <div key={w.id}>
                  <MovieCard movie={w.movie} className="w-auto" />
                  <div className="mt-2 flex gap-1">
                    <button onClick={() => remind.mutate({ id: w.movie.id, on: !w.remindMe })} className="flex flex-1 items-center justify-center gap-1 rounded-lg bg-mist py-1.5 text-xs font-semibold hover:bg-brand-50" aria-pressed={w.remindMe}>
                      {w.remindMe ? <><Bell className="size-3.5 text-brand-600" />Reminder on</> : <><BellOff className="size-3.5" />Remind me</>}
                    </button>
                    <button onClick={() => remove.mutate(w.movie.id)} className="grid size-8 place-items-center rounded-lg bg-mist text-muted hover:bg-rose-50 hover:text-rose-600" aria-label={`Remove ${w.movie.title}`}><Trash2 className="size-3.5" /></button>
                  </div>
                </div>
              ))}
            </div>
          )}
      </div>
    </div>
  );
}
