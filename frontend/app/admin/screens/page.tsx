"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowDown, ArrowUp, Ban, Brush, Columns2, Minus, Plus, Save, Trash2, AlertTriangle } from "lucide-react";
import { adminApi } from "@/services";
import { PageHeader, Panel } from "@/components/admin/AdminShell";
import { Button } from "@/components/ui/Button";
import { Field, Input, Select } from "@/components/ui/Field";
import { Chip } from "@/components/ui/Chip";
import { Tabs } from "@/components/ui/Tabs";
import { Modal } from "@/components/ui/Modal";
import { EmptyState } from "@/components/ui/States";
import { Skeleton } from "@/components/ui/Skeleton";
import { ApiError } from "@/lib/api-client";
import { inr } from "@/lib/format";
import type { AdminScreen, RowInput, SeatType } from "@/types/admin";
import { cn } from "@/lib/cn";

type Tool = "disable" | "paint" | "aisle";
const TYPE_STYLE: Record<string, string> = {
  VIP: "border-gold bg-gold-soft text-amber-800", PREMIUM: "border-brand-400 bg-brand-100 text-brand-800",
  GOLD: "border-amber-300 bg-amber-50 text-amber-700", SILVER: "border-zinc-300 bg-white text-zinc-600",
};

function ScreensInner() {
  const params = useSearchParams();
  const router = useRouter();
  const cinemas = useQuery({ queryKey: ["admin", "cinemas"], queryFn: adminApi.cinemas });
  const cinemaId = Number(params.get("cinemaId")) || cinemas.data?.[0]?.id;
  const screens = useQuery({ queryKey: ["admin", "screens", cinemaId], queryFn: () => adminApi.screens(cinemaId!), enabled: !!cinemaId });
  const seatTypes = useQuery({ queryKey: ["admin", "seat-types"], queryFn: adminApi.seatTypes });
  const [screenId, setScreenId] = useState<number>();
  const [creating, setCreating] = useState(false);
  useEffect(() => { if (screens.data?.length && !screens.data.some((s) => s.id === screenId)) setScreenId(screens.data[0].id); }, [screens.data, screenId]);
  const screen = screens.data?.find((s) => s.id === screenId);

  return (
    <div>
      <PageHeader title="Screens & seat layouts" subtitle="Design each auditorium: rows, seat types, aisles and disabled seats."
        actions={cinemaId && <Button icon={<Plus className="size-4" />} onClick={() => setCreating(true)}>New screen</Button>} />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Select aria-label="Cinema" className="max-w-sm" value={cinemaId ?? ""} onChange={(e) => router.replace(`/admin/screens?cinemaId=${e.target.value}`)}>
          {cinemas.data?.map((c) => <option key={c.id} value={c.id}>{c.name} — {c.city}</option>)}
        </Select>
        {screens.data && screens.data.length > 0 && (
          <Tabs id="screens" value={String(screenId)} onChange={(v) => setScreenId(Number(v))} tabs={screens.data.map((s) => ({ value: String(s.id), label: `${s.name} · ${s.capacity}` }))} />
        )}
      </div>
      {cinemas.isLoading || screens.isLoading || seatTypes.isLoading ? <Skeleton className="h-96 rounded-3xl" />
        : !cinemaId ? <EmptyState title="No cinemas" message="You don't manage any cinemas yet." />
        : !screen ? <EmptyState title="No screens yet" message="Create the first screen for this cinema." />
        : <SeatConfigurator key={screen.id} screen={screen} seatTypes={seatTypes.data ?? []} />}
      {creating && cinemaId && <NewScreen cinemaId={cinemaId} onClose={() => setCreating(false)} onCreated={(id) => setScreenId(id)} />}
    </div>
  );
}

function computeCols(row: RowInput) {
  const cols: number[] = [];
  let col = Math.max(0, row.offset) + 1;
  for (let n = 1; n <= row.seatCount; n++) { cols.push(col); col += row.aisleAfter.includes(n) ? 2 : 1; }
  return cols;
}

function SeatConfigurator({ screen, seatTypes }: { screen: AdminScreen; seatTypes: SeatType[] }) {
  const qc = useQueryClient();
  const [rows, setRows] = useState<RowInput[]>(() => screen.layout.map((r) => ({ ...r, seatTypeOverrides: { ...(r.seatTypeOverrides ?? {}) } })));
  const [tool, setTool] = useState<Tool>("disable");
  const [brush, setBrush] = useState(seatTypes[0]?.code ?? "SILVER");
  const [dirty, setDirty] = useState(false);
  const update = (fn: (r: RowInput[]) => RowInput[]) => { setRows((r) => fn(r.map((x) => ({ ...x })))); setDirty(true); };
  const maxCols = useMemo(() => Math.max(10, ...rows.map((r) => computeCols(r).at(-1) ?? 0)), [rows]);
  const capacity = rows.reduce((n, r) => n + r.seatCount - r.disabledSeats.filter((d) => d <= r.seatCount).length, 0);
  const delta = Object.fromEntries(seatTypes.map((t) => [t.code, t.priceDelta]));

  const save = useMutation({
    mutationFn: () => adminApi.saveLayout(screen.id, rows.map((r) => ({ ...r, disabledSeats: r.disabledSeats.filter((d) => d <= r.seatCount),
      aisleAfter: r.aisleAfter.filter((a) => a < r.seatCount) }))),
    onSuccess: () => { toast.success("Seat layout saved"); setDirty(false); qc.invalidateQueries({ queryKey: ["admin", "screens"] }); },
    onError: (e: ApiError) => toast.error(e.message),
  });

  function clickSeat(ri: number, n: number) {
    update((rs) => {
      const r = rs[ri];
      if (tool === "disable") r.disabledSeats = r.disabledSeats.includes(n) ? r.disabledSeats.filter((x) => x !== n) : [...r.disabledSeats, n];
      else if (tool === "aisle") r.aisleAfter = r.aisleAfter.includes(n) ? r.aisleAfter.filter((x) => x !== n) : [...r.aisleAfter, n];
      else {
        const o = { ...(r.seatTypeOverrides ?? {}) };
        if (brush === r.seatType) delete o[n]; else o[n] = brush;
        r.seatTypeOverrides = o;
      }
      return rs;
    });
  }
  const nextLabel = () => { const last = rows.at(-1)?.label ?? "@"; return last.length === 1 && last < "Z" ? String.fromCharCode(last.charCodeAt(0) + 1) : "A" + rows.length; };

  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_380px]">
      <Panel title={`${screen.name} preview`} actions={<span className="text-sm text-muted">{capacity} sellable seats</span>}>
        {screen.hasUpcomingShows && (
          <p className="mb-4 flex items-start gap-2 rounded-xl bg-amber-50 p-3 text-sm text-amber-800"><AlertTriangle className="mt-0.5 size-4 shrink-0" />This screen has scheduled shows. Layout changes are blocked until they finish or are cancelled, so sold seats never move.</p>
        )}
        <div className="mb-4 flex flex-wrap items-center gap-2" role="toolbar" aria-label="Seat tools">
          <Chip active={tool === "disable"} onClick={() => setTool("disable")}><Ban className="mr-1 inline size-3.5" />Disable / enable</Chip>
          <Chip active={tool === "aisle"} onClick={() => setTool("aisle")}><Columns2 className="mr-1 inline size-3.5" />Aisle after seat</Chip>
          <Chip active={tool === "paint"} onClick={() => setTool("paint")}><Brush className="mr-1 inline size-3.5" />Paint seat type</Chip>
          {tool === "paint" && <Select aria-label="Brush seat type" className="h-9 w-40" value={brush} onChange={(e) => setBrush(e.target.value)}>{seatTypes.map((t) => <option key={t.code} value={t.code}>{t.name}</option>)}</Select>}
        </div>
        <div className="overflow-x-auto rounded-2xl bg-mist/60 p-6">
          <div className="mx-auto w-max space-y-1.5">
            {rows.map((r, ri) => {
              const cols = computeCols(r);
              return (
                <div key={ri} className="flex items-center gap-3">
                  <span className="w-6 text-xs font-bold text-muted">{r.label}</span>
                  <div className="grid gap-1" style={{ gridTemplateColumns: `repeat(${maxCols}, 1.6rem)` }}>
                    {cols.map((c, i) => {
                      const n = i + 1;
                      const type = r.seatTypeOverrides?.[n] ?? r.seatType;
                      const disabled = r.disabledSeats.includes(n);
                      return (
                        <button key={n} type="button" style={{ gridColumn: c }} onClick={() => clickSeat(ri, n)}
                          aria-label={`${r.label}${n}, ${type}${disabled ? ", disabled" : ""}${r.aisleAfter.includes(n) ? ", aisle after" : ""}`}
                          className={cn("relative grid size-[1.6rem] place-items-center rounded-t-md rounded-b-sm border text-[9px] font-bold transition hover:scale-110",
                            disabled ? "border-dashed border-zinc-300 bg-transparent text-zinc-300" : TYPE_STYLE[type] ?? TYPE_STYLE.SILVER)}>
                          {disabled ? "✕" : n}
                          {r.aisleAfter.includes(n) && <span className="absolute -right-[0.55rem] top-0 h-full w-0.5 rounded bg-brand-400" aria-hidden />}
                        </button>
                      );
                    })}
                  </div>
                </div>
              );
            })}
            <div className="!mt-8 h-2 rounded-[50%] bg-gradient-to-b from-brand-300 to-transparent" aria-hidden />
            <p className="text-center text-[10px] font-bold uppercase tracking-[0.3em] text-muted">Screen</p>
          </div>
        </div>
        <div className="mt-4 flex flex-wrap gap-3 text-xs text-muted">
          {seatTypes.map((t) => <span key={t.code} className="flex items-center gap-1.5"><span className={cn("size-3.5 rounded border", TYPE_STYLE[t.code])} />{t.name} (+{inr(t.priceDelta)})</span>)}
        </div>
      </Panel>

      <Panel title="Rows" actions={<Button size="sm" variant="secondary" icon={<Plus className="size-4" />} onClick={() => update((rs) => [...rs, { label: nextLabel(), seatType: "SILVER", seatCount: 12, offset: 0, aisleAfter: [], disabledSeats: [], seatTypeOverrides: {} }])}>Add row</Button>}>
        <ul className="max-h-[560px] space-y-2 overflow-y-auto pr-1">
          {rows.map((r, ri) => (
            <li key={ri} className="rounded-2xl border border-line p-3">
              <div className="grid grid-cols-[3.5rem_1fr_auto] items-end gap-2">
                <Field label="Row" htmlFor={`rl${ri}`}><Input id={`rl${ri}`} value={r.label} maxLength={2} onChange={(e) => update((rs) => { rs[ri].label = e.target.value.toUpperCase().replace(/[^A-Z]/g, ""); return rs; })} className="h-9 px-2 text-center font-bold" /></Field>
                <Field label="Seat type" htmlFor={`rt${ri}`}><Select id={`rt${ri}`} className="h-9" value={r.seatType} onChange={(e) => update((rs) => { rs[ri].seatType = e.target.value; return rs; })}>
                  {seatTypes.map((t) => <option key={t.code} value={t.code}>{t.name} (+{inr(delta[t.code])})</option>)}</Select></Field>
                <div className="flex gap-1">
                  <Button size="sm" variant="ghost" aria-label={`Move row ${r.label} up`} disabled={ri === 0} onClick={() => update((rs) => { [rs[ri - 1], rs[ri]] = [rs[ri], rs[ri - 1]]; return rs; })}><ArrowUp className="size-4" /></Button>
                  <Button size="sm" variant="ghost" aria-label={`Move row ${r.label} down`} disabled={ri === rows.length - 1} onClick={() => update((rs) => { [rs[ri + 1], rs[ri]] = [rs[ri], rs[ri + 1]]; return rs; })}><ArrowDown className="size-4" /></Button>
                  <Button size="sm" variant="ghost" aria-label={`Remove row ${r.label}`} onClick={() => update((rs) => rs.filter((_, i) => i !== ri))}><Trash2 className="size-4 text-rose-600" /></Button>
                </div>
              </div>
              <div className="mt-2 flex items-center gap-4 text-sm">
                <Stepper label="Seats" value={r.seatCount} min={1} max={60} onChange={(v) => update((rs) => { rs[ri].seatCount = v; return rs; })} />
                <Stepper label="Indent" value={r.offset} min={0} max={20} onChange={(v) => update((rs) => { rs[ri].offset = v; return rs; })} />
              </div>
            </li>
          ))}
        </ul>
        <Button className="mt-4 w-full" size="lg" disabled={!dirty || screen.hasUpcomingShows || !rows.length} loading={save.isPending} onClick={() => save.mutate()} icon={<Save className="size-4" />}>Save layout</Button>
      </Panel>
    </div>
  );
}

function Stepper({ label, value, min, max, onChange }: { label: string; value: number; min: number; max: number; onChange: (v: number) => void }) {
  return (
    <div className="flex items-center gap-2">
      <span className="text-xs text-muted">{label}</span>
      <div className="flex items-center rounded-lg border border-line">
        <button type="button" className="grid size-7 place-items-center hover:bg-mist" onClick={() => onChange(Math.max(min, value - 1))} aria-label={`Decrease ${label}`}><Minus className="size-3" /></button>
        <span className="w-7 text-center text-sm font-semibold tabular">{value}</span>
        <button type="button" className="grid size-7 place-items-center hover:bg-mist" onClick={() => onChange(Math.min(max, value + 1))} aria-label={`Increase ${label}`}><Plus className="size-3" /></button>
      </div>
    </div>
  );
}

function NewScreen({ cinemaId, onClose, onCreated }: { cinemaId: number; onClose: () => void; onCreated: (id: number) => void }) {
  const qc = useQueryClient();
  const [name, setName] = useState("");
  const [type, setType] = useState("STANDARD");
  const [formats, setFormats] = useState<string[]>(["2D"]);
  const create = useMutation({
    mutationFn: async () => {
      const s = await adminApi.createScreen({ cinemaId, name, screenType: type, formats, active: true });
      // Start from a sensible default layout the admin can then edit visually.
      const rows = "ABCDEFGH".split("").map((label, i) => ({ label, seatType: i < 2 ? "PREMIUM" : i < 5 ? "GOLD" : "SILVER", seatCount: 12, offset: 0, aisleAfter: [3, 9], disabledSeats: [] }));
      return adminApi.saveLayout(s.id, rows);
    },
    onSuccess: (s) => { toast.success("Screen created with a starter layout"); qc.invalidateQueries({ queryKey: ["admin", "screens"] }); onCreated(s.id); onClose(); },
    onError: (e: ApiError) => toast.error(e.message),
  });
  return (
    <Modal open onClose={onClose} title="New screen">
      <div className="space-y-4">
        <Field label="Name" htmlFor="sn"><Input id="sn" value={name} onChange={(e) => setName(e.target.value)} placeholder="Screen 4" /></Field>
        <Field label="Screen type" htmlFor="stp"><Select id="stp" value={type} onChange={(e) => setType(e.target.value)}>{["STANDARD", "IMAX", "4DX", "DOLBY"].map((t) => <option key={t}>{t}</option>)}</Select></Field>
        <Field label="Supported formats"><div className="flex flex-wrap gap-2">{["2D", "3D", "IMAX", "4DX", "DOLBY_ATMOS"].map((f) => (
          <Chip key={f} active={formats.includes(f)} onClick={() => setFormats(formats.includes(f) ? formats.filter((x) => x !== f) : [...formats, f])}>{f}</Chip>))}</div></Field>
        <div className="flex justify-end gap-2"><Button variant="ghost" onClick={onClose}>Cancel</Button><Button disabled={!name.trim() || !formats.length} loading={create.isPending} onClick={() => create.mutate()}>Create</Button></div>
      </div>
    </Modal>
  );
}

export default function AdminScreensPage() {
  return <Suspense><ScreensInner /></Suspense>;
}
