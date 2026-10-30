"use client";

import Link from "next/link";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { adminApi } from "@/services";
import { PageHeader } from "@/components/admin/AdminShell";
import { DataTable } from "@/components/admin/DataTable";
import { Button } from "@/components/ui/Button";
import { Input, Select } from "@/components/ui/Field";
import { StatusBadge } from "@/components/ui/Badge";
import { useDebounced } from "@/hooks/useDebounced";
import { ApiError } from "@/lib/api-client";
import { dateTime, inr } from "@/lib/format";

const STATUSES = ["", "PENDING", "PAYMENT_PENDING", "CONFIRMED", "CANCELLED", "EXPIRED", "REFUND_PENDING", "REFUNDED"];

export default function AdminBookingsPage() {
  const qc = useQueryClient();
  const [status, setStatus] = useState("");
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const dq = useDebounced(q, 300);
  const list = useQuery({ queryKey: ["admin", "bookings", status, dq, page], queryFn: () => adminApi.bookings({ status: status || undefined, q: dq || undefined, page }) });
  const cancel = useMutation({
    mutationFn: (id: number) => adminApi.cancelBooking(id, "Cancelled by BookMyEvent support"),
    onSuccess: () => { toast.success("Booking cancelled; refund queued if paid"); qc.invalidateQueries({ queryKey: ["admin", "bookings"] }); },
    onError: (e: ApiError) => toast.error(e.message),
  });
  const data = list.data;
  return (
    <div>
      <PageHeader title="Bookings" subtitle={data ? `${data.totalElements.toLocaleString("en-IN")} bookings` : undefined} />
      <div className="mb-4 flex flex-wrap gap-3">
        <Input aria-label="Search" placeholder="Reference or customer email" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} className="max-w-xs" />
        <Select aria-label="Status" className="w-52" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>{STATUSES.map((s) => <option key={s} value={s}>{s ? s.replace("_", " ") : "All statuses"}</option>)}</Select>
      </div>
      <DataTable caption="Bookings" loading={list.isLoading} rows={data?.content} rowKey={(r) => r.booking.id} columns={[
        { key: "r", header: "Reference", cell: (r) => <Link href={`/bookings/${r.booking.id}`} className="font-mono text-xs font-semibold text-brand-700 hover:underline">{r.booking.reference}</Link> },
        { key: "c", header: "Customer", cell: (r) => <div><p className="font-medium">{r.userName}</p><p className="text-xs text-muted">{r.userEmail}</p></div> },
        { key: "s", header: "Show", cell: (r) => <div><p className="font-medium">{r.booking.title}</p><p className="text-xs text-muted">{r.booking.venueName} · {dateTime(r.booking.startTime)}</p></div> },
        { key: "t", header: "Tickets", cell: (r) => <span className="text-xs">{r.booking.seats.map((s) => s.label).join(", ") || r.booking.tickets.map((t) => `${t.quantity}×${t.name}`).join(", ")}</span> },
        { key: "a", header: "Amount", cell: (r) => inr(r.booking.price.grandTotal, { decimals: true }) },
        { key: "st", header: "Status", cell: (r) => <StatusBadge status={r.booking.status} /> },
        { key: "x", header: "", className: "text-right", cell: (r) => ["CONFIRMED", "PENDING", "PAYMENT_PENDING"].includes(r.booking.status) && (
          <Button size="sm" variant="ghost" className="text-rose-600" onClick={() => confirm(`Cancel ${r.booking.reference}? Paid bookings are refunded in full minus fees.`) && cancel.mutate(r.booking.id)}>Cancel</Button>) },
      ]} />
      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-end gap-2 text-sm">
          <Button size="sm" variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
          <span>Page {page + 1} of {data.totalPages}</span>
          <Button size="sm" variant="outline" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</Button>
        </div>
      )}
    </div>
  );
}
