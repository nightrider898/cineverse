"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { ChevronRight, MapPin } from "lucide-react";
import { cinemaApi } from "@/services";
import { useCity } from "@/features/location/CityProvider";
import { RowSkeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/States";
import { Badge } from "@/components/ui/Badge";

export default function CinemasPage() {
  const { citySlug, city } = useCity();
  const q = useQuery({ queryKey: ["cinemas", citySlug], queryFn: () => cinemaApi.list(citySlug) });
  return (
    <div className="container-page py-8 pb-24 md:py-12">
      <h1 className="text-3xl font-extrabold">Cinemas in {city?.name ?? "your city"}</h1>
      <p className="mt-1 text-muted">Pick a cinema to see everything playing there today.</p>
      <div className="mt-6">
        {q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} /> : q.isLoading ? <RowSkeleton /> : !q.data?.length ? <EmptyState title="No cinemas here yet" /> : (
          <ul className="grid gap-4 md:grid-cols-2">
            {q.data.map((c, i) => (
              <motion.li key={c.id} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.04 }}>
                <Link href={`/cinemas/${c.id}`} className="flex items-center gap-4 rounded-3xl border border-line bg-white p-5 transition hover:-translate-y-0.5 hover:shadow-soft">
                  <div className="grid size-14 shrink-0 place-items-center rounded-2xl bg-brand-gradient font-display text-lg font-extrabold text-white">{c.name.split(" ").map((w) => w[0]).slice(0, 2).join("")}</div>
                  <div className="min-w-0 flex-1">
                    <h2 className="font-display font-bold">{c.name}</h2>
                    <p className="flex items-center gap-1 truncate text-sm text-muted"><MapPin className="size-3.5" />{c.address}</p>
                    <div className="mt-2 flex flex-wrap gap-1.5">{c.facilities.slice(0, 4).map((f) => <Badge key={f}>{f.replace("_", " ").toLowerCase()}</Badge>)}</div>
                  </div>
                  <ChevronRight className="size-5 text-muted" />
                </Link>
              </motion.li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
