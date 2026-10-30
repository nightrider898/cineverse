"use client";

import { use, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Leaf, Minus, Plus } from "lucide-react";
import { toast } from "sonner";
import { bookingApi, catalogApi } from "@/services";
import { useRequireAuth } from "@/features/auth/useRequireAuth";
import { isOpen, selectionUrl } from "@/features/booking";
import { BookingHeader } from "@/components/booking/BookingHeader";
import { PriceSummary } from "@/components/booking/PriceSummary";
import { LockTimer, useCountdown } from "@/components/ui/Countdown";
import { Button } from "@/components/ui/Button";
import { Tabs } from "@/components/ui/Tabs";
import { Poster } from "@/components/ui/Poster";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorState } from "@/components/ui/States";
import { ApiError } from "@/lib/api-client";
import { dateTime, inr } from "@/lib/format";
import { cn } from "@/lib/cn";
import type { FoodItem } from "@/types/api";

const CATS = [{ value: "ALL", label: "All" }, { value: "COMBO", label: "Combos" }, { value: "POPCORN", label: "Popcorn" }, { value: "SNACK", label: "Snacks" }, { value: "BEVERAGE", label: "Beverages" }] as const;

export default function FoodPage({ params }: { params: Promise<{ id: string }> }) {
  const bookingId = Number(use(params).id);
  const { ready } = useRequireAuth();
  const router = useRouter();
  const [cat, setCat] = useState<(typeof CATS)[number]["value"]>("ALL");
  const [qty, setQty] = useState<Record<number, number>>({});
  const booking = useQuery({ queryKey: ["booking", bookingId], queryFn: () => bookingApi.get(bookingId), enabled: ready });
  const menu = useQuery({ queryKey: ["food", booking.data?.cinemaId], queryFn: () => catalogApi.food(booking.data?.cinemaId), enabled: !!booking.data?.cinemaId });

  useEffect(() => {
    if (booking.data) setQty(Object.fromEntries(booking.data.food.map((f) => [f.foodItemId, f.quantity])));
  }, [booking.data?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (booking.data && !isOpen(booking.data)) router.replace(booking.data.status === "CONFIRMED" ? `/booking-confirmed/${bookingId}` : selectionUrl(booking.data));
    if (booking.data?.type === "EVENT") router.replace(`/checkout/${bookingId}`);
  }, [booking.data, bookingId, router]);

  const seconds = useCountdown(booking.data?.expiresAt, booking.data?.serverTime, () => {
    toast.error("Your seat hold expired. Please select seats again.");
    if (booking.data) router.replace(selectionUrl(booking.data));
  });

  const items = useMemo(() => (menu.data ?? []).filter((f) => cat === "ALL" || f.category === cat), [menu.data, cat]);
  const foodTotal = (menu.data ?? []).reduce((sum, f) => sum + (qty[f.id] ?? 0) * f.price, 0);
  const lines = Object.entries(qty).filter(([, q]) => q > 0).map(([id, q]) => ({ foodItemId: Number(id), quantity: q }));

  const save = useMutation({
    mutationFn: () => bookingApi.updateFood(bookingId, lines),
    onSuccess: () => router.push(`/checkout/${bookingId}`),
    onError: (e: ApiError) => {
      if (e.code === "BOOKING_EXPIRED" || e.code === "SEAT_LOCK_EXPIRED") { toast.error("Your seat hold expired."); if (booking.data) router.replace(selectionUrl(booking.data)); }
      else toast.error(e.message);
    },
  });

  if (!ready || booking.isLoading) return <div className="container-page py-8"><Skeleton className="h-20" /><div className="mt-6 grid gap-4 md:grid-cols-3">{Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-48 rounded-3xl" />)}</div></div>;
  if (booking.isError || !booking.data) return <div className="container-page py-16"><ErrorState error={booking.error} onRetry={() => booking.refetch()} /></div>;
  const b = booking.data;
  const set = (id: number, delta: number) => setQty((q) => ({ ...q, [id]: Math.max(0, Math.min(20, (q[id] ?? 0) + delta)) }));

  return (
    <div className="min-h-screen bg-mist/50 pb-36">
      <BookingHeader back={selectionUrl(b)} step="Food" title="Grab a bite" subtitle={`${b.title} · ${b.venueName} · ${dateTime(b.startTime)}`} right={<LockTimer seconds={seconds} />} />
      <div className="container-page grid gap-8 pt-6 lg:grid-cols-[1fr_360px]">
        <section aria-label="Food menu">
          <Tabs id="food-cats" value={cat} onChange={setCat} tabs={CATS.map((c) => ({ value: c.value, label: c.label }))} className="w-fit" />
          {menu.isError ? <ErrorState className="mt-6" error={menu.error} onRetry={() => menu.refetch()} /> : (
            <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {(menu.isLoading ? Array.from<FoodItem | undefined>({ length: 6 }) : items).map((f, i) => f ? (
                <motion.article key={f.id} layout initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.03 }}
                  className={cn("flex gap-4 rounded-3xl border bg-white p-4 transition", (qty[f.id] ?? 0) > 0 ? "border-brand-300 shadow-soft" : "border-line")}>
                  <div className="relative size-24 shrink-0 overflow-hidden rounded-2xl"><Poster src={f.imageUrl} alt="" sizes="96px" /></div>
                  <div className="flex min-w-0 flex-1 flex-col">
                    <div className="flex items-start gap-1.5">
                      <span className={cn("mt-1 grid size-3.5 shrink-0 place-items-center rounded-sm border", f.veg ? "border-emerald-600" : "border-rose-600")} aria-label={f.veg ? "Vegetarian" : "Non-vegetarian"}>
                        <span className={cn("size-1.5 rounded-full", f.veg ? "bg-emerald-600" : "bg-rose-600")} />
                      </span>
                      <h3 className="font-semibold leading-snug">{f.name}</h3>
                    </div>
                    <p className="mt-0.5 line-clamp-2 text-xs text-muted">{f.description}</p>
                    <div className="mt-auto flex items-center justify-between pt-2">
                      <span className="font-bold">{inr(f.price)}</span>
                      {(qty[f.id] ?? 0) === 0 ? (
                        <Button size="sm" variant="secondary" onClick={() => set(f.id, 1)} aria-label={`Add ${f.name}`}>Add</Button>
                      ) : (
                        <div className="flex items-center gap-1 rounded-xl bg-brand-gradient p-1 text-white">
                          <button className="grid size-7 place-items-center rounded-lg hover:bg-white/20" onClick={() => set(f.id, -1)} aria-label={`Remove one ${f.name}`}><Minus className="size-4" /></button>
                          <span className="w-6 text-center text-sm font-bold tabular" aria-live="polite">{qty[f.id]}</span>
                          <button className="grid size-7 place-items-center rounded-lg hover:bg-white/20" onClick={() => set(f.id, 1)} aria-label={`Add one ${f.name}`}><Plus className="size-4" /></button>
                        </div>
                      )}
                    </div>
                  </div>
                </motion.article>
              ) : <Skeleton key={i} className="h-32 rounded-3xl" />)}
            </div>
          )}
          <p className="mt-4 flex items-center gap-1.5 text-xs text-muted"><Leaf className="size-3.5 text-emerald-600" />Food is prepared at the cinema and collected at the counter with your booking QR.</p>
        </section>
        <aside className="hidden lg:block">
          <div className="sticky top-6 rounded-3xl border border-line bg-white p-6">
            <h2 className="font-bold">Booking summary</h2>
            <div className="mt-4"><PriceSummary booking={{ ...b, price: { ...b.price, foodSubtotal: foodTotal } }} compact /></div>
            <p className="mt-3 text-xs text-muted">Final amount, fees and taxes are calculated securely on the next step.</p>
          </div>
        </aside>
      </div>
      <div className="glass fixed inset-x-0 bottom-0 z-40 border-t border-line pb-[env(safe-area-inset-bottom)]">
        <div className="container-page flex items-center justify-between gap-3 py-3.5">
          <div>
            <p className="text-xs text-muted">Food subtotal</p>
            <p className="font-display text-xl font-extrabold tabular">{inr(foodTotal)}</p>
          </div>
          <div className="flex gap-2">
            {lines.length === 0 && <Button variant="ghost" onClick={() => save.mutate()} disabled={save.isPending}>Skip</Button>}
            <Button size="lg" loading={save.isPending} onClick={() => save.mutate()}>Continue to checkout</Button>
          </div>
        </div>
      </div>
    </div>
  );
}
