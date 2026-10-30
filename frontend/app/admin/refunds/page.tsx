"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { adminApi } from "@/services";
import { PageHeader } from "@/components/admin/AdminShell";
import { DataTable } from "@/components/admin/DataTable";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Field";
import { StatusBadge } from "@/components/ui/Badge";
import { ApiError } from "@/lib/api-client";
import { dateTime, inr } from "@/lib/format";

export default function AdminRefundsPage() {
  const qc = useQueryClient();
  const [status, setStatus] = useState("");
  const list = useQuery({ queryKey: ["admin", "refunds", status], queryFn: () => adminApi.refunds(status || undefined), refetchInterval: 20_000 });
  const done = { onSuccess: () => qc.invalidateQueries({ queryKey: ["admin", "refunds"] }), onError: (e: ApiError) => toast.error(e.message) };
  const retry = useMutation({ mutationFn: adminApi.retryRefund, ...done });
  const complete = useMutation({ mutationFn: adminApi.completeRefund, ...done });
  return (
    <div>
      <PageHeader title="Refunds" subtitle="Refunds are only marked completed when the gateway confirms them (or staff settle a counter refund)." />
      <Select aria-label="Status" className="mb-4 w-48" value={status} onChange={(e) => setStatus(e.target.value)}>
        {["", "REQUESTED", "PROCESSING", "COMPLETED", "FAILED"].map((s) => <option key={s} value={s}>{s || "All statuses"}</option>)}
      </Select>
      <DataTable caption="Refunds" loading={list.isLoading} rows={list.data?.content} rowKey={(r) => r.id} columns={[
        { key: "b", header: "Booking", cell: (r) => <span className="font-mono text-xs font-semibold">{r.bookingReference}</span> },
        { key: "a", header: "Amount", cell: (r) => inr(r.amount, { decimals: true }) },
        { key: "re", header: "Reason", cell: (r) => <span className="text-xs">{r.reason}</span> },
        { key: "s", header: "Status", cell: (r) => <StatusBadge status={r.status} /> },
        { key: "p", header: "Gateway ref", cell: (r) => <span className="font-mono text-[11px] text-muted">{r.providerRefundId ?? "—"}</span> },
        { key: "t", header: "Requested", cell: (r) => <span className="text-xs">{dateTime(r.requestedAt)}{r.processedAt && <><br />done {dateTime(r.processedAt)}</>}</span> },
        { key: "x", header: "", className: "text-right", cell: (r) => r.status !== "COMPLETED" && (
          <div className="flex justify-end gap-1">
            {r.status === "FAILED" && <Button size="sm" variant="outline" loading={retry.isPending && retry.variables === r.id} onClick={() => retry.mutate(r.id)}>Retry</Button>}
            <Button size="sm" variant="ghost" onClick={() => confirm("Mark as refunded? Use only after money was returned outside the gateway (e.g. at the counter).") && complete.mutate(r.id)}>Mark completed</Button>
          </div>) },
      ]} />
    </div>
  );
}
