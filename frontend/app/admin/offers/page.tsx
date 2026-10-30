"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { adminApi, catalogApi } from "@/services";
import { PageHeader } from "@/components/admin/AdminShell";
import { DataTable } from "@/components/admin/DataTable";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { Field, Input, Select, Textarea } from "@/components/ui/Field";
import { Badge } from "@/components/ui/Badge";
import { ApiError } from "@/lib/api-client";
import { date } from "@/lib/format";
import type { AdminOffer } from "@/types/admin";

export default function AdminOffersPage() {
  const qc = useQueryClient();
  const [editing, setEditing] = useState<AdminOffer | "new" | null>(null);
  const list = useQuery({ queryKey: ["admin", "offers"], queryFn: adminApi.offers });
  const del = useMutation({ mutationFn: adminApi.deleteOffer, onSuccess: () => qc.invalidateQueries({ queryKey: ["admin", "offers"] }) });
  return (
    <div>
      <PageHeader title="Offers" subtitle="Promotional cards shown on Home, Offers and Checkout." actions={<Button icon={<Plus className="size-4" />} onClick={() => setEditing("new")}>New offer</Button>} />
      <DataTable caption="Offers" loading={list.isLoading} rows={list.data} rowKey={(o) => o.id} columns={[
        { key: "t", header: "Offer", cell: (o) => <div><p className="font-semibold">{o.title}</p><p className="max-w-80 truncate text-xs text-muted">{o.description}</p></div> },
        { key: "y", header: "Type", cell: (o) => <Badge tone="brand">{o.offerType.replace("_", " ").toLowerCase()}</Badge> },
        { key: "c", header: "Coupon", cell: (o) => <span className="font-mono text-xs">{o.couponCode ?? "—"}</span> },
        { key: "v", header: "Valid till", cell: (o) => date(o.validTo) },
        { key: "s", header: "Status", cell: (o) => (o.active ? <Badge tone="success">Live</Badge> : <Badge>Hidden</Badge>) },
        { key: "a", header: "", className: "text-right", cell: (o) => (
          <div className="flex justify-end gap-1"><Button size="sm" variant="ghost" onClick={() => setEditing(o)} aria-label="Edit"><Pencil className="size-4" /></Button>
            <Button size="sm" variant="ghost" onClick={() => confirm("Delete offer?") && del.mutate(o.id)} aria-label="Delete"><Trash2 className="size-4 text-rose-600" /></Button></div>) },
      ]} />
      {editing && <OfferForm offer={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
    </div>
  );
}

function OfferForm({ offer, onClose }: { offer: AdminOffer | null; onClose: () => void }) {
  const qc = useQueryClient();
  const cities = useQuery({ queryKey: ["cities"], queryFn: catalogApi.cities });
  const [f, setF] = useState(() => offer ?? { title: "", description: "", offerType: "WEEKEND", couponCode: "", cityId: undefined as number | undefined, movieId: undefined as number | undefined,
    imageUrl: "/media/offers/weekend.svg", terms: "", validFrom: new Date().toISOString(), validTo: new Date(Date.now() + 30 * 86400_000).toISOString(), active: true, sortOrder: 10 });
  const set = (p: Partial<typeof f>) => setF((x) => ({ ...x, ...p }));
  const save = useMutation({
    mutationFn: () => adminApi.saveOffer(offer?.id ?? null, f),
    onSuccess: () => { toast.success("Offer saved"); qc.invalidateQueries({ queryKey: ["admin", "offers"] }); onClose(); },
    onError: (e: ApiError) => toast.error(e.message),
  });
  return (
    <Modal open onClose={onClose} title={offer ? "Edit offer" : "New offer"} wide>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Title" htmlFor="ot" className="sm:col-span-2"><Input id="ot" value={f.title} onChange={(e) => set({ title: e.target.value })} /></Field>
        <Field label="Description" htmlFor="od" className="sm:col-span-2"><Textarea id="od" value={f.description} onChange={(e) => set({ description: e.target.value })} /></Field>
        <Field label="Type" htmlFor="oy"><Select id="oy" value={f.offerType} onChange={(e) => set({ offerType: e.target.value })}>{["WEEKEND", "STUDENT", "BANK", "UPI", "FIRST_BOOKING", "MOVIE"].map((t) => <option key={t}>{t}</option>)}</Select></Field>
        <Field label="Coupon code (optional)" htmlFor="oc"><Input id="oc" className="font-mono uppercase" value={f.couponCode ?? ""} onChange={(e) => set({ couponCode: e.target.value.toUpperCase() })} /></Field>
        <Field label="City" htmlFor="ocy"><Select id="ocy" value={f.cityId ?? ""} onChange={(e) => set({ cityId: e.target.value ? Number(e.target.value) : undefined })}><option value="">All cities</option>{cities.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</Select></Field>
        <Field label="Image URL" htmlFor="oi"><Input id="oi" value={f.imageUrl ?? ""} onChange={(e) => set({ imageUrl: e.target.value })} /></Field>
        <Field label="Valid from" htmlFor="of"><Input id="of" type="date" value={f.validFrom.slice(0, 10)} onChange={(e) => set({ validFrom: new Date(e.target.value + "T00:00:00+05:30").toISOString() })} /></Field>
        <Field label="Valid to" htmlFor="ov"><Input id="ov" type="date" value={f.validTo.slice(0, 10)} onChange={(e) => set({ validTo: new Date(e.target.value + "T23:59:59+05:30").toISOString() })} /></Field>
        <Field label="Terms" htmlFor="otm" className="sm:col-span-2"><Textarea id="otm" value={f.terms ?? ""} onChange={(e) => set({ terms: e.target.value })} /></Field>
        <Field label="Sort order" htmlFor="os"><Input id="os" type="number" value={f.sortOrder} onChange={(e) => set({ sortOrder: Number(e.target.value) })} /></Field>
        <label className="flex items-center gap-2 self-end text-sm"><input type="checkbox" className="accent-brand-600" checked={f.active} onChange={(e) => set({ active: e.target.checked })} />Live</label>
        <div className="flex justify-end gap-2 sm:col-span-2"><Button variant="ghost" onClick={onClose}>Cancel</Button><Button disabled={!f.title || !f.description} loading={save.isPending} onClick={() => save.mutate()}>Save offer</Button></div>
      </div>
    </Modal>
  );
}
