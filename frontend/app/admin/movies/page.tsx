"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useFieldArray, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { Film, Lock, Pencil, Plus, RefreshCw, Trash2 } from "lucide-react";
import { adminApi, catalogApi } from "@/services";
import { PageHeader } from "@/components/admin/AdminShell";
import { DataTable } from "@/components/admin/DataTable";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { Field, Input, Select, Textarea } from "@/components/ui/Field";
import { Chip } from "@/components/ui/Chip";
import { Badge, StatusBadge } from "@/components/ui/Badge";
import { Poster } from "@/components/ui/Poster";
import { ApiError } from "@/lib/api-client";
import type { AdminMovie } from "@/types/admin";

const LANGS = ["Telugu", "Hindi", "Tamil", "English", "Malayalam", "Kannada", "Bengali", "Marathi", "Punjabi", "Japanese", "Korean", "French", "Spanish"];
const FORMATS = ["2D", "3D", "IMAX", "4DX", "DOLBY_ATMOS"];

const schema = z.object({
  title: z.string().trim().min(1, "Required").max(160),
  tagline: z.string().max(240).optional(),
  synopsis: z.string().trim().min(10, "Add a short synopsis"),
  durationMin: z.coerce.number().int().min(30).max(400),
  certificate: z.enum(["U", "UA", "UA7+", "UA13+", "UA16+", "A", "S"]),
  releaseDate: z.string().min(1, "Required"),
  posterUrl: z.string().optional(),
  backdropUrl: z.string().optional(),
  trailerUrl: z.string().optional(),
  status: z.enum(["NOW_SHOWING", "COMING_SOON", "CLASSIC", "ARCHIVED"]),
  mediaLocked: z.boolean(),
  featured: z.boolean(),
  active: z.boolean(),
  formats: z.array(z.string()).min(1, "Pick at least one format"),
  languages: z.array(z.string()).min(1, "Pick at least one language"),
  genres: z.string().min(1, "Add at least one genre"),
  directors: z.string().optional(),
  cast: z.array(z.object({ name: z.string().min(1), character: z.string().optional(), crewRole: z.string().optional() })),
});
type FormValues = z.infer<typeof schema>;

const empty: FormValues = { title: "", tagline: "", synopsis: "", durationMin: 120, certificate: "UA", releaseDate: "", posterUrl: "", backdropUrl: "", trailerUrl: "",
  status: "COMING_SOON", mediaLocked: false, featured: false, active: true, formats: ["2D"], languages: [], genres: "", directors: "", cast: [] };

export default function AdminMoviesPage() {
  const qc = useQueryClient();
  const [q, setQ] = useState("");
  const [editing, setEditing] = useState<AdminMovie | "new" | null>(null);
  const list = useQuery({ queryKey: ["admin", "movies", q], queryFn: () => adminApi.movies(q || undefined) });
  const del = useMutation({
    mutationFn: adminApi.deleteMovie,
    onSuccess: () => { toast.success("Movie deactivated"); qc.invalidateQueries({ queryKey: ["admin", "movies"] }); },
  });
  const syncOne = useMutation({
    mutationFn: adminApi.syncMovieMedia,
    onSuccess: (r) => {
      if (r.matched) toast.success(`${r.title}: ${[r.poster && "poster", r.trailer && "trailer"].filter(Boolean).join(" & ") || "details"} updated`);
      else toast.warning(`${r.title}: ${r.note}. Paste a poster URL and YouTube link by hand.`);
      qc.invalidateQueries({ queryKey: ["admin", "movies"] });
    },
    onError: (e: ApiError) => toast.error(e.message),
  });

  return (
    <div>
      <PageHeader title="Movies" subtitle="Create, edit and retire titles. Changes are live immediately."
        actions={<Button icon={<Plus className="size-4" />} onClick={() => setEditing("new")}>New movie</Button>} />
      <MediaPanel />
      <Input placeholder="Search titles…" value={q} onChange={(e) => setQ(e.target.value)} className="mb-4 max-w-sm" aria-label="Search movies" />
      <DataTable caption="Movies" loading={list.isLoading} rows={list.data?.content} rowKey={(m) => m.id} columns={[
        { key: "t", header: "Title", cell: (m) => (
          <div className="flex items-center gap-3"><div className="relative h-14 w-10 shrink-0 overflow-hidden rounded-lg"><Poster src={m.posterUrl} alt="" sizes="40px" /></div>
            <div><p className="font-semibold">{m.title}</p><p className="text-xs text-muted">{m.genres.join(", ")}</p>
              <p className="mt-0.5 flex items-center gap-1 text-[11px] text-muted">{m.mediaLocked && <Lock className="size-3" aria-label="Media locked" />}{m.trailerUrl ? "Trailer ✓" : "No trailer"}{m.mediaSyncNote ? ` · ${m.mediaSyncNote}` : ""}</p></div></div>) },
        { key: "l", header: "Languages", cell: (m) => <span className="text-xs">{m.languages.join(", ")}</span> },
        { key: "f", header: "Formats", cell: (m) => <div className="flex flex-wrap gap-1">{m.formats.map((f) => <Badge key={f}>{f}</Badge>)}</div> },
        { key: "r", header: "Release", cell: (m) => m.releaseDate },
        { key: "s", header: "Status", cell: (m) => <div className="flex gap-1"><StatusBadge status={m.active ? m.status : "ARCHIVED"} />{m.featured && <Badge tone="gold">Featured</Badge>}</div> },
        { key: "rt", header: "Rating", cell: (m) => (m.votes ? `${Number(m.rating).toFixed(1)} (${m.votes})` : m.imdbRating ? `IMDb ${m.imdbRating}` : "—") },
        { key: "a", header: "", className: "text-right", cell: (m) => (
          <div className="flex justify-end gap-1">
            <Button size="sm" variant="ghost" onClick={() => syncOne.mutate(m.id)} loading={syncOne.isPending && syncOne.variables === m.id}
              aria-label={`Fetch poster and trailer for ${m.title}`} title="Fetch poster & trailer from TMDB"><RefreshCw className="size-4" /></Button>
            <Button size="sm" variant="ghost" onClick={() => setEditing(m)} aria-label={`Edit ${m.title}`}><Pencil className="size-4" /></Button>
            {m.active && <Button size="sm" variant="ghost" onClick={() => confirm(`Deactivate ${m.title}?`) && del.mutate(m.id)} aria-label={`Deactivate ${m.title}`}><Trash2 className="size-4 text-rose-600" /></Button>}
          </div>) },
      ]} />
      {editing && <MovieForm movie={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
    </div>
  );
}

function MovieForm({ movie, onClose }: { movie: AdminMovie | null; onClose: () => void }) {
  const qc = useQueryClient();
  const genres = useQuery({ queryKey: ["genres"], queryFn: catalogApi.genres });
  const form = useForm<FormValues>({
    resolver: zodResolver(schema) as never,
    defaultValues: movie ? { ...movie, tagline: movie.tagline ?? "", posterUrl: movie.posterUrl ?? "", backdropUrl: movie.backdropUrl ?? "", trailerUrl: movie.trailerUrl ?? "",
      certificate: movie.certificate as FormValues["certificate"], status: movie.status as FormValues["status"], mediaLocked: movie.mediaLocked ?? false,
      genres: movie.genres.join(", "), directors: movie.directors.join(", ") } : empty,
  });
  const { register, handleSubmit, watch, setValue, control, formState: { errors, isSubmitting } } = form;
  const cast = useFieldArray({ control, name: "cast" });
  const toggle = (field: "formats" | "languages", v: string) => {
    const cur = watch(field);
    setValue(field, cur.includes(v) ? cur.filter((x) => x !== v) : [...cur, v], { shouldValidate: true });
  };
  const save = useMutation({
    mutationFn: (v: FormValues) => {
      const body = { ...v, genres: v.genres.split(",").map((s) => s.trim()).filter(Boolean), directors: (v.directors ?? "").split(",").map((s) => s.trim()).filter(Boolean),
        cast: v.cast.filter((c) => c.name.trim()) };
      return movie ? adminApi.updateMovie(movie.id, body) : adminApi.createMovie(body);
    },
    onSuccess: () => { toast.success(movie ? "Movie updated" : "Movie created"); qc.invalidateQueries({ queryKey: ["admin", "movies"] }); onClose(); },
    onError: (e: ApiError) => toast.error(e.fields ? Object.values(e.fields).join(" · ") : e.message),
  });

  return (
    <Modal open onClose={onClose} title={movie ? `Edit ${movie.title}` : "New movie"} wide>
      <form className="grid gap-4 sm:grid-cols-2" onSubmit={handleSubmit((v) => save.mutate(v))} noValidate>
        <Field label="Title" htmlFor="t" error={errors.title?.message} className="sm:col-span-2"><Input id="t" {...register("title")} /></Field>
        <Field label="Tagline" htmlFor="tg" className="sm:col-span-2"><Input id="tg" {...register("tagline")} /></Field>
        <Field label="Synopsis" htmlFor="sy" error={errors.synopsis?.message} className="sm:col-span-2"><Textarea id="sy" {...register("synopsis")} /></Field>
        <Field label="Duration (minutes)" htmlFor="du" error={errors.durationMin?.message}><Input id="du" type="number" {...register("durationMin")} /></Field>
        <Field label="Certificate" htmlFor="ce"><Select id="ce" {...register("certificate")}>{["U", "UA", "UA7+", "UA13+", "UA16+", "A", "S"].map((c) => <option key={c}>{c}</option>)}</Select></Field>
        <Field label="Release date" htmlFor="rd" error={errors.releaseDate?.message}><Input id="rd" type="date" {...register("releaseDate")} /></Field>
        <Field label="Status" htmlFor="st"><Select id="st" {...register("status")}><option value="NOW_SHOWING">Now showing</option><option value="COMING_SOON">Coming soon</option><option value="CLASSIC">Classic re-release</option><option value="ARCHIVED">Archived</option></Select></Field>
        <Field label="Formats" error={errors.formats?.message} className="sm:col-span-2"><div className="flex flex-wrap gap-2">{FORMATS.map((f) => <Chip key={f} active={watch("formats").includes(f)} onClick={() => toggle("formats", f)}>{f}</Chip>)}</div></Field>
        <Field label="Languages" error={errors.languages?.message} className="sm:col-span-2"><div className="flex flex-wrap gap-2">{LANGS.map((l) => <Chip key={l} active={watch("languages").includes(l)} onClick={() => toggle("languages", l)}>{l}</Chip>)}</div></Field>
        <Field label="Genres (comma separated)" htmlFor="gn" error={errors.genres?.message} hint={genres.data?.map((g) => g.name).join(", ")} className="sm:col-span-2"><Input id="gn" {...register("genres")} /></Field>
        <Field label="Directors (comma separated)" htmlFor="dr" className="sm:col-span-2"><Input id="dr" {...register("directors")} /></Field>
        <Field label="Poster URL" htmlFor="pu" hint="Filled from TMDB automatically, or paste https://…"><Input id="pu" {...register("posterUrl")} /></Field>
        <Field label="Backdrop URL" htmlFor="bu"><Input id="bu" {...register("backdropUrl")} /></Field>
        <Field label="YouTube trailer" htmlFor="tu" hint="Paste any YouTube link (watch, youtu.be or shorts). It is converted to a player link." className="sm:col-span-2"><Input id="tu" placeholder="https://www.youtube.com/watch?v=…" {...register("trailerUrl")} /></Field>
        <label className="flex items-center gap-2 text-sm sm:col-span-2"><input type="checkbox" className="accent-brand-600" {...register("mediaLocked")} />Lock poster &amp; trailer (stop automatic TMDB updates for this movie)</label>
        <div className="sm:col-span-2">
          <div className="mb-2 flex items-center justify-between"><span className="text-sm font-semibold text-ink-soft">Cast & crew</span>
            <Button type="button" size="sm" variant="secondary" onClick={() => cast.append({ name: "", character: "", crewRole: "" })} icon={<Plus className="size-4" />}>Add</Button></div>
          <div className="space-y-2">
            {cast.fields.map((f, i) => (
              <div key={f.id} className="grid grid-cols-[1fr_1fr_1fr_auto] gap-2">
                <Input placeholder="Name" aria-label="Name" {...register(`cast.${i}.name`)} />
                <Input placeholder="Character" aria-label="Character" {...register(`cast.${i}.character`)} />
                <Input placeholder="Crew role (e.g. Music)" aria-label="Crew role" {...register(`cast.${i}.crewRole`)} />
                <Button type="button" variant="ghost" size="sm" onClick={() => cast.remove(i)} aria-label="Remove"><Trash2 className="size-4" /></Button>
              </div>
            ))}
          </div>
        </div>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-brand-600" {...register("featured")} />Featured on home page</label>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-brand-600" {...register("active")} />Active</label>
        <div className="flex justify-end gap-2 sm:col-span-2"><Button type="button" variant="ghost" onClick={onClose}>Cancel</Button><Button type="submit" loading={save.isPending || isSubmitting}>Save movie</Button></div>
      </form>
    </Modal>
  );
}

/** Poster/trailer coverage and the "fetch everything from TMDB" action. */
function MediaPanel() {
  const qc = useQueryClient();
  const status = useQuery({ queryKey: ["admin", "media-status"], queryFn: adminApi.mediaStatus, refetchInterval: (q) => (q.state.data?.running ? 4000 : false) });
  const run = useMutation({
    mutationFn: () => adminApi.syncAllMedia(false),
    onSuccess: () => { toast.success("Fetching posters and trailers in the background…"); qc.invalidateQueries({ queryKey: ["admin", "media-status"] }); },
    onError: (e: ApiError) => toast.error(e.message),
  });
  const s = status.data;
  if (!s) return null;
  return (
    <section className="mb-5 flex flex-wrap items-center gap-4 rounded-2xl border border-line bg-mist/60 p-4" aria-label="Posters and trailers">
      <Film className="size-6 text-brand-600" />
      <div className="min-w-0 flex-1 text-sm">
        <p className="font-semibold">Posters &amp; trailers {s.running && <span className="ml-1 text-brand-700">· syncing…</span>}</p>
        {s.tmdbConfigured ? (
          <p className="text-muted">{s.withPoster}/{s.activeMovies} with posters · {s.withTrailer}/{s.activeMovies} with trailers · {s.matchedOnTmdb} matched on TMDB{s.youtubeConfigured ? " · YouTube fallback on" : ""}</p>
        ) : (
          <p className="text-muted">Add <code className="rounded bg-white px-1">TMDB_API_KEY</code> to <code className="rounded bg-white px-1">.env</code> and restart the backend to fetch posters and official YouTube trailers automatically.</p>
        )}
      </div>
      <Button size="sm" variant="secondary" disabled={!s.tmdbConfigured || s.running} loading={run.isPending} onClick={() => run.mutate()} icon={<RefreshCw className="size-4" />}>Fetch missing</Button>
    </section>
  );
}
