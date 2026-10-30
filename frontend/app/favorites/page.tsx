"use client";

import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Heart, Trash2 } from "lucide-react";
import { engagementApi } from "@/services";
import { useRequireAuth } from "@/features/auth/useRequireAuth";
import { Poster } from "@/components/ui/Poster";
import { Badge } from "@/components/ui/Badge";
import { ButtonLink } from "@/components/ui/Button";
import { RowSkeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/States";

export default function FavoritesPage() {
  const { ready } = useRequireAuth();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ["favorites"], queryFn: engagementApi.favorites, enabled: ready });
  const remove = useMutation({ mutationFn: engagementApi.removeFavorite, onSuccess: () => qc.invalidateQueries({ queryKey: ["favorites"] }) });
  return (
    <div className="container-page py-8 pb-24 md:py-12">
      <h1 className="text-3xl font-extrabold">Favorites</h1>
      <div className="mt-6">
        {!ready || q.isLoading ? <RowSkeleton /> : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} /> : !q.data?.length ? (
          <EmptyState icon={<Heart className="size-7" />} title="No favorites yet" message="Tap the heart on a movie, cinema or event to save it here." action={<ButtonLink href="/movies">Explore movies</ButtonLink>} />
        ) : (
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {q.data.map((f) => (
              <li key={f.id} className="flex items-center gap-4 rounded-3xl border border-line p-4">
                <Link href={f.link} className="relative grid aspect-[2/3] w-16 shrink-0 place-items-center overflow-hidden rounded-xl bg-brand-50 font-display font-bold text-brand-700">
                  {f.imageUrl ? <Poster src={f.imageUrl} alt="" sizes="64px" /> : f.title[0]}
                </Link>
                <div className="min-w-0 flex-1">
                  <Badge tone="brand">{f.targetType.toLowerCase()}</Badge>
                  <Link href={f.link} className="mt-1 block truncate font-semibold hover:text-brand-700">{f.title}</Link>
                  <p className="truncate text-xs text-muted">{f.subtitle}</p>
                </div>
                <button onClick={() => remove.mutate(f.id)} aria-label={`Remove ${f.title} from favorites`} className="grid size-9 place-items-center rounded-xl text-muted hover:bg-rose-50 hover:text-rose-600"><Trash2 className="size-4" /></button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
