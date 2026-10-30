"use client";

import { use, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AnimatePresence, motion } from "framer-motion";
import { Minus, Plus, Info } from "lucide-react";
import { toast } from "sonner";
import { bookingApi, seatApi, showApi } from "@/services";
import { useRequireAuth } from "@/features/auth/useRequireAuth";
import { BookingHeader } from "@/components/booking/BookingHeader";
import { SeatLegend, SeatMap } from "@/components/booking/SeatMap";
import { LockTimer, useCountdown } from "@/components/ui/Countdown";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorState } from "@/components/ui/States";
import { ApiError, idempotencyKey } from "@/lib/api-client";
import { dateTime, formatLabel, inr } from "@/lib/format";
import type { Seat } from "@/types/api";

export default function SeatSelectionPage({ params }: { params: Promise<{ id: string }> }) {
  const showId = Number(use(params).id);
  const { ready } = useRequireAuth();
  const router = useRouter();
  const qc = useQueryClient();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [zoom, setZoom] = useState(1);
  const seeded = useRef(false);

  const show = useQuery({ queryKey: ["show", showId], queryFn: () => showApi.detail(showId), enabled: ready, staleTime: 0 });
  // Poll lightly for other customers' holds/purchases; never faster than every 15s, only while visible.
  const live = useQuery({ queryKey: ["show", showId, "seats"], queryFn: () => showApi.availability(showId), enabled: ready && !!show.data, refetchInterval: 15_000 });

  const seats: Seat[] = useMemo(() => {
    if (!show.data) return [];
    const status = new Map(live.data?.seats.map((s) => [s.label, s]) ?? []);
    return show.data.layout.seats.map((s) => {
      const l = status.get(s.label);
      return l ? { ...s, status: l.status, lockedByYou: l.lockedByYou } : s;
    });
  }, [show.data, live.data]);

  // Resume a hold the customer already has on this show.
  useEffect(() => {
    if (show.data?.yourLock && !seeded.current) {
      seeded.current = true;
      setSelected(new Set(show.data.yourLock.seatIds));
    }
  }, [show.data]);

  // If someone else grabbed a seat we had selected (and we don't hold it), drop it and say so.
  useEffect(() => {
    const lost = [...selected].filter((l) => { const s = seats.find((x) => x.label === l); return s && (s.status === "BOOKED" || (s.status === "LOCKED" && !s.lockedByYou)); });
    if (lost.length) {
      setSelected((prev) => new Set([...prev].filter((l) => !lost.includes(l))));
      toast.warning(`Seat${lost.length > 1 ? "s" : ""} ${lost.join(", ")} ${lost.length > 1 ? "were" : "was"} just booked by another customer.`);
    }
  }, [seats]); // eslint-disable-line react-hooks/exhaustive-deps

  // Fit the whole auditorium on small screens; customers can zoom in with the controls or pinch.
  const cols = show.data?.layout.columns;
  useEffect(() => {
    if (!cols) return;
    const needed = cols * 36.5 + 110;
    const available = Math.min(window.innerWidth, 1320) - 64;
    setZoom(Math.max(0.5, Math.min(1, +(available / needed).toFixed(2))));
  }, [cols]);

  const max = show.data?.maxSeatsPerBooking ?? 10;
  const toggle = useCallback((seat: Seat) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(seat.label)) next.delete(seat.label);
      else if (next.size >= max) { toast.info(`You can select up to ${max} seats`); return prev; }
      else next.add(seat.label);
      return next;
    });
  }, [max]);

  const chosen = seats.filter((s) => selected.has(s.label));
  const subtotal = chosen.reduce((sum, s) => sum + s.price, 0);
  const lock = show.data?.yourLock;
  const lockMatches = !!lock && lock.seatIds.length === selected.size && lock.seatIds.every((l) => selected.has(l));
  const secondsLeft = useCountdown(lock?.expiresAt, show.data?.serverTime, () => { qc.invalidateQueries({ queryKey: ["show", showId] }); });

  const proceed = useMutation({
    mutationFn: async () => {
      const labels = [...selected].sort();
      const l = lockMatches && lock ? { lockId: lock.lockId } : await seatApi.lock(showId, labels);
      return bookingApi.create(l.lockId, [], idempotencyKey("book-" + l.lockId));
    },
    onSuccess: (booking) => router.push(`/booking/${booking.id}/food`),
    onError: async (e: ApiError) => {
      if (e.code === "SEAT_LOCKED" || e.code === "SEAT_ALREADY_BOOKED") {
        toast.error("Those seats were just booked by another customer. Please pick different seats.");
        await qc.invalidateQueries({ queryKey: ["show", showId, "seats"] });
      } else if (e.code === "SHOW_NOT_BOOKABLE") {
        toast.error("This show is no longer open for booking.");
        router.back();
      } else toast.error(e.message);
    },
  });

  if (!ready || show.isLoading) {
    return <div className="container-page py-8"><Skeleton className="h-20 w-full" /><Skeleton className="mx-auto mt-8 h-[420px] w-full max-w-3xl rounded-3xl" /></div>;
  }
  if (show.isError || !show.data) return <div className="container-page py-16"><ErrorState error={show.error} onRetry={() => show.refetch()} /></div>;
  const s = show.data;

  return (
    <div className="min-h-screen bg-mist/50 pb-40">
      <BookingHeader back={`/movies/${s.movie.id}/tickets`} step="Seats" title={s.movie.title}
        subtitle={`${s.cinema.name} · ${s.screen.name} · ${dateTime(s.startTime)} · ${formatLabel(s.format)} · ${s.language}`}
        right={lockMatches ? <LockTimer seconds={secondsLeft} /> : undefined} />

      {!s.bookable ? (
        <div className="container-page py-16"><ErrorState title="Booking closed" error={new ApiError("SHOW_NOT_BOOKABLE", "This show has started or was cancelled.", 422)} /></div>
      ) : (
        <div className="container-page pt-6">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <p className="flex items-center gap-2 text-sm text-muted"><Info className="size-4 text-brand-600" />{s.availableSeats} of {s.totalSeats} seats available · select up to {max}</p>
            <div className="flex items-center gap-1 rounded-xl border border-line bg-white p-1" aria-label="Zoom seat map">
              <button className="grid size-8 place-items-center rounded-lg hover:bg-mist" onClick={() => setZoom((z) => Math.max(0.5, +(z - 0.1).toFixed(2)))} aria-label="Zoom out"><Minus className="size-4" /></button>
              <span className="w-10 text-center text-xs font-semibold tabular">{Math.round(zoom * 100)}%</span>
              <button className="grid size-8 place-items-center rounded-lg hover:bg-mist" onClick={() => setZoom((z) => Math.min(1.5, +(z + 0.1).toFixed(2)))} aria-label="Zoom in"><Plus className="size-4" /></button>
            </div>
          </div>
          <div className="overflow-x-auto rounded-[2rem] border border-line bg-white px-4 py-8 shadow-soft md:px-10">
            <SeatMap seats={seats} columns={s.layout.columns} pricing={s.pricing} selected={selected} onToggle={toggle} zoom={zoom} />
          </div>
          <div className="mt-6"><SeatLegend /></div>
        </div>
      )}

      <AnimatePresence>
        {selected.size > 0 && (
          <motion.div initial={{ y: 120 }} animate={{ y: 0 }} exit={{ y: 120 }} transition={{ type: "spring", damping: 26, stiffness: 300 }}
            className="glass fixed inset-x-0 bottom-0 z-40 border-t border-line pb-[env(safe-area-inset-bottom)]">
            <div className="container-page flex items-center justify-between gap-4 py-3.5">
              <div className="min-w-0">
                <p className="text-xs text-muted">{chosen.length} seat{chosen.length > 1 ? "s" : ""} · {chosen.map((c) => c.label).join(", ")}</p>
                <p className="font-display text-xl font-extrabold tabular">{inr(subtotal)}</p>
              </div>
              <Button size="lg" loading={proceed.isPending} onClick={() => proceed.mutate()} className="min-w-44">
                {lockMatches ? "Continue" : "Reserve seats"}
              </Button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
