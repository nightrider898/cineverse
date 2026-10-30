"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { adminApi } from "@/services";
import { PageHeader } from "@/components/admin/AdminShell";
import { DataTable } from "@/components/admin/DataTable";
import { Select } from "@/components/ui/Field";
import { Badge, StatusBadge } from "@/components/ui/Badge";
import { dateTime, inr } from "@/lib/format";

export default function AdminPaymentsPage() {
  const [status, setStatus] = useState("");
  const [provider, setProvider] = useState("");
  const list = useQuery({ queryKey: ["admin", "payments", status, provider], queryFn: () => adminApi.payments({ status: status || undefined, provider: provider || undefined }) });
  return (
    <div>
      <PageHeader title="Payments" subtitle="Every gateway interaction is recorded; only safe gateway references are stored — never card data." />
      <div className="mb-4 flex flex-wrap gap-3">
        <Select aria-label="Status" className="w-48" value={status} onChange={(e) => setStatus(e.target.value)}>
          {["", "INITIATED", "PENDING", "SUCCESS", "FAILED", "CANCELLED", "REFUNDED", "PARTIALLY_REFUNDED"].map((s) => <option key={s} value={s}>{s || "All statuses"}</option>)}
        </Select>
        <Select aria-label="Provider" className="w-44" value={provider} onChange={(e) => setProvider(e.target.value)}>
          {["", "MOCK", "STRIPE", "RAZORPAY", "PHONEPE", "VENUE"].map((s) => <option key={s} value={s}>{s || "All providers"}</option>)}
        </Select>
      </div>
      <DataTable caption="Payments" loading={list.isLoading} rows={list.data?.content} rowKey={(p) => p.paymentId} columns={[
        { key: "b", header: "Booking", cell: (p) => <span className="font-mono text-xs font-semibold">{p.bookingReference}</span> },
        { key: "p", header: "Provider", cell: (p) => <div className="flex gap-1"><Badge tone="brand">{p.provider}</Badge><Badge>{p.method.replace("_", " ")}</Badge></div> },
        { key: "a", header: "Amount", cell: (p) => <div>{inr(p.amount, { decimals: true })}{p.refundedAmount > 0 && <p className="text-xs text-muted">−{inr(p.refundedAmount)} refunded</p>}</div> },
        { key: "s", header: "Status", cell: (p) => <div><StatusBadge status={p.status} />{p.failureReason && <p className="mt-1 max-w-48 truncate text-xs text-muted" title={p.failureReason}>{p.failureReason}</p>}</div> },
        { key: "r", header: "Gateway refs", cell: (p) => <div className="max-w-56 truncate font-mono text-[11px] text-muted" title={`${p.providerOrderId ?? ""} ${p.providerPaymentId ?? ""}`}>{p.providerOrderId}<br />{p.providerPaymentId}</div> },
        { key: "t", header: "Created", cell: (p) => <span className="text-xs">{dateTime(p.createdAt)}</span> },
      ]} />
    </div>
  );
}
