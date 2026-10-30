"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Star, X } from "lucide-react";
import { adminApi } from "@/services";
import { PageHeader } from "@/components/admin/AdminShell";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/States";
import { RowSkeleton } from "@/components/ui/Skeleton";
import { date } from "@/lib/format";

export default function AdminReviewsPage() {
  const qc = useQueryClient();
  const list = useQuery({ queryKey: ["admin", "reviews"], queryFn: adminApi.reviews });
  const mod = useMutation({ mutationFn: ({ id, d }: { id: number; d: "approve" | "reject" }) => adminApi.moderate(id, d), onSuccess: () => qc.invalidateQueries({ queryKey: ["admin", "reviews"] }) });
  return (
    <div>
      <PageHeader title="Review moderation" subtitle="Only approved reviews count toward a movie's rating." />
      {list.isLoading ? <RowSkeleton /> : !list.data?.content.length ? <EmptyState title="All caught up" message="No reviews waiting for moderation." /> : (
        <ul className="grid gap-4 md:grid-cols-2">
          {list.data.content.map((r) => (
            <li key={r.id} className="rounded-3xl border border-line bg-white p-5">
              <div className="flex justify-between"><p className="font-semibold">{r.movieTitle}</p><span className="flex items-center gap-1 text-sm font-bold"><Star className="size-4 fill-amber-400 text-amber-400" />{r.rating}</span></div>
              <p className="text-xs text-muted">{r.userName} · {date(r.createdAt)}{r.verifiedBooking && " · verified booking"}</p>
              {r.title && <p className="mt-3 font-medium">{r.title}</p>}
              {r.body && <p className="mt-1 whitespace-pre-line text-sm text-ink-soft">{r.body}</p>}
              <div className="mt-4 flex gap-2">
                <Button size="sm" icon={<Check className="size-4" />} onClick={() => mod.mutate({ id: r.id, d: "approve" })}>Approve</Button>
                <Button size="sm" variant="outline" icon={<X className="size-4" />} onClick={() => mod.mutate({ id: r.id, d: "reject" })}>Reject</Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
