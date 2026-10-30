"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { CalendarDays, Clock, Heart, Languages, MapPin, Minus, Plus, ShieldAlert, Users } from "lucide-react";
import { toast } from "sonner";
import { bookingApi, engagementApi, eventApi } from "@/services";
import { useAuth } from "@/features/auth/AuthProvider";
import type { EventDetail } from "@/types/api";
import { Poster } from "@/components/ui/Poster";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/States";
import { ApiError, idempotencyKey } from "@/lib/api-client";
import { date, duration, inr, time } from "@/lib/format";
import { displayEventTitle } from "@/lib/event-display";
import { cn } from "@/lib/cn";

export function EventDetailView({ id, initial }: { id: number; initial?: EventDetail }) {
  const router = useRouter();
  const qc = useQueryClient();
  const { status } = useAuth();
  const q = useQuery({ queryKey: ["event", id], queryFn: () => eventApi.detail(id), initialData: initial, refetchInterval: 30_000 });
  const favorites = useQuery({ queryKey: ["favorites"], queryFn: engagementApi.favorites, enabled: status === "authenticated" });
  const [sessionId, setSessionId] = useState<number>();
  const [qty, setQty] = useState<Record<number, number>>({});
  const d = q.data;

  useEffect(() => { if (d && !sessionId && d.sessions[0]) setSessionId(d.sessions[0].id); }, [d, sessionId]);
  useEffect(() => { setQty({}); }, [sessionId]);
  const session = d?.sessions.find((s) => s.id === sessionId);
  const total = useMemo(() => session?.categories.reduce((sum, c) => sum + (qty[c.id] ?? 0) * c.price, 0) ?? 0, [session, qty]);
  const count = Object.values(qty).reduce((a, b) => a + b, 0);
  const favorite = favorites.data?.find((f) => f.targetType === "EVENT" && f.targetId === id);

  const book = useMutation({
    mutationFn: () => bookingApi.createEvent(sessionId!, Object.entries(qty).filter(([, n]) => n > 0).map(([c, n]) => ({ categoryId: Number(c), quantity: n })), idempotencyKey("ev")),
    onSuccess: (b) => router.push(`/checkout/${b.id}`),
    onError: (e: ApiError) => { toast.error(e.message); qc.invalidateQueries({ queryKey: ["event", id] }); },
  });
  const fav = useMutation({
    mutationFn: async () => { if (favorite) await engagementApi.removeFavorite(favorite.id); else await engagementApi.addFavorite("EVENT", id); },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["favorites"] }),
  });

  if (q.isError) return <div className="container-page py-16"><ErrorState error={q.error} onRetry={() => q.refetch()} /></div>;
  if (!d) return <div className="container-page py-6"><Skeleton className="h-96 rounded-[2rem]" /></div>;
  const e = d.event;
  const title = displayEventTitle(e);
  const hasDisplayTitle = title !== e.title;

  return (
    <div className="pb-32">
      <section className="overflow-hidden border-b border-line bg-white">
        <div className="container-page grid gap-8 py-10 md:grid-cols-[240px_1fr] md:py-16">
          <div className="relative mx-auto aspect-[2/3] w-44 overflow-hidden rounded-3xl bg-brand-50 shadow-lift md:w-full"><Poster src={e.posterUrl} alt={`${title} poster`} priority sizes="240px" /></div>
          <div className="flex flex-col justify-end">
            <div className="flex flex-wrap gap-2"><Badge tone="brand">{e.category.name}</Badge>{e.ageRestriction && <Badge>{e.ageRestriction}</Badge>}{e.soldOut && <Badge tone="danger">Sold out</Badge>}</div>
            <h1 className="mt-3 text-3xl font-extrabold text-ink md:text-5xl">{hasDisplayTitle ? title : e.teamHome && e.teamAway ? <>{e.teamHome} <span className="text-muted">vs</span> {e.teamAway}</> : title}</h1>
            {hasDisplayTitle && e.teamHome && e.teamAway && <p className="mt-1 text-ink-soft">{e.teamHome} vs {e.teamAway}</p>}
            {!hasDisplayTitle && e.teamHome && <p className="mt-1 text-ink-soft">{e.title}</p>}
            {e.artist && <p className="mt-2 text-lg text-ink-soft">{e.artist}</p>}
            <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-sm text-ink-soft">
              <span className="flex items-center gap-1.5"><MapPin className="size-4 text-brand-600" />{d.venue.name}, {d.venue.city}</span>
              {d.durationMin && <span className="flex items-center gap-1.5"><Clock className="size-4 text-brand-600" />{duration(d.durationMin)}</span>}
              {e.languages && <span className="flex items-center gap-1.5"><Languages className="size-4 text-brand-600" />{e.languages}</span>}
            </div>
            <div className="mt-5">
              <Button variant="outline" onClick={() => (status === "authenticated" ? fav.mutate() : router.push(`/login?next=/events/${id}`))} icon={<Heart className={cn("size-5", favorite && "fill-brand-500 text-brand-500")} />}>
                {favorite ? "Saved" : "Save"}
              </Button>
            </div>
          </div>
        </div>
      </section>

      <div className="container-page grid gap-10 py-10 lg:grid-cols-[1fr_400px]">
        <div className="space-y-10">
          <section><h2 className="text-2xl font-bold">About</h2><p className="mt-3 whitespace-pre-line leading-relaxed text-ink-soft">{d.description}</p></section>
          <section>
            <h2 className="text-2xl font-bold">Venue</h2>
            <div className="mt-3 rounded-3xl border border-line p-5">
              <p className="font-semibold">{d.venue.name}</p>
              <p className="mt-1 text-sm text-muted">{d.venue.address}</p>
              <div className="mt-3 flex flex-wrap gap-2">
                {d.venue.capacity && <Badge><Users className="size-3" />{d.venue.capacity.toLocaleString("en-IN")} capacity</Badge>}
                {d.venue.facilities.map((f) => <Badge key={f}>{f.replace("_", " ").toLowerCase()}</Badge>)}
                {d.venue.allowPayAtVenue && <Badge tone="brand">Pay at venue available</Badge>}
              </div>
              {d.venue.latitude && <a className="mt-3 inline-block text-sm font-semibold text-brand-700 hover:underline" target="_blank" rel="noopener noreferrer" href={`https://www.openstreetmap.org/?mlat=${d.venue.latitude}&mlon=${d.venue.longitude}#map=16/${d.venue.latitude}/${d.venue.longitude}`}>Open in map</a>}
            </div>
          </section>
          <p className="flex items-start gap-2 rounded-2xl bg-brand-50 p-4 text-sm text-ink-soft"><ShieldAlert className="mt-0.5 size-4 shrink-0 text-brand-600" />Tickets are held for 10 minutes while you pay. Entry with the QR code from My Bookings; each code admits once.</p>
        </div>

        <aside id="tickets" className="lg:sticky lg:top-28 lg:self-start">
          <div className="rounded-3xl border border-line bg-white p-5 shadow-soft">
            <h2 className="text-lg font-bold">{e.vertical === "ACTIVITY" ? "Pick a time slot" : "Select date & tickets"}</h2>
            {!d.sessions.length ? <EmptyState className="mt-4" title="No upcoming dates" message="Check back soon." /> : (
              <>
                <div className="no-scrollbar -mx-1 mt-3 flex gap-2 overflow-x-auto p-1 pb-2" role="radiogroup" aria-label="Session">
                  {d.sessions.map((s) => (
                    <button key={s.id} role="radio" aria-checked={s.id === sessionId} onClick={() => setSessionId(s.id)}
                      className={cn("shrink-0 rounded-2xl border px-3.5 py-2 text-left transition", s.id === sessionId ? "border-transparent bg-brand-gradient text-white shadow-glow" : "border-line hover:border-brand-300")}>
                      <span className="flex items-center gap-1 text-xs font-semibold opacity-80"><CalendarDays className="size-3" />{date(s.startTime, { weekday: "short", day: "numeric", month: "short" })}</span>
                      <span className="block text-sm font-bold">{time(s.startTime)}</span>
                    </button>
                  ))}
                </div>
                <ul className="mt-5 space-y-3">
                  {session?.categories.map((c) => {
                    const n = qty[c.id] ?? 0;
                    const low = !c.soldOut && c.remaining <= Math.max(5, c.capacity * 0.1);
                    return (
                      <motion.li key={c.id} layout className={cn("flex items-center gap-3 rounded-2xl border p-3.5", n ? "border-brand-300 bg-brand-50/50" : "border-line", c.soldOut && "opacity-50")}>
                        <div className="min-w-0 flex-1">
                          <p className="font-semibold">{c.name}</p>
                          {c.description && <p className="truncate text-xs text-muted">{c.description}</p>}
                          <p className="mt-1 text-sm font-bold">{inr(c.price)}{low && <span className="ml-2 text-xs font-semibold text-rose-600">Only {c.remaining} left</span>}</p>
                        </div>
                        {c.soldOut ? <Badge tone="danger">Sold out</Badge> : (
                          <div className="flex items-center gap-1 rounded-xl border border-line p-1">
                            <button className="grid size-8 place-items-center rounded-lg hover:bg-mist disabled:opacity-30" disabled={!n} onClick={() => setQty((q) => ({ ...q, [c.id]: n - 1 }))} aria-label={`Fewer ${c.name}`}><Minus className="size-4" /></button>
                            <span className="w-6 text-center font-bold tabular" aria-live="polite">{n}</span>
                            <button className="grid size-8 place-items-center rounded-lg hover:bg-mist disabled:opacity-30" disabled={n >= Math.min(c.maxPerBooking, c.remaining)} onClick={() => setQty((q) => ({ ...q, [c.id]: n + 1 }))} aria-label={`More ${c.name}`}><Plus className="size-4" /></button>
                          </div>
                        )}
                      </motion.li>
                    );
                  })}
                </ul>
                <div className="mt-5 flex items-center justify-between border-t border-line pt-4">
                  <div><p className="text-xs text-muted">{count} ticket{count === 1 ? "" : "s"}</p><p className="font-display text-xl font-extrabold">{inr(total)}</p></div>
                  <Button size="lg" disabled={!count} loading={book.isPending}
                    onClick={() => (status === "authenticated" ? book.mutate() : router.push(`/login?next=/events/${id}`))}>Book now</Button>
                </div>
              </>
            )}
          </div>
        </aside>
      </div>
    </div>
  );
}
