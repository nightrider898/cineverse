"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { adminApi } from "@/services";
import { PageHeader } from "@/components/admin/AdminShell";
import { DataTable } from "@/components/admin/DataTable";
import { Input, Select } from "@/components/ui/Field";
import { Badge } from "@/components/ui/Badge";
import { useDebounced } from "@/hooks/useDebounced";
import { ApiError } from "@/lib/api-client";
import { date, dateTime } from "@/lib/format";
import type { AdminUser } from "@/types/admin";
import { cn } from "@/lib/cn";

const ROLES = ["USER", "ADMIN", "PARTNER", "VENUE_STAFF"];

export default function AdminUsersPage() {
  const qc = useQueryClient();
  const [q, setQ] = useState("");
  const [role, setRole] = useState("");
  const dq = useDebounced(q, 300);
  const list = useQuery({ queryKey: ["admin", "users", dq, role], queryFn: () => adminApi.users(dq || undefined, role || undefined) });
  const update = useMutation({
    mutationFn: ({ u, roles, enabled }: { u: AdminUser; roles: string[]; enabled: boolean }) => adminApi.updateUser(u.id, roles, enabled),
    onSuccess: () => { toast.success("User updated"); qc.invalidateQueries({ queryKey: ["admin", "users"] }); },
    onError: (e: ApiError) => toast.error(e.message),
  });
  return (
    <div>
      <PageHeader title="Users" subtitle="Manage roles and access. Disabling a user revokes their sessions." />
      <div className="mb-4 flex flex-wrap gap-3">
        <Input aria-label="Search users" placeholder="Name or email" value={q} onChange={(e) => setQ(e.target.value)} className="max-w-xs" />
        <Select aria-label="Role" className="w-44" value={role} onChange={(e) => setRole(e.target.value)}>{["", ...ROLES].map((r) => <option key={r} value={r}>{r || "All roles"}</option>)}</Select>
      </div>
      <DataTable caption="Users" loading={list.isLoading} rows={list.data?.content} rowKey={(u) => u.id} columns={[
        { key: "n", header: "User", cell: (u) => <div><p className="font-semibold">{u.fullName}</p><p className="text-xs text-muted">{u.email}</p></div> },
        { key: "r", header: "Roles", cell: (u) => (
          <div className="flex flex-wrap gap-1">
            {ROLES.map((r) => {
              const on = u.roles.includes(r);
              return <button key={r} onClick={() => update.mutate({ u, roles: on ? u.roles.filter((x) => x !== r) : [...u.roles, r], enabled: u.enabled })} disabled={on && u.roles.length === 1}
                className={cn("rounded-full px-2 py-0.5 text-[11px] font-semibold ring-1", on ? "bg-brand-600 text-white ring-brand-600" : "text-muted ring-line hover:ring-brand-300")} aria-pressed={on}>{r.replace("_", " ")}</button>;
            })}
          </div>) },
        { key: "j", header: "Joined", cell: (u) => <span className="text-xs">{date(u.createdAt)}</span> },
        { key: "l", header: "Last login", cell: (u) => <span className="text-xs">{u.lastLoginAt ? dateTime(u.lastLoginAt) : "—"}</span> },
        { key: "e", header: "Access", cell: (u) => (
          <button onClick={() => update.mutate({ u, roles: u.roles, enabled: !u.enabled })}>{u.enabled ? <Badge tone="success">Active</Badge> : <Badge tone="danger">Disabled</Badge>}</button>) },
      ]} />
    </div>
  );
}
