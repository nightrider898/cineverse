"use client";

import { Suspense, use, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import { paymentApi } from "@/services";
import { useRequireAuth } from "@/features/auth/useRequireAuth";
import { Button, ButtonLink } from "@/components/ui/Button";
import { inr } from "@/lib/format";

/**
 * Payment verification. We never treat a missing/failed browser callback as failure: the backend asks the
 * gateway for the real status, so customers aren't nudged into paying twice.
 */
function PaymentStatusInner({ bookingId }: { bookingId: number }) {
  const paymentId = useSearchParams().get("paymentId");
  const { ready } = useRequireAuth();
  const router = useRouter();
  const [startedAt] = useState(() => Date.now());
  const status = useQuery({
    queryKey: ["payment", paymentId],
    queryFn: () => paymentApi.status(paymentId!),
    enabled: ready && !!paymentId,
    refetchInterval: (q) => {
      const s = q.state.data?.status;
      if (s === "SUCCESS" || s === "FAILED" || s === "CANCELLED" || s === "REFUNDED") return false;
      return Date.now() - startedAt < 120_000 ? 2500 : 10_000;
    },
    retry: 5,
  });
  const s = status.data;

  useEffect(() => {
    if (s?.status === "SUCCESS" && s.bookingStatus === "CONFIRMED") {
      const t = setTimeout(() => router.replace(`/booking-confirmed/${bookingId}`), 900);
      return () => clearTimeout(t);
    }
  }, [s, bookingId, router]);

  const failed = s?.status === "FAILED" || s?.status === "CANCELLED";
  const refunded = s?.status === "SUCCESS" && s.bookingStatus === "REFUND_PENDING";
  const success = s?.status === "SUCCESS" && s.bookingStatus === "CONFIRMED";

  return (
    <div className="container-page grid min-h-[75vh] place-items-center py-16">
      <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} className="w-full max-w-md rounded-[2rem] border border-line bg-white p-8 text-center shadow-soft" role="status" aria-live="polite">
        {success ? (
          <>
            <motion.div initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: "spring", damping: 12 }}><CheckCircle2 className="mx-auto size-16 text-emerald-500" /></motion.div>
            <h1 className="mt-4 text-2xl font-extrabold">Payment successful</h1>
            <p className="mt-1 text-muted">Generating your ticket…</p>
          </>
        ) : failed ? (
          <>
            <XCircle className="mx-auto size-16 text-rose-500" />
            <h1 className="mt-4 text-2xl font-extrabold">Payment {s?.status === "CANCELLED" ? "cancelled" : "failed"}</h1>
            <p className="mt-1 text-muted">{s?.failureReason ?? "No money was taken for this attempt."} Your seats are still held for a few minutes.</p>
            <div className="mt-6 flex justify-center gap-2">
              <ButtonLink href={`/checkout/${bookingId}`}>Try again</ButtonLink>
              <ButtonLink href="/" variant="ghost">Home</ButtonLink>
            </div>
          </>
        ) : refunded ? (
          <>
            <XCircle className="mx-auto size-16 text-amber-500" />
            <h1 className="mt-4 text-2xl font-extrabold">Seats no longer available</h1>
            <p className="mt-1 text-muted">Your payment arrived after the seats were released. A full refund of {inr(s!.amount)} has been initiated.</p>
            <ButtonLink href={`/bookings/${bookingId}`} className="mt-6">Track refund</ButtonLink>
          </>
        ) : (
          <>
            <Loader2 className="mx-auto size-14 animate-spin text-brand-600" />
            <h1 className="mt-4 text-2xl font-extrabold">Payment status is being verified</h1>
            <p className="mt-1 text-muted">Please don't close this page or pay again. We're confirming with your payment provider.</p>
            {status.isError && <Button variant="secondary" className="mt-6" onClick={() => status.refetch()}>Check again</Button>}
            {!paymentId && <ButtonLink href={`/bookings/${bookingId}`} className="mt-6">View booking</ButtonLink>}
          </>
        )}
      </motion.div>
    </div>
  );
}

export default function PaymentStatusPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return <Suspense><PaymentStatusInner bookingId={Number(id)} /></Suspense>;
}
