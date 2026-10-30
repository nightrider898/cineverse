"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Pencil, Plus } from "lucide-react";
import { adminApi, catalogApi } from "@/services";
import { PageHeader } from "@/components/admin/AdminShell";
import { DataTable } from "@/components/admin/DataTable";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { Field, Input, Select } from "@/components/ui/Field";
import { Badge } from "@/components/ui/Badge";
import { ApiError } from "@/lib/api-client";
import { date, inr } from "@/lib/format";
import type { AdminCoupon } from "@/types/admin";

type CouponDraft = Omit<AdminCoupon, "id" | "timesUsed" | "paymentMethod"> & { paymentMethod: string };

const toLocal = (iso: string) => new Date(new Date(iso).getTime() + 330 * 60_000).toISOString().slice(0, 16);
const fromLocal = (v: string) => new Date(v + ":00+05:30").toISOString();

export default function AdminCouponsPage() {
  const qc = useQueryClient();
  const [editing, setEditing] = useState<AdminCoupon | "new" | null>(null);
  const list = useQuery({ queryKey: ["admin", "coupons"], queryFn: adminApi.coupons });
  const toggle = useMutation({
    mutationFn: (c: AdminCoupon) => adminApi.saveCoupon(c.id, { ...c, active: !c.active, paymentMethod: c.paymentMethod ?? "" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin", "coupons"] }),
    onError: (e: ApiError) => toast.error(e.message),
  });
  return (
    <div>
      <PageHeader title="Coupons" subtitle="Every rule is enforced on the server at apply time and again at payment."
        actions={<Button icon={<Plus className="size-4" />} onClick={() => setEditing("new")}>New coupon</Button>} />
      <DataTable caption="Coupons" loading={list.isLoading} rows={list.data} rowKey={(c) => c.id} columns={[
        { key: "c", header: "Code", cell: (c) => <div><p className="font-mono font-bold">{c.code}</p><p className="max-w-64 truncate text-xs text-muted">{c.description}</p></div> },
        { key: "d", header: "Discount", cell: (c) => (c.discountType === "PERCENT" ? `${c.discountValue}%${c.maxDiscount ? ` up to ${inr(c.maxDiscount)}` : ""}` : inr(c.discountValue)) },
        { key: "r", header: "Rules", cell: (c) => (
          <div className="flex max-w-72 flex-wrap gap-1">
            {c.minOrderAmount > 0 && <Badge>min {inr(c.minOrderAmount)}</Badge>}
            {c.firstBookingOnly && <Badge tone="brand">first booking</Badge>}
            {c.paymentMethod && <Badge tone="brand">{c.paymentMethod}</Badge>}
            {c.movieId && <Badge>movie #{c.movieId}</Badge>}{c.cityId && <Badge>city #{c.cityId}</Badge>}{c.cinemaId && <Badge>cinema #{c.cinemaId}</Badge>}
            <Badge>{c.perUserLimit}/user</Badge>
          </div>) },
        { key: "u", header: "Used", cell: (c) => `${c.timesUsed}${c.usageLimit ? ` / ${c.usageLimit}` : ""}` },
        { key: "v", header: "Valid", cell: (c) => <span className="text-xs">{date(c.validFrom)} – {date(c.validTo)}</span> },
        { key: "s", header: "Active", cell: (c) => <button onClick={() => toggle.mutate(c)} aria-pressed={c.active}>{c.active ? <Badge tone="success">Active</Badge> : <Badge>Inactive</Badge>}</button> },
        { key: "a", header: "", className: "text-right", cell: (c) => <Button size="sm" variant="ghost" onClick={() => setEditing(c)} aria-label={`Edit ${c.code}`}><Pencil className="size-4" /></Button> },
      ]} />
      {editing && <CouponForm coupon={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
    </div>
  );
}

function CouponForm({ coupon, onClose }: { coupon: AdminCoupon | null; onClose: () => void }) {
  const qc = useQueryClient();
  const cities = useQuery({ queryKey: ["cities"], queryFn: catalogApi.cities });
  const movies = useQuery({ queryKey: ["admin", "movies", ""], queryFn: () => adminApi.movies() });
  const cinemas = useQuery({ queryKey: ["admin", "cinemas"], queryFn: adminApi.cinemas });
  const now = new Date();
  const [f, setF] = useState<CouponDraft>(() => coupon ? { ...coupon, paymentMethod: coupon.paymentMethod ?? "" } : {
    code: "", description: "", discountType: "PERCENT", discountValue: 10, maxDiscount: 100 as number | undefined, minOrderAmount: 0,
    validFrom: now.toISOString(), validTo: new Date(now.getTime() + 30 * 86400_000).toISOString(), movieId: undefined as number | undefined,
    cityId: undefined as number | undefined, cinemaId: undefined as number | undefined, paymentMethod: "", firstBookingOnly: false,
    usageLimit: undefined as number | undefined, perUserLimit: 1, active: true,
  });
  const set = (patch: Partial<typeof f>) => setF((p) => ({ ...p, ...patch }));
  const num = (v: string) => (v === "" ? undefined : Number(v));
  const save = useMutation({
    mutationFn: () => adminApi.saveCoupon(coupon?.id ?? null, f),
    onSuccess: () => { toast.success("Coupon saved"); qc.invalidateQueries({ queryKey: ["admin", "coupons"] }); onClose(); },
    onError: (e: ApiError) => toast.error(e.fields ? Object.entries(e.fields).map(([k, v]) => `${k}: ${v}`).join(" · ") : e.message),
  });
  return (
    <Modal open onClose={onClose} title={coupon ? `Edit ${coupon.code}` : "New coupon"} wide>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Code" htmlFor="cc"><Input id="cc" className="font-mono uppercase" value={f.code} onChange={(e) => set({ code: e.target.value.toUpperCase() })} /></Field>
        <Field label="Description" htmlFor="cd"><Input id="cd" value={f.description} onChange={(e) => set({ description: e.target.value })} /></Field>
        <Field label="Type" htmlFor="ct"><Select id="ct" value={f.discountType} onChange={(e) => set({ discountType: e.target.value as "PERCENT" })}><option value="PERCENT">Percent</option><option value="FLAT">Flat ₹</option></Select></Field>
        <Field label="Value" htmlFor="cv"><Input id="cv" type="number" value={f.discountValue} onChange={(e) => set({ discountValue: Number(e.target.value) })} /></Field>
        <Field label="Max discount ₹ (percent only)" htmlFor="cm"><Input id="cm" type="number" value={f.maxDiscount ?? ""} onChange={(e) => set({ maxDiscount: num(e.target.value) })} /></Field>
        <Field label="Minimum order ₹" htmlFor="co"><Input id="co" type="number" value={f.minOrderAmount} onChange={(e) => set({ minOrderAmount: Number(e.target.value) })} /></Field>
        <Field label="Valid from" htmlFor="vf"><Input id="vf" type="datetime-local" value={toLocal(f.validFrom)} onChange={(e) => set({ validFrom: fromLocal(e.target.value) })} /></Field>
        <Field label="Valid to" htmlFor="vt"><Input id="vt" type="datetime-local" value={toLocal(f.validTo)} onChange={(e) => set({ validTo: fromLocal(e.target.value) })} /></Field>
        <Field label="Only for movie" htmlFor="cmv"><Select id="cmv" value={f.movieId ?? ""} onChange={(e) => set({ movieId: num(e.target.value) })}><option value="">Any movie</option>{movies.data?.content.map((m) => <option key={m.id} value={m.id}>{m.title}</option>)}</Select></Field>
        <Field label="Only in city" htmlFor="ccy"><Select id="ccy" value={f.cityId ?? ""} onChange={(e) => set({ cityId: num(e.target.value) })}><option value="">Any city</option>{cities.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</Select></Field>
        <Field label="Only at cinema" htmlFor="ccn"><Select id="ccn" value={f.cinemaId ?? ""} onChange={(e) => set({ cinemaId: num(e.target.value) })}><option value="">Any cinema</option>{cinemas.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</Select></Field>
        <Field label="Payment method" htmlFor="cpm"><Select id="cpm" value={f.paymentMethod} onChange={(e) => set({ paymentMethod: e.target.value })}><option value="">Any method</option>{["UPI", "CARD", "NETBANKING", "WALLET", "PHONEPE", "GPAY"].map((m) => <option key={m}>{m}</option>)}</Select></Field>
        <Field label="Total usage limit" htmlFor="cul" hint="Blank = unlimited"><Input id="cul" type="number" value={f.usageLimit ?? ""} onChange={(e) => set({ usageLimit: num(e.target.value) })} /></Field>
        <Field label="Uses per user" htmlFor="cpu"><Input id="cpu" type="number" min={1} value={f.perUserLimit} onChange={(e) => set({ perUserLimit: Number(e.target.value) })} /></Field>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-brand-600" checked={f.firstBookingOnly} onChange={(e) => set({ firstBookingOnly: e.target.checked })} />First booking only</label>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-brand-600" checked={f.active} onChange={(e) => set({ active: e.target.checked })} />Active</label>
        <div className="flex justify-end gap-2 sm:col-span-2"><Button variant="ghost" onClick={onClose}>Cancel</Button><Button disabled={!f.code || !f.description} loading={save.isPending} onClick={() => save.mutate()}>Save coupon</Button></div>
      </div>
    </Modal>
  );
}
