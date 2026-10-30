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
import { Badge, StatusBadge } from "@/components/ui/Badge";
import { ApiError } from "@/lib/api-client";
import { dateTime, inr } from "@/lib/format";
import type { AdminEvent, AdminSession } from "@/types/admin";

// datetime-local <-> ISO in IST
const toLocal = (iso: string) => new Date(new Date(iso).getTime() + 330 * 60_000).toISOString().slice(0, 16);
const fromLocal = (v: string) => new Date(v + ":00+05:30").toISOString();

export default function AdminEventsPage() {
  const [editing, setEditing] = useState<AdminEvent | "new" | null>(null);
  const list = useQuery({ queryKey: ["admin", "events"], queryFn: adminApi.events });
  return (
    <div>
      <PageHeader title="Events" subtitle="Concerts, comedy, plays, sports and activities with ticket tiers and inventory."
        actions={<Button icon={<Plus className="size-4" />} onClick={() => setEditing("new")}>New event</Button>} />
      <DataTable caption="Events" loading={list.isLoading} rows={list.data?.content} rowKey={(e) => e.id} columns={[
        { key: "t", header: "Event", cell: (e) => <div><p className="font-semibold">{e.title}</p><p className="text-xs text-muted">{e.venueName}, {e.city}</p></div> },
        { key: "c", header: "Category", cell: (e) => <Badge tone="brand">{e.categoryCode.replace("_", " ").toLowerCase()}</Badge> },
        { key: "s", header: "Sessions", cell: (e) => <span className="text-xs">{e.sessions.length} · next {e.sessions[0] ? dateTime(e.sessions[0].startTime) : "—"}</span> },
        { key: "i", header: "Sold / capacity", cell: (e) => { const cats = e.sessions.flatMap((s) => s.categories); const sold = cats.reduce((n, c) => n + (c.sold ?? 0), 0); const cap = cats.reduce((n, c) => n + c.capacity, 0); return `${sold.toLocaleString("en-IN")} / ${cap.toLocaleString("en-IN")}`; } },
        { key: "st", header: "Status", cell: (e) => <StatusBadge status={e.status === "PUBLISHED" ? "CONFIRMED" : e.status} /> },
        { key: "a", header: "", className: "text-right", cell: (e) => <Button size="sm" variant="ghost" onClick={() => setEditing(e)} aria-label={`Edit ${e.title}`}><Pencil className="size-4" /></Button> },
      ]} />
      {editing && <EventForm event={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
    </div>
  );
}

function EventForm({ event, onClose }: { event: AdminEvent | null; onClose: () => void }) {
  const qc = useQueryClient();
  const cats = useQuery({ queryKey: ["event-cats", "all"], queryFn: () => catalogApi.eventCategories() });
  const venues = useQuery({ queryKey: ["admin", "venues"], queryFn: adminApi.venues });
  const [f, setF] = useState(() => event ? { ...event } : {
    title: "", description: "", categoryCode: "CONCERT", venueId: 0, artist: "", teamHome: "", teamAway: "", languages: "", durationMin: 120,
    ageRestriction: "All ages", posterUrl: "", bannerUrl: "", featured: false, status: "PUBLISHED", sessions: [] as AdminSession[],
  });
  const set = <K extends keyof typeof f>(k: K, v: (typeof f)[K]) => setF((p) => ({ ...p, [k]: v }));
  const setSessions = (fn: (s: AdminSession[]) => AdminSession[]) => setF((p) => ({ ...p, sessions: fn(structuredClone(p.sessions)) }));
  const save = useMutation({
    mutationFn: () => {
      const body = { ...f, venueId: Number(f.venueId), durationMin: f.durationMin ? Number(f.durationMin) : undefined,
        sessions: f.sessions.map((s) => ({ ...s, categories: s.categories.map((c) => ({ ...c, price: Number(c.price), capacity: Number(c.capacity), maxPerBooking: Number(c.maxPerBooking) })) })) };
      return event ? adminApi.updateEvent(event.id, body) : adminApi.createEvent(body);
    },
    onSuccess: () => { toast.success("Event saved"); qc.invalidateQueries({ queryKey: ["admin", "events"] }); onClose(); },
    onError: (e: ApiError) => toast.error(e.fields ? Object.entries(e.fields).map(([k, v]) => `${k}: ${v}`).join(" · ") : e.message),
  });
  const isSports = cats.data?.find((c) => c.code === f.categoryCode)?.vertical === "SPORTS";

  return (
    <Modal open onClose={onClose} title={event ? `Edit ${event.title}` : "New event"} wide>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Title" htmlFor="et" className="sm:col-span-2"><Input id="et" value={f.title} onChange={(e) => set("title", e.target.value)} /></Field>
        <Field label="Description" htmlFor="ed" className="sm:col-span-2"><Textarea id="ed" value={f.description} onChange={(e) => set("description", e.target.value)} /></Field>
        <Field label="Category" htmlFor="ec"><Select id="ec" value={f.categoryCode} onChange={(e) => set("categoryCode", e.target.value)}>{cats.data?.map((c) => <option key={c.code} value={c.code}>{c.name} ({c.vertical.toLowerCase()})</option>)}</Select></Field>
        <Field label="Venue" htmlFor="ev"><Select id="ev" value={f.venueId} onChange={(e) => set("venueId", Number(e.target.value))}><option value={0}>Select venue…</option>{venues.data?.map((v) => <option key={v.id} value={v.id}>{v.name} — {v.city}</option>)}</Select></Field>
        {isSports ? (
          <>
            <Field label="Home team" htmlFor="th"><Input id="th" value={f.teamHome ?? ""} onChange={(e) => set("teamHome", e.target.value)} /></Field>
            <Field label="Away team" htmlFor="ta"><Input id="ta" value={f.teamAway ?? ""} onChange={(e) => set("teamAway", e.target.value)} /></Field>
          </>
        ) : <Field label="Artist / host" htmlFor="ea" className="sm:col-span-2"><Input id="ea" value={f.artist ?? ""} onChange={(e) => set("artist", e.target.value)} /></Field>}
        <Field label="Languages" htmlFor="el"><Input id="el" value={f.languages ?? ""} onChange={(e) => set("languages", e.target.value)} /></Field>
        <Field label="Age restriction" htmlFor="ear"><Input id="ear" value={f.ageRestriction ?? ""} onChange={(e) => set("ageRestriction", e.target.value)} /></Field>
        <Field label="Duration (minutes)" htmlFor="edu"><Input id="edu" type="number" value={f.durationMin ?? ""} onChange={(e) => set("durationMin", Number(e.target.value))} /></Field>
        <Field label="Status" htmlFor="es"><Select id="es" value={f.status} onChange={(e) => set("status", e.target.value)}><option>PUBLISHED</option><option>DRAFT</option><option>CANCELLED</option></Select></Field>
        <Field label="Poster URL" htmlFor="ep"><Input id="ep" value={f.posterUrl ?? ""} onChange={(e) => set("posterUrl", e.target.value)} /></Field>
        <Field label="Banner URL" htmlFor="eb"><Input id="eb" value={f.bannerUrl ?? ""} onChange={(e) => set("bannerUrl", e.target.value)} /></Field>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-brand-600" checked={f.featured} onChange={(e) => set("featured", e.target.checked)} />Featured</label>

        <div className="sm:col-span-2">
          <div className="mb-2 flex items-center justify-between">
            <h3 className="font-bold">Sessions & ticket categories</h3>
            <Button size="sm" variant="secondary" icon={<Plus className="size-4" />} onClick={() => setSessions((s) => {
              const start = new Date(Date.now() + 7 * 86400_000); start.setMinutes(0, 0, 0);
              return [...s, { startTime: start.toISOString(), endTime: new Date(start.getTime() + 2 * 3600_000).toISOString(),
                categories: [{ name: "General", price: 499, capacity: 100, maxPerBooking: 6 }] }];
            })}>Add session</Button>
          </div>
          <div className="space-y-3">
            {f.sessions.map((s, si) => (
              <div key={si} className="rounded-2xl border border-line p-3">
                <div className="grid grid-cols-[1fr_1fr_auto] items-end gap-2">
                  <Field label="Starts" htmlFor={`ss${si}`}><Input id={`ss${si}`} type="datetime-local" value={toLocal(s.startTime)} onChange={(e) => setSessions((x) => { x[si].startTime = fromLocal(e.target.value); return x; })} /></Field>
                  <Field label="Ends" htmlFor={`se${si}`}><Input id={`se${si}`} type="datetime-local" value={toLocal(s.endTime)} onChange={(e) => setSessions((x) => { x[si].endTime = fromLocal(e.target.value); return x; })} /></Field>
                  <Button size="sm" variant="ghost" aria-label="Remove session" onClick={() => setSessions((x) => x.filter((_, i) => i !== si))}><Trash2 className="size-4 text-rose-600" /></Button>
                </div>
                <table className="mt-3 w-full text-sm">
                  <thead className="text-xs text-muted"><tr><th className="text-left font-semibold">Tier</th><th className="text-left font-semibold">Price ₹</th><th className="text-left font-semibold">Capacity</th><th className="text-left font-semibold">Max/booking</th><th className="text-left font-semibold">Sold</th><th /></tr></thead>
                  <tbody>
                    {s.categories.map((c, ci) => (
                      <tr key={ci}>
                        <td className="pr-2 pt-1"><Input className="h-9" aria-label="Tier name" value={c.name} onChange={(e) => setSessions((x) => { x[si].categories[ci].name = e.target.value; return x; })} /></td>
                        <td className="pr-2 pt-1"><Input className="h-9" type="number" aria-label="Price" value={c.price} onChange={(e) => setSessions((x) => { x[si].categories[ci].price = Number(e.target.value); return x; })} /></td>
                        <td className="pr-2 pt-1"><Input className="h-9" type="number" aria-label="Capacity" value={c.capacity} onChange={(e) => setSessions((x) => { x[si].categories[ci].capacity = Number(e.target.value); return x; })} /></td>
                        <td className="pr-2 pt-1"><Input className="h-9" type="number" aria-label="Max per booking" value={c.maxPerBooking} onChange={(e) => setSessions((x) => { x[si].categories[ci].maxPerBooking = Number(e.target.value); return x; })} /></td>
                        <td className="pr-2 pt-1 text-xs text-muted">{c.sold ?? 0}{c.held ? ` (+${c.held} held)` : ""}</td>
                        <td className="pt-1"><Button size="sm" variant="ghost" aria-label="Remove tier" disabled={!!c.sold} onClick={() => setSessions((x) => { x[si].categories.splice(ci, 1); return x; })}><Trash2 className="size-4" /></Button></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <Button size="sm" variant="ghost" className="mt-2" icon={<Plus className="size-4" />} onClick={() => setSessions((x) => { x[si].categories.push({ name: "VIP", price: 1499, capacity: 20, maxPerBooking: 4 }); return x; })}>Add tier</Button>
                <p className="mt-1 text-xs text-muted">From {inr(Math.min(...s.categories.map((c) => c.price)))} · {s.categories.reduce((n, c) => n + Number(c.capacity), 0)} tickets</p>
              </div>
            ))}
          </div>
        </div>
        <div className="flex justify-end gap-2 sm:col-span-2">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button disabled={!f.title || !f.venueId || !f.sessions.length} loading={save.isPending} onClick={() => save.mutate()}>Save event</Button>
        </div>
      </div>
    </Modal>
  );
}
