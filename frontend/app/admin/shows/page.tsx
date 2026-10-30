"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Ban, IndianRupee, Plus } from "lucide-react";
import { adminApi } from "@/services";
import { PageHeader } from "@/components/admin/AdminShell";
import { DataTable } from "@/components/admin/DataTable";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { Field, Input, Select } from "@/components/ui/Field";
import { StatusBadge } from "@/components/ui/Badge";
import { ApiError } from "@/lib/api-client";
import { inr, time, todayIST } from "@/lib/format";
import type { AdminShow } from "@/types/admin";

export default function AdminShowsPage() {
  const qc = useQueryClient();
  const cinemas = useQuery({ queryKey: ["admin", "cinemas"], queryFn: adminApi.cinemas });
  const [cinemaId, setCinemaId] = useState<number>();
  const [date, setDate] = useState(todayIST());
  const [creating, setCreating] = useState(false);
  const [repricing, setRepricing] = useState<AdminShow | null>(null);
  const effectiveCinema = cinemaId ?? cinemas.data?.[0]?.id;
  const shows = useQuery({ queryKey: ["admin", "shows", effectiveCinema, date], queryFn: () => adminApi.shows({ cinemaId: effectiveCinema, date }), enabled: !!effectiveCinema });
  const cancel = useMutation({
    mutationFn: (id: number) => adminApi.updateShow(id, { status: "CANCELLED" }),
    onSuccess: () => { toast.success("Show cancelled. Ticket holders are being refunded."); qc.invalidateQueries({ queryKey: ["admin", "shows"] }); },
    onError: (e: ApiError) => toast.error(e.message),
  });

  return (
    <div>
      <PageHeader title="Shows & pricing" subtitle="Schedule shows per screen. Overlapping shows on a screen are rejected by the database."
        actions={<Button icon={<Plus className="size-4" />} onClick={() => setCreating(true)}>Schedule show</Button>} />
      <div className="mb-4 flex flex-wrap gap-3">
        <Select aria-label="Cinema" className="max-w-xs" value={effectiveCinema ?? ""} onChange={(e) => setCinemaId(Number(e.target.value))}>
          {cinemas.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </Select>
        <Input aria-label="Date" type="date" className="w-44" value={date} onChange={(e) => setDate(e.target.value)} />
      </div>
      <DataTable caption="Shows" loading={shows.isLoading} rows={shows.data?.content} rowKey={(s) => s.id} empty="No shows on this date." columns={[
        { key: "t", header: "Time", cell: (s) => <b>{time(s.startTime)} – {time(s.endTime)}</b> },
        { key: "m", header: "Movie", cell: (s) => <div><p className="font-semibold">{s.movieTitle}</p><p className="text-xs text-muted">{s.language} · {s.format}</p></div> },
        { key: "sc", header: "Screen", cell: (s) => s.screenName },
        { key: "p", header: "Base price", cell: (s) => inr(s.basePrice) },
        { key: "o", header: "Sold", cell: (s) => (
          <div className="w-28"><p className="text-xs">{s.booked}/{s.total}</p><div className="mt-1 h-1.5 rounded-full bg-mist"><div className="h-1.5 rounded-full bg-brand-600" style={{ width: `${s.total ? (s.booked / s.total) * 100 : 0}%` }} /></div></div>) },
        { key: "s", header: "Status", cell: (s) => <StatusBadge status={s.status === "SCHEDULED" ? "CONFIRMED" : s.status} /> },
        { key: "a", header: "", className: "text-right", cell: (s) => s.status === "SCHEDULED" && (
          <div className="flex justify-end gap-1">
            <Button size="sm" variant="ghost" onClick={() => setRepricing(s)} aria-label="Change price"><IndianRupee className="size-4" /></Button>
            <Button size="sm" variant="ghost" onClick={() => confirm(`Cancel ${s.movieTitle} at ${time(s.startTime)}? ${s.booked} booked seats will be refunded.`) && cancel.mutate(s.id)} aria-label="Cancel show"><Ban className="size-4 text-rose-600" /></Button>
          </div>) },
      ]} />
      {creating && <CreateShow defaultCinema={effectiveCinema} defaultDate={date} onClose={() => setCreating(false)} />}
      {repricing && <Reprice show={repricing} onClose={() => setRepricing(null)} />}
    </div>
  );
}

function CreateShow({ defaultCinema, defaultDate, onClose }: { defaultCinema?: number; defaultDate: string; onClose: () => void }) {
  const qc = useQueryClient();
  const cinemas = useQuery({ queryKey: ["admin", "cinemas"], queryFn: adminApi.cinemas });
  const movies = useQuery({ queryKey: ["admin", "movies", ""], queryFn: () => adminApi.movies() });
  const [cinemaId, setCinemaId] = useState(defaultCinema);
  const screens = useQuery({ queryKey: ["admin", "screens", cinemaId], queryFn: () => adminApi.screens(cinemaId!), enabled: !!cinemaId });
  const [f, setF] = useState({ movieId: 0, screenId: 0, date: defaultDate, startTime: "18:30", endTime: "", format: "2D", language: "", basePrice: 200 });
  const movie = movies.data?.content.find((m) => m.id === f.movieId);
  const screen = screens.data?.find((s) => s.id === f.screenId);
  const formats = useMemo(() => (movie && screen ? movie.formats.filter((x) => screen.formats.includes(x)) : []), [movie, screen]);
  const create = useMutation({
    mutationFn: () => adminApi.createShow({ ...f, endTime: f.endTime || undefined }),
    onSuccess: () => { toast.success("Show scheduled — it's bookable right away"); qc.invalidateQueries({ queryKey: ["admin", "shows"] }); onClose(); },
    onError: (e: ApiError) => toast.error(e.message),
  });
  return (
    <Modal open onClose={onClose} title="Schedule a show" wide>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Movie" htmlFor="mv" className="sm:col-span-2">
          <Select id="mv" value={f.movieId} onChange={(e) => { const m = movies.data?.content.find((x) => x.id === Number(e.target.value)); setF({ ...f, movieId: Number(e.target.value), language: m?.languages[0] ?? "" }); }}>
            <option value={0}>Select movie…</option>
            {movies.data?.content.filter((m) => m.active).map((m) => <option key={m.id} value={m.id}>{m.title} ({m.durationMin} min)</option>)}
          </Select>
        </Field>
        <Field label="Cinema" htmlFor="cn"><Select id="cn" value={cinemaId ?? ""} onChange={(e) => { setCinemaId(Number(e.target.value)); setF({ ...f, screenId: 0 }); }}>{cinemas.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</Select></Field>
        <Field label="Screen" htmlFor="sc"><Select id="sc" value={f.screenId} onChange={(e) => setF({ ...f, screenId: Number(e.target.value) })}><option value={0}>Select screen…</option>{screens.data?.filter((s) => s.active).map((s) => <option key={s.id} value={s.id}>{s.name} · {s.screenType} · {s.capacity} seats</option>)}</Select></Field>
        <Field label="Date" htmlFor="dt"><Input id="dt" type="date" min={todayIST()} value={f.date} onChange={(e) => setF({ ...f, date: e.target.value })} /></Field>
        <div className="grid grid-cols-2 gap-2">
          <Field label="Start" htmlFor="st"><Input id="st" type="time" value={f.startTime} onChange={(e) => setF({ ...f, startTime: e.target.value })} /></Field>
          <Field label="End (optional)" htmlFor="et" hint="Default: runtime + 20 min"><Input id="et" type="time" value={f.endTime} onChange={(e) => setF({ ...f, endTime: e.target.value })} /></Field>
        </div>
        <Field label="Format" htmlFor="fm" hint={movie && screen && !formats.length ? "This screen doesn't support any of the movie's formats" : undefined}>
          <Select id="fm" value={f.format} onChange={(e) => setF({ ...f, format: e.target.value })}>{(formats.length ? formats : ["2D"]).map((x) => <option key={x}>{x}</option>)}</Select>
        </Field>
        <Field label="Language" htmlFor="lg"><Select id="lg" value={f.language} onChange={(e) => setF({ ...f, language: e.target.value })}>{movie?.languages.map((l) => <option key={l}>{l}</option>)}</Select></Field>
        <Field label="Base price (Silver)" htmlFor="bp" hint="Gold, Premium and VIP add their seat-type premium"><Input id="bp" type="number" min={0} value={f.basePrice} onChange={(e) => setF({ ...f, basePrice: Number(e.target.value) })} /></Field>
        <div className="flex items-end justify-end gap-2 sm:col-span-2">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button disabled={!f.movieId || !f.screenId || !f.language} loading={create.isPending} onClick={() => create.mutate()}>Schedule</Button>
        </div>
      </div>
    </Modal>
  );
}

function Reprice({ show, onClose }: { show: AdminShow; onClose: () => void }) {
  const qc = useQueryClient();
  const [price, setPrice] = useState(show.basePrice);
  const m = useMutation({
    mutationFn: () => adminApi.updateShow(show.id, { basePrice: price }),
    onSuccess: () => { toast.success("Price updated for unsold seats"); qc.invalidateQueries({ queryKey: ["admin", "shows"] }); onClose(); },
    onError: (e: ApiError) => toast.error(e.message),
  });
  return (
    <Modal open onClose={onClose} title={`Ticket pricing · ${show.movieTitle} ${time(show.startTime)}`}>
      <Field label="Base price (₹)" htmlFor="rp" hint="Seats already sold keep the price the customer paid."><Input id="rp" type="number" min={0} value={price} onChange={(e) => setPrice(Number(e.target.value))} /></Field>
      <div className="mt-5 flex justify-end gap-2"><Button variant="ghost" onClick={onClose}>Cancel</Button><Button loading={m.isPending} onClick={() => m.mutate()}>Update price</Button></div>
    </Modal>
  );
}
