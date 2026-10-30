"use client";

import { useQuery } from "@tanstack/react-query";
import { catalogApi } from "@/services";
import { useCity } from "@/features/location/CityProvider";
import { OfferCard } from "@/components/movies/OfferCard";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/States";

export default function OffersPage() {
  const { citySlug, city } = useCity();
  const q = useQuery({ queryKey: ["offers", citySlug], queryFn: () => catalogApi.offers(citySlug) });
  return (
    <div className="container-page py-8 pb-24 md:py-12">
      <h1 className="text-3xl font-extrabold">Offers <span className="text-brand-gradient">in {city?.name ?? "your city"}</span></h1>
      <p className="mt-1 text-muted">Copy a code and apply it at checkout. Eligibility is checked when you pay.</p>
      <div className="mt-8">
        {q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} /> : q.isLoading ? <div className="grid gap-4 md:grid-cols-3">{Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-48 rounded-3xl" />)}</div>
          : !q.data?.length ? <EmptyState title="No offers right now" /> : <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">{q.data.map((o) => <OfferCard key={o.id} offer={o} />)}</div>}
      </div>
    </div>
  );
}
