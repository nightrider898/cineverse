"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Pencil, Plus } from "lucide-react";
import { adminApi } from "@/services";
import { PageHeader } from "@/components/admin/AdminShell";
import { DataTable } from "@/components/admin/DataTable";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { Field, Input, Select } from "@/components/ui/Field";
import { Badge } from "@/components/ui/Badge";
import { Poster } from "@/components/ui/Poster";
import { ApiError } from "@/lib/api-client";
import { inr } from "@/lib/format";
import type { AdminFood } from "@/types/admin";

export default function AdminFoodPage() {
  const qc = useQueryClient();
  const [editing, setEditing] = useState<AdminFood | "new" | null>(null);
  const list = useQuery({ queryKey: ["admin", "food"], queryFn: adminApi.food });
  const cinemas = useQuery({ queryKey: ["admin", "cinemas"], queryFn: adminApi.cinemas });
  const toggle = useMutation({ mutationFn: (f: AdminFood) => adminApi.saveFood(f.id, { ...f, available: !f.available }), onSuccess: () => qc.invalidateQueries({ queryKey: ["admin", "food"] }), onError: (e: ApiError) => toast.error(e.message) });
  return (
    <div>
      <PageHeader title="Food & beverages" subtitle="Menu items offered during booking. Prices are snapshotted into each order."
        actions={<Button icon={<Plus className="size-4" />} onClick={() => setEditing("new")}>New item</Button>} />
      <DataTable caption="Food items" loading={list.isLoading} rows={list.data} rowKey={(f) => f.id} columns={[
        { key: "n", header: "Item", cell: (f) => <div className="flex items-center gap-3"><div className="relative size-10 overflow-hidden rounded-lg"><Poster src={f.imageUrl} alt="" sizes="40px" /></div><div><p className="font-semibold">{f.name}</p><p className="text-xs text-muted">{f.description}</p></div></div> },
        { key: "c", header: "Category", cell: (f) => <Badge>{f.category.toLowerCase()}</Badge> },
        { key: "p", header: "Price", cell: (f) => inr(f.price) },
        { key: "w", header: "Where", cell: (f) => (f.cinemaId ? cinemas.data?.find((c) => c.id === f.cinemaId)?.name ?? `Cinema #${f.cinemaId}` : "All cinemas") },
        { key: "a", header: "Available", cell: (f) => <button onClick={() => toggle.mutate(f)} aria-pressed={f.available}>{f.available ? <Badge tone="success">On menu</Badge> : <Badge>Sold out</Badge>}</button> },
        { key: "e", header: "", className: "text-right", cell: (f) => <Button size="sm" variant="ghost" onClick={() => setEditing(f)} aria-label={`Edit ${f.name}`}><Pencil className="size-4" /></Button> },
      ]} />
      {editing && <FoodForm item={editing === "new" ? null : editing} cinemas={cinemas.data ?? []} onClose={() => setEditing(null)} />}
    </div>
  );
}

function FoodForm({ item, cinemas, onClose }: { item: AdminFood | null; cinemas: { id: number; name: string }[]; onClose: () => void }) {
  const qc = useQueryClient();
  const [f, setF] = useState(() => item ?? { cinemaId: undefined as number | undefined, name: "", description: "", category: "SNACK", price: 150, imageUrl: "/media/food/popcorn.svg", veg: true, available: true, sortOrder: 20 });
  const set = (p: Partial<typeof f>) => setF((x) => ({ ...x, ...p }));
  const save = useMutation({
    mutationFn: () => adminApi.saveFood(item?.id ?? null, f),
    onSuccess: () => { toast.success("Menu updated"); qc.invalidateQueries({ queryKey: ["admin", "food"] }); onClose(); },
    onError: (e: ApiError) => toast.error(e.message),
  });
  return (
    <Modal open onClose={onClose} title={item ? `Edit ${item.name}` : "New menu item"}>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Name" htmlFor="fn" className="sm:col-span-2"><Input id="fn" value={f.name} onChange={(e) => set({ name: e.target.value })} /></Field>
        <Field label="Description" htmlFor="fd" className="sm:col-span-2"><Input id="fd" value={f.description ?? ""} onChange={(e) => set({ description: e.target.value })} /></Field>
        <Field label="Category" htmlFor="fc"><Select id="fc" value={f.category} onChange={(e) => set({ category: e.target.value })}>{["POPCORN", "BEVERAGE", "SNACK", "COMBO"].map((c) => <option key={c}>{c}</option>)}</Select></Field>
        <Field label="Price ₹" htmlFor="fp"><Input id="fp" type="number" value={f.price} onChange={(e) => set({ price: Number(e.target.value) })} /></Field>
        <Field label="Cinema" htmlFor="fci" className="sm:col-span-2"><Select id="fci" value={f.cinemaId ?? ""} onChange={(e) => set({ cinemaId: e.target.value ? Number(e.target.value) : undefined })}><option value="">All cinemas</option>{cinemas.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</Select></Field>
        <Field label="Image URL" htmlFor="fi" className="sm:col-span-2"><Input id="fi" value={f.imageUrl ?? ""} onChange={(e) => set({ imageUrl: e.target.value })} /></Field>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-brand-600" checked={f.veg} onChange={(e) => set({ veg: e.target.checked })} />Vegetarian</label>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-brand-600" checked={f.available} onChange={(e) => set({ available: e.target.checked })} />Available</label>
        <div className="flex justify-end gap-2 sm:col-span-2"><Button variant="ghost" onClick={onClose}>Cancel</Button><Button disabled={!f.name} loading={save.isPending} onClick={() => save.mutate()}>Save</Button></div>
      </div>
    </Modal>
  );
}
