"use client";

import Link from "next/link";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { LayoutGrid, Pencil, Plus } from "lucide-react";
import { adminApi, catalogApi } from "@/services";
import { useAuth } from "@/features/auth/AuthProvider";
import { PageHeader } from "@/components/admin/AdminShell";
import { DataTable } from "@/components/admin/DataTable";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { Field, Input, Select } from "@/components/ui/Field";
import { Chip } from "@/components/ui/Chip";
import { Badge } from "@/components/ui/Badge";
import { ApiError } from "@/lib/api-client";
import type { AdminCinema } from "@/types/admin";

const FACILITIES = ["PARKING", "FOOD_COURT", "WHEELCHAIR", "M_TICKET", "RECLINER"];

type FormValues = { name: string; cityId: number; locality: string; address: string; latitude?: number; longitude?: number; facilities: string[];
  allowPayAtVenue: boolean; cancellationAllowed: boolean; active: boolean };

export default function AdminCinemasPage() {
  const { hasRole } = useAuth();
  const [editing, setEditing] = useState<AdminCinema | "new" | null>(null);
  const list = useQuery({ queryKey: ["admin", "cinemas"], queryFn: adminApi.cinemas });
  return (
    <div>
      <PageHeader title="Cinemas" subtitle={hasRole("ADMIN") ? "All cinemas across cities." : "Cinemas linked to your partner account."}
        actions={hasRole("ADMIN") && <Button icon={<Plus className="size-4" />} onClick={() => setEditing("new")}>New cinema</Button>} />
      <DataTable caption="Cinemas" loading={list.isLoading} rows={list.data} rowKey={(c) => c.id} empty="No cinemas assigned to you yet." columns={[
        { key: "n", header: "Cinema", cell: (c) => <div><p className="font-semibold">{c.name}</p><p className="text-xs text-muted">{c.address}</p></div> },
        { key: "c", header: "City", cell: (c) => `${c.locality ? c.locality + ", " : ""}${c.city}` },
        { key: "s", header: "Screens", cell: (c) => c.screens },
        { key: "f", header: "Options", cell: (c) => <div className="flex flex-wrap gap-1">{c.allowPayAtVenue && <Badge tone="brand">Pay at venue</Badge>}{!c.cancellationAllowed && <Badge tone="warning">No cancellations</Badge>}{!c.active && <Badge tone="danger">Inactive</Badge>}</div> },
        { key: "a", header: "", className: "text-right", cell: (c) => (
          <div className="flex justify-end gap-1">
            <Link href={`/admin/screens?cinemaId=${c.id}`} className="inline-flex h-9 items-center gap-1 rounded-xl px-3 text-sm font-semibold text-brand-700 hover:bg-brand-50"><LayoutGrid className="size-4" />Screens</Link>
            <Button size="sm" variant="ghost" onClick={() => setEditing(c)} aria-label={`Edit ${c.name}`}><Pencil className="size-4" /></Button>
          </div>) },
      ]} />
      {editing && <CinemaForm cinema={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
    </div>
  );
}

function CinemaForm({ cinema, onClose }: { cinema: AdminCinema | null; onClose: () => void }) {
  const qc = useQueryClient();
  const cities = useQuery({ queryKey: ["cities"], queryFn: catalogApi.cities });
  const { register, handleSubmit, watch, setValue } = useForm<FormValues>({
    defaultValues: cinema ? { ...cinema, locality: cinema.locality ?? "" } : { name: "", cityId: 1, locality: "", address: "", facilities: ["M_TICKET"], allowPayAtVenue: false, cancellationAllowed: true, active: true },
  });
  const save = useMutation({
    mutationFn: (v: FormValues) => {
      const body = { ...v, cityId: Number(v.cityId), latitude: v.latitude ? Number(v.latitude) : undefined, longitude: v.longitude ? Number(v.longitude) : undefined };
      return cinema ? adminApi.updateCinema(cinema.id, body) : adminApi.createCinema(body);
    },
    onSuccess: () => { toast.success("Cinema saved"); qc.invalidateQueries({ queryKey: ["admin", "cinemas"] }); onClose(); },
    onError: (e: ApiError) => toast.error(e.fields ? Object.values(e.fields).join(" · ") : e.message),
  });
  const facilities = watch("facilities");
  return (
    <Modal open onClose={onClose} title={cinema ? `Edit ${cinema.name}` : "New cinema"} wide>
      <form className="grid gap-4 sm:grid-cols-2" onSubmit={handleSubmit((v) => save.mutate(v))}>
        <Field label="Name" htmlFor="n" className="sm:col-span-2"><Input id="n" required {...register("name")} /></Field>
        <Field label="City" htmlFor="c"><Select id="c" {...register("cityId")} disabled={!!cinema && false}>{cities.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</Select></Field>
        <Field label="Locality" htmlFor="l"><Input id="l" {...register("locality")} /></Field>
        <Field label="Address" htmlFor="ad" className="sm:col-span-2"><Input id="ad" required {...register("address")} /></Field>
        <Field label="Latitude" htmlFor="la"><Input id="la" type="number" step="any" {...register("latitude")} /></Field>
        <Field label="Longitude" htmlFor="lo"><Input id="lo" type="number" step="any" {...register("longitude")} /></Field>
        <Field label="Facilities" className="sm:col-span-2"><div className="flex flex-wrap gap-2">{FACILITIES.map((f) => (
          <Chip key={f} active={facilities.includes(f)} onClick={() => setValue("facilities", facilities.includes(f) ? facilities.filter((x) => x !== f) : [...facilities, f])}>{f.replace("_", " ").toLowerCase()}</Chip>))}</div></Field>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-brand-600" {...register("allowPayAtVenue")} />Allow pay at venue</label>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-brand-600" {...register("cancellationAllowed")} />Allow customer cancellations</label>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-brand-600" {...register("active")} />Active</label>
        <div className="flex justify-end gap-2 sm:col-span-2"><Button type="button" variant="ghost" onClick={onClose}>Cancel</Button><Button type="submit" loading={save.isPending}>Save</Button></div>
      </form>
    </Modal>
  );
}
