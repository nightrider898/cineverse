"use client";

import Link from "next/link";
import { use } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronLeft } from "lucide-react";
import { bookingApi } from "@/services";
import { useRequireAuth } from "@/features/auth/useRequireAuth";
import { TicketView } from "@/components/booking/TicketView";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorState } from "@/components/ui/States";
import { ButtonLink } from "@/components/ui/Button";

export default function BookingDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const id = Number(use(params).id);
  const { ready } = useRequireAuth();
  const q = useQuery({
    queryKey: ["booking", id], queryFn: () => bookingApi.get(id), enabled: ready,
    // Keep refund progress fresh while it is moving through the gateway.
    refetchInterval: (query) => (query.state.data?.refund && query.state.data.refund.status !== "COMPLETED" ? 15_000 : false),
  });
  return (
    <div className="container-page py-8 pb-24">
      <Link href="/bookings" className="no-print mb-6 inline-flex items-center gap-1 text-sm font-semibold text-muted hover:text-brand-700"><ChevronLeft className="size-4" />My bookings</Link>
      {!ready || q.isLoading ? <Skeleton className="mx-auto h-[520px] max-w-3xl rounded-[2rem]" />
        : q.isError || !q.data ? <ErrorState error={q.error} onRetry={() => q.refetch()} />
        : (
          <>
            {(q.data.status === "PENDING" || (q.data.status === "PAYMENT_PENDING" && !q.data.payAtVenue)) && (
              <div className="mx-auto mb-5 flex max-w-3xl items-center justify-between gap-3 rounded-2xl bg-amber-50 p-4 text-sm text-amber-800 ring-1 ring-amber-100">
                This booking isn't paid yet.
                <ButtonLink size="sm" href={`/checkout/${q.data.id}`}>Complete payment</ButtonLink>
              </div>
            )}
            <TicketView booking={q.data} />
          </>
        )}
    </div>
  );
}
