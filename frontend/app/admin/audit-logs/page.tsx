"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { adminApi } from "@/services";
import { PageHeader } from "@/components/admin/AdminShell";
import { DataTable } from "@/components/admin/DataTable";
import { Input } from "@/components/ui/Field";
import { Badge } from "@/components/ui/Badge";
import { useDebounced } from "@/hooks/useDebounced";
import { dateTime } from "@/lib/format";

export default function AuditLogPage() {
  const [action, setAction] = useState("");
  const [resource, setResource] = useState("");
  const a = useDebounced(action.trim().toUpperCase(), 300), r = useDebounced(resource.trim().toUpperCase(), 300);
  const list = useQuery({ queryKey: ["admin", "audit", a, r], queryFn: () => adminApi.audit(a || undefined, r || undefined) });
  return (
    <div>
      <PageHeader title="Audit log" subtitle="Who did what, when, from where. Secrets and card data are never recorded." />
      <div className="mb-4 flex flex-wrap gap-3">
        <Input aria-label="Action" placeholder="Action, e.g. ADMIN_CREATED_SHOW" value={action} onChange={(e) => setAction(e.target.value)} className="max-w-xs font-mono" />
        <Input aria-label="Resource" placeholder="Resource, e.g. BOOKING" value={resource} onChange={(e) => setResource(e.target.value)} className="max-w-xs font-mono" />
      </div>
      <DataTable caption="Audit log" loading={list.isLoading} rows={list.data?.content} rowKey={(l) => l.id} columns={[
        { key: "t", header: "Time", cell: (l) => <span className="whitespace-nowrap text-xs">{dateTime(l.createdAt)}</span> },
        { key: "a", header: "Action", cell: (l) => <Badge tone={l.action.startsWith("ADMIN") ? "brand" : l.action.includes("FAIL") || l.action.includes("REUSE") ? "danger" : "neutral"}>{l.action}</Badge> },
        { key: "r", header: "Resource", cell: (l) => <span className="font-mono text-xs">{l.resource}#{l.resourceId}</span> },
        { key: "u", header: "User", cell: (l) => l.userId ?? "system" },
        { key: "i", header: "IP / request", cell: (l) => <span className="font-mono text-[11px] text-muted">{l.ipAddress}<br />{l.requestId?.slice(0, 8)}</span> },
        { key: "m", header: "Details", cell: (l) => <code className="block max-w-72 truncate text-[11px] text-muted" title={JSON.stringify(l.metadata)}>{l.metadata ? JSON.stringify(l.metadata) : ""}</code> },
      ]} />
    </div>
  );
}
