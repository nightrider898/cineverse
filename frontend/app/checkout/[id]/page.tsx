"use client";

import { use, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Building2, CreditCard, Landmark, Smartphone, Store, TicketPercent, Wallet, X, ShieldCheck, CalendarDays, MapPin, Armchair, Popcorn } from "lucide-react";
import { toast } from "sonner";
import { bookingApi, catalogApi, paymentApi } from "@/services";
import { useAuth } from "@/features/auth/AuthProvider";
import { useRequireAuth } from "@/features/auth/useRequireAuth";
import { isOpen, selectionUrl } from "@/features/booking";
import { BookingHeader } from "@/components/booking/BookingHeader";
import { PriceSummary } from "@/components/booking/PriceSummary";
import { LockTimer, useCountdown } from "@/components/ui/Countdown";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Field";
import { Modal } from "@/components/ui/Modal";
import { Poster } from "@/components/ui/Poster";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorState } from "@/components/ui/States";
import { Badge } from "@/components/ui/Badge";
import { ApiError, idempotencyKey } from "@/lib/api-client";
import { mountStripe, openRazorpay } from "@/lib/gateways";
import { dateTime, inr } from "@/lib/format";
import { cn } from "@/lib/cn";
import type { PaymentInit } from "@/types/api";

const METHOD_ICON: Record<string, React.ReactNode> = {
  UPI: <Smartphone className="size-5" />, GPAY: <Wallet className="size-5" />, PHONEPE: <Smartphone className="size-5" />,
  CARD: <CreditCard className="size-5" />, NETBANKING: <Landmark className="size-5" />, WALLET: <Wallet className="size-5" />, PAY_AT_VENUE: <Store className="size-5" />,
};

export default function CheckoutPage({ params }: { params: Promise<{ id: string }> }) {
  const bookingId = Number(use(params).id);
  const { ready } = useRequireAuth();
  const { user } = useAuth();
  const router = useRouter();
  const qc = useQueryClient();
  const [coupon, setCoupon] = useState("");
  const [method, setMethod] = useState<string>();
  const [agreed, setAgreed] = useState(false);
  const [stripeInit, setStripeInit] = useState<PaymentInit | null>(null);
  // One Idempotency-Key per payment attempt: double clicks and retries of the same attempt can't charge twice.
  const attemptKey = useRef(idempotencyKey("pay"));

  const booking = useQuery({ queryKey: ["booking", bookingId], queryFn: () => bookingApi.get(bookingId), enabled: ready });
  const methods = useQuery({ queryKey: ["payment-methods", bookingId], queryFn: () => paymentApi.methods(bookingId), enabled: ready });
  const offers = useQuery({ queryKey: ["offers", booking.data?.city], queryFn: () => catalogApi.offers(booking.data?.city.toLowerCase()), enabled: !!booking.data });

  useEffect(() => {
    const b = booking.data;
    if (b && !isOpen(b)) router.replace(b.status === "CONFIRMED" ? `/booking-confirmed/${b.id}` : selectionUrl(b));
    if (b?.payAtVenue) router.replace(`/booking-confirmed/${b.id}`);
  }, [booking.data, router]);
  useEffect(() => {
    if (!method && methods.data) setMethod(methods.data.methods.find((m) => m.available)?.method);
  }, [methods.data, method]);

  const seconds = useCountdown(booking.data?.expiresAt, booking.data?.serverTime, () => {
    toast.error("Time's up — your seats were released. Please select again.");
    if (booking.data) router.replace(selectionUrl(booking.data));
  });

  const onBookingError = (e: ApiError) => {
    if (["BOOKING_EXPIRED", "SEAT_LOCK_EXPIRED"].includes(e.code)) {
      toast.error("Your hold expired. Those seats may have been booked by another customer.");
      if (booking.data) router.replace(selectionUrl(booking.data));
    } else toast.error(e.message);
  };

  const apply = useMutation({
    mutationFn: (code: string) => bookingApi.applyCoupon(bookingId, code),
    onSuccess: (b) => { qc.setQueryData(["booking", bookingId], b); setCoupon(""); toast.success(`Coupon applied — you save ${inr(b.price.discount, { decimals: true })}`); },
    onError: onBookingError,
  });
  const remove = useMutation({
    mutationFn: () => bookingApi.removeCoupon(bookingId),
    onSuccess: (b) => qc.setQueryData(["booking", bookingId], b),
    onError: onBookingError,
  });

  const pay = useMutation({
    mutationFn: () => paymentApi.initiate(bookingId, method!, attemptKey.current),
    onSuccess: async (init) => {
      attemptKey.current = idempotencyKey("pay"); // the next click is a new attempt
      const statusUrl = `/payment/${bookingId}?paymentId=${init.paymentId}`;
      switch (init.action.type) {
        case "PAY_AT_VENUE":
          toast.success("Reserved! Pay at the box office to collect your tickets.");
          router.push(`/booking-confirmed/${bookingId}`);
          break;
        case "MOCK_SIMULATOR":
          router.push(`/dev/payment-simulator?paymentId=${init.paymentId}&bookingId=${bookingId}`);
          break;
        case "REDIRECT":
          window.location.href = init.action.redirectUrl!;
          break;
        case "RAZORPAY_CHECKOUT":
          await openRazorpay({
            keyId: init.action.keyId!, orderId: init.action.orderId!, amountPaise: Math.round(init.amount * 100),
            name: "BookMyEvent", description: `Booking ${booking.data?.reference}`, method: init.method,
            prefill: { name: user?.fullName, email: user?.email, contact: user?.phone },
            onSuccess: async (r) => {
              try {
                await paymentApi.verifyRazorpay({ paymentId: init.paymentId, razorpayOrderId: r.razorpay_order_id, razorpayPaymentId: r.razorpay_payment_id, razorpaySignature: r.razorpay_signature });
              } finally { router.push(statusUrl); }
            },
            onDismiss: () => router.push(statusUrl),
          });
          break;
        case "STRIPE_CLIENT_SECRET":
          setStripeInit(init);
          break;
      }
    },
    onError: onBookingError,
  });

  if (!ready || booking.isLoading) return <div className="container-page py-8"><Skeleton className="h-20" /><div className="mt-6 grid gap-6 lg:grid-cols-[1fr_380px]"><Skeleton className="h-96 rounded-3xl" /><Skeleton className="h-96 rounded-3xl" /></div></div>;
  if (booking.isError || !booking.data) return <div className="container-page py-16"><ErrorState error={booking.error} onRetry={() => booking.refetch()} /></div>;
  const b = booking.data;
  const chosen = methods.data?.methods.find((m) => m.method === method);

  return (
    <div className="min-h-screen bg-mist/50 pb-32">
      <BookingHeader back={b.type === "MOVIE" ? `/booking/${b.id}/food` : `/events/${b.eventId}`} step="Payment" title="Review & pay" subtitle={`Booking ${b.reference}`} right={<LockTimer seconds={seconds} />} />
      <div className="container-page grid gap-6 pt-6 lg:grid-cols-[1fr_380px]">
        <div className="space-y-6">
          <section className="flex gap-4 rounded-3xl border border-line bg-white p-5" aria-label="Booking details">
            <div className="relative aspect-[2/3] w-24 shrink-0 overflow-hidden rounded-2xl"><Poster src={b.posterUrl} alt="" sizes="96px" /></div>
            <div className="min-w-0 space-y-1.5 text-sm">
              <h2 className="font-display text-lg font-bold leading-tight">{b.title}</h2>
              <p className="text-muted">{[b.certificate, b.language, b.format].filter(Boolean).join(" · ")}</p>
              <p className="flex items-center gap-1.5"><MapPin className="size-4 text-brand-600" />{b.venueName}{b.screenName && ` · ${b.screenName}`}</p>
              <p className="flex items-center gap-1.5"><CalendarDays className="size-4 text-brand-600" />{dateTime(b.startTime)}</p>
              {b.seats.length > 0 && <p className="flex items-center gap-1.5"><Armchair className="size-4 text-brand-600" />{b.seats.map((s) => s.label).join(", ")}</p>}
              {b.tickets.length > 0 && <p>{b.tickets.map((t) => `${t.quantity} × ${t.name}`).join(", ")}</p>}
              {b.food.length > 0 && <p className="flex items-center gap-1.5"><Popcorn className="size-4 text-brand-600" />{b.food.map((f) => `${f.quantity} × ${f.name}`).join(", ")}</p>}
            </div>
          </section>

          <section className="rounded-3xl border border-line bg-white p-5" aria-labelledby="coupon-h">
            <h2 id="coupon-h" className="flex items-center gap-2 font-bold"><TicketPercent className="size-5 text-brand-600" />Offers & coupons</h2>
            {b.couponCode ? (
              <div className="mt-4 flex items-center justify-between rounded-2xl bg-emerald-50 p-4 ring-1 ring-emerald-100">
                <div><p className="font-mono font-bold text-emerald-700">{b.couponCode}</p><p className="text-sm text-emerald-700">You save {inr(b.price.discount, { decimals: true })}</p></div>
                <Button variant="ghost" size="sm" onClick={() => remove.mutate()} loading={remove.isPending} icon={<X className="size-4" />}>Remove</Button>
              </div>
            ) : (
              <form className="mt-4 flex gap-2" onSubmit={(e) => { e.preventDefault(); if (coupon.trim()) apply.mutate(coupon.trim()); }}>
                <Input aria-label="Coupon code" placeholder="Enter coupon code" value={coupon} onChange={(e) => setCoupon(e.target.value.toUpperCase())} className="font-mono uppercase" />
                <Button type="submit" variant="secondary" loading={apply.isPending} disabled={!coupon.trim()}>Apply</Button>
              </form>
            )}
            {!b.couponCode && !!offers.data?.filter((o) => o.couponCode).length && (
              <div className="no-scrollbar mt-4 flex gap-2 overflow-x-auto">
                {offers.data.filter((o) => o.couponCode).map((o) => (
                  <button key={o.id} disabled={apply.isPending} onClick={() => apply.mutate(o.couponCode!)} className="shrink-0 rounded-2xl border border-dashed border-brand-300 bg-brand-50/60 px-3.5 py-2 text-left hover:bg-brand-50">
                    <span className="block font-mono text-sm font-bold text-brand-700">{o.couponCode}</span>
                    <span className="block max-w-52 truncate text-xs text-muted">{o.title}</span>
                  </button>
                ))}
              </div>
            )}
          </section>

          <section className="rounded-3xl border border-line bg-white p-5" aria-labelledby="pay-h">
            <div className="flex items-center justify-between">
              <h2 id="pay-h" className="font-bold">Payment method</h2>
              {methods.data?.sandbox && <Badge tone="warning">Sandbox mode</Badge>}
            </div>
            <div role="radiogroup" aria-label="Payment method" className="mt-4 grid gap-2.5 sm:grid-cols-2">
              {methods.isLoading ? Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-16 rounded-2xl" />) :
                methods.data?.methods.map((m) => (
                  <button key={m.method} role="radio" aria-checked={method === m.method} disabled={!m.available} onClick={() => setMethod(m.method)}
                    className={cn("flex items-center gap-3 rounded-2xl border p-3.5 text-left transition",
                      method === m.method ? "border-brand-500 bg-brand-50 shadow-[inset_0_0_0_1px_var(--color-brand-500)]" : "border-line hover:border-brand-300",
                      !m.available && "cursor-not-allowed opacity-45")}>
                    <span className={cn("grid size-10 shrink-0 place-items-center rounded-xl", method === m.method ? "bg-brand-gradient text-white" : "bg-mist text-ink-soft")}>{METHOD_ICON[m.method] ?? <Building2 className="size-5" />}</span>
                    <span className="min-w-0">
                      <span className="block text-sm font-semibold">{m.label}</span>
                      <span className="block truncate text-xs text-muted">{m.available ? m.description : m.method === "PAY_AT_VENUE" ? "Not offered for this show" : "Not configured"}</span>
                    </span>
                  </button>
                ))}
            </div>
            {chosen?.method === "PAY_AT_VENUE" && (
              <p className="mt-3 rounded-xl bg-amber-50 p-3 text-xs text-amber-800">Your seats are held until shortly before the show. Pay at the counter with your booking reference; unpaid reservations are released automatically.</p>
            )}
          </section>

          <label className="flex items-start gap-3 text-sm text-ink-soft">
            <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} className="mt-0.5 size-4 accent-brand-600" />
            <span>I agree to the booking terms: tickets are non-transferable, cancellations close before showtime, and convenience fees are non-refundable.</span>
          </label>
        </div>

        <aside>
          <div className="sticky top-6 space-y-4 rounded-3xl border border-line bg-white p-6">
            <h2 className="font-bold">Price details</h2>
            <PriceSummary booking={b} />
            <p className="flex items-center gap-1.5 text-xs text-muted"><ShieldCheck className="size-4 text-emerald-600" />Prices are calculated by BookMyEvent servers. Card details are handled by the payment gateway, never stored by us.</p>
          </div>
        </aside>
      </div>

      <div className="glass fixed inset-x-0 bottom-0 z-40 border-t border-line pb-[env(safe-area-inset-bottom)]">
        <div className="container-page flex items-center justify-between gap-3 py-3.5">
          <div><p className="text-xs text-muted">Total</p><p className="font-display text-xl font-extrabold tabular">{inr(b.price.grandTotal, { decimals: true })}</p></div>
          <Button size="lg" className="min-w-52" disabled={!method || !agreed || !chosen?.available} loading={pay.isPending} onClick={() => pay.mutate()}>
            {method === "PAY_AT_VENUE" ? "Reserve & pay at venue" : `Pay ${inr(b.price.grandTotal, { decimals: true })}`}
          </Button>
        </div>
      </div>

      <StripeSheet init={stripeInit} bookingId={bookingId} onClose={() => setStripeInit(null)} />
    </div>
  );
}

function StripeSheet({ init, bookingId, onClose }: { init: PaymentInit | null; bookingId: number; onClose: () => void }) {
  const host = useRef<HTMLDivElement>(null);
  const handle = useRef<Awaited<ReturnType<typeof mountStripe>> | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  useEffect(() => {
    if (!init || !host.current) return;
    let cancelled = false;
    mountStripe(init.action.publishableKey!, init.action.clientSecret!, host.current).then((h) => { if (cancelled) h.destroy(); else handle.current = h; })
      .catch(() => setError("Could not load the card form. Check your connection."));
    return () => { cancelled = true; handle.current?.destroy(); handle.current = null; };
  }, [init]);
  async function confirm() {
    if (!handle.current || !init) return;
    setBusy(true);
    const res = await handle.current.confirm(`${window.location.origin}/payment/${bookingId}?paymentId=${init.paymentId}`);
    setBusy(false);
    if (res.error) setError(res.error.message ?? "Payment failed");
  }
  return (
    <Modal open={!!init} onClose={onClose} title="Pay by card">
      <div ref={host} className="min-h-40" />
      {error && <p className="mt-3 text-sm text-danger">{error}</p>}
      <Button className="mt-5 w-full" size="lg" loading={busy} onClick={confirm}>Pay {init && inr(init.amount, { decimals: true })}</Button>
    </Modal>
  );
}
