"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { CalendarDays, MapPin, Ticket } from "lucide-react";
import { bookingApi } from "@/services";
import { useRequireAuth } from "@/features/auth/useRequireAuth";
import { Tabs } from "@/components/ui/Tabs";
import { Poster } from "@/components/ui/Poster";
import { StatusBadge } from "@/components/ui/Badge";
import { ButtonLink } from "@/components/ui/Button";
import { RowSkeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/States";
import { dateTime, inr } from "@/lib/format";

type Tab = "upcoming" | "completed" | "cancelled";

export default function MyBookingsPage() {
  const { ready } = useRequireAuth();
  const [tab, setTab] = useState<Tab>("upcoming");
  const q = useQuery({ queryKey: ["bookings", tab], queryFn: () => bookingApi.list(tab), enabled: ready });

  return (
    <div className="container-page py-8 pb-24 md:py-12">
      <h1 className="text-3xl font-extrabold">My bookings</h1>
      <Tabs id="bookings" className="mt-6 w-fit" value={tab} onChange={setTab}
        tabs={[{ value: "upcoming", label: "Upcoming" }, { value: "completed", label: "Completed" }, { value: "cancelled", label: "Cancelled" }]} />
      <div className="mt-6">
        {!ready || q.isLoading ? <RowSkeleton />
          : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} />
          : !q.data?.content.length ? (
            <EmptyState icon={<Ticket className="size-7" />} title={tab === "upcoming" ? "No upcoming bookings" : tab === "completed" ? "Nothing watched yet" : "No cancelled bookings"}
              message="When you book a movie or event it will show up here with your QR ticket." action={<ButtonLink href="/movies">Browse movies</ButtonLink>} />
          ) : (
            <ul className="grid gap-4 md:grid-cols-2">
              {q.data.content.map((b, i) => (
                <motion.li key={b.id} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.04 }}>
                  <Link href={`/bookings/${b.id}`} className="flex gap-4 rounded-3xl border border-line bg-white p-4 transition hover:-translate-y-0.5 hover:shadow-soft">
                    <div className="relative aspect-[2/3] w-20 shrink-0 overflow-hidden rounded-2xl"><Poster src={b.posterUrl} alt="" sizes="80px" /></div>
                    <div className="min-w-0 flex-1 space-y-1 text-sm">
                      <div className="flex items-start justify-between gap-2">
                        <h2 className="truncate font-display text-base font-bold">{b.title}</h2>
                        <StatusBadge status={b.payAtVenue && b.status === "PAYMENT_PENDING" ? "PAYMENT_PENDING" : b.status} />
                      </div>
                      <p className="flex items-center gap-1.5 text-muted"><MapPin className="size-3.5" />{b.venueName}</p>
                      <p className="flex items-center gap-1.5 text-muted"><CalendarDays className="size-3.5" />{dateTime(b.startTime)}</p>
                      <p className="truncate">{b.seats.length ? `Seats ${b.seats.map((s) => s.label).join(", ")}` : b.tickets.map((t) => `${t.quantity} × ${t.name}`).join(", ")}</p>
                      <p className="flex items-center justify-between pt-1 text-xs"><span className="font-mono text-muted">{b.reference}</span><b>{inr(b.price.grandTotal, { decimals: true })}</b></p>
                    </div>
                  </Link>
                </motion.li>
              ))}
            </ul>
          )}
      </div>
    </div>
  );
}
