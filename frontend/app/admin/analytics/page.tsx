"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { adminApi } from "@/services";
import { PageHeader, Panel } from "@/components/admin/AdminShell";
import { RankBars, StatTile, TrendArea, TrendBars } from "@/components/admin/Charts";
import { Input } from "@/components/ui/Field";
import { Chip } from "@/components/ui/Chip";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorState } from "@/components/ui/States";
import { inr, todayIST } from "@/lib/format";

const shift = (ymd: string, days: number) => { const [y, m, d] = ymd.split("-").map(Number); return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10); };

export default function AnalyticsPage() {
  const today = todayIST();
  const [from, setFrom] = useState(shift(today, -29));
  const [to, setTo] = useState(today);
  const q = useQuery({ queryKey: ["admin", "analytics", from, to], queryFn: () => adminApi.analytics(from, to) });
  const a = q.data;
  const preset = (days: number) => { setFrom(shift(today, -(days - 1))); setTo(today); };
  return (
    <div className="space-y-6">
      <PageHeader title="Analytics" subtitle="Sales, occupancy and demand for the selected range." />
      <div className="flex flex-wrap items-center gap-2 rounded-3xl border border-line bg-white p-3">
        {[7, 30, 90].map((d) => <Chip key={d} active={from === shift(today, -(d - 1)) && to === today} onClick={() => preset(d)}>Last {d} days</Chip>)}
        <label className="ml-auto flex items-center gap-2 text-sm">From <Input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} className="h-9 w-40" /></label>
        <label className="flex items-center gap-2 text-sm">To <Input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} className="h-9 w-40" /></label>
      </div>
      {q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} /> : !a ? <Skeleton className="h-96 rounded-3xl" /> : (
        <>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <StatTile label="Gross sales" value={inr(a.grossSales)} hint={`${inr(a.discounts)} in discounts`} />
            <StatTile label="Net sales" value={inr(a.netSales)} hint={`${inr(a.refunds)} refunded`} />
            <StatTile label="Bookings" value={a.bookings.toLocaleString("en-IN")} hint={`${a.ticketsSold.toLocaleString("en-IN")} tickets`} />
            <StatTile label="Avg. ticket value" value={inr(a.averageTicketValue)} hint={`Occupancy ${a.occupancyPercent}%`} />
          </div>
          <div className="grid gap-6 xl:grid-cols-2">
            <Panel title="Revenue by day"><TrendArea data={a.revenueByDay} money label="rev-range" /></Panel>
            <Panel title="Bookings by day"><TrendBars data={a.bookingsByDay} label="Bookings" /></Panel>
            <Panel title="Popular movies (tickets)"><RankBars data={a.popularMovies} /></Panel>
            <Panel title="Popular cinemas (revenue)"><RankBars data={a.popularCinemas} money /></Panel>
            <Panel title="Popular showtimes (bookings)"><RankBars data={a.popularShowtimes} /></Panel>
            <Panel title="Payment methods (amount)"><RankBars data={a.paymentMethods} money /></Panel>
            <Panel title="Coupon usage (redemptions)"><RankBars data={a.couponUsage} /></Panel>
            <Panel title="Booking status mix"><RankBars data={a.bookingStatus} /></Panel>
          </div>
        </>
      )}
    </div>
  );
}
