"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { CalendarClock, Clapperboard, IndianRupee, MessageSquareQuote, RotateCcw, Sparkles, Ticket, Users } from "lucide-react";
import { adminApi } from "@/services";
import { useAuth } from "@/features/auth/AuthProvider";
import { PageHeader, Panel } from "@/components/admin/AdminShell";
import { StatTile, TrendArea, TrendBars } from "@/components/admin/Charts";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorState } from "@/components/ui/States";
import { ButtonLink } from "@/components/ui/Button";
import { inr } from "@/lib/format";

export default function AdminDashboard() {
  const { hasRole } = useAuth();
  const q = useQuery({ queryKey: ["admin", "dashboard"], queryFn: adminApi.dashboard, enabled: hasRole("ADMIN"), refetchInterval: 60_000 });
  if (!hasRole("ADMIN")) {
    return (
      <div><PageHeader title="Partner console" subtitle="Manage your cinemas, screens, seat layouts, shows and food menu." />
        <div className="flex flex-wrap gap-2"><ButtonLink href="/admin/shows">Schedule shows</ButtonLink><ButtonLink href="/admin/screens" variant="outline">Seat layouts</ButtonLink></div></div>
    );
  }
  if (q.isError) return <ErrorState error={q.error} onRetry={() => q.refetch()} />;
  const d = q.data;
  return (
    <div className="space-y-6">
      <PageHeader title="Dashboard" subtitle="Live numbers from PostgreSQL. Revenue counts confirmed bookings." />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {!d ? Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="h-32 rounded-3xl" />) : (
          <>
            <StatTile label="Today's revenue" value={inr(d.todaysRevenue)} icon={<IndianRupee className="size-5" />} />
            <StatTile label="Today's bookings" value={d.todaysBookings.toLocaleString("en-IN")} icon={<Ticket className="size-5" />} />
            <StatTile label="Total users" value={d.totalUsers.toLocaleString("en-IN")} icon={<Users className="size-5" />} />
            <StatTile label="Active movies" value={String(d.activeMovies)} icon={<Clapperboard className="size-5" />} />
            <StatTile label="Upcoming shows" value={d.activeShows.toLocaleString("en-IN")} icon={<CalendarClock className="size-5" />} />
            <StatTile label="Upcoming events" value={String(d.upcomingEvents)} icon={<Sparkles className="size-5" />} />
            <Link href="/admin/refunds"><StatTile label="Refunds in progress" value={String(d.pendingRefunds)} icon={<RotateCcw className="size-5" />} /></Link>
            <Link href="/admin/reviews"><StatTile label="Reviews to moderate" value={String(d.pendingReviews)} icon={<MessageSquareQuote className="size-5" />} /></Link>
          </>
        )}
      </div>
      {d && (
        <div className="grid gap-6 xl:grid-cols-2">
          <Panel title="Revenue · last 14 days"><TrendArea data={d.revenueLast14Days} money label="revenue" /></Panel>
          <Panel title="Bookings · last 14 days"><TrendBars data={d.bookingsLast14Days} label="Bookings" /></Panel>
          <Panel title="New users · last 14 days"><TrendBars data={d.signupsLast14Days} label="Signups" /></Panel>
          <Panel title="Occupancy % by show date"><TrendArea data={d.occupancyLast14Days} label="occupancy" suffix="%" /></Panel>
        </div>
      )}
    </div>
  );
}
