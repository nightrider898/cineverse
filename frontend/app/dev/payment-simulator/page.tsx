"use client";

import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { FlaskConical, ShieldAlert } from "lucide-react";
import { paymentApi } from "@/services";
import { useRequireAuth } from "@/features/auth/useRequireAuth";
import { Button } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/States";
import { Skeleton } from "@/components/ui/Skeleton";
import { inr } from "@/lib/format";

/**
 * Development-only stand-in for a gateway's hosted page. The backend endpoint behind it exists only when
 * PAYMENTS_MOCK_ENABLED=true; the result is delivered as a signed webhook through the normal pipeline.
 */
function Simulator() {
  const params = useSearchParams();
  const paymentId = params.get("paymentId")!;
  const bookingId = params.get("bookingId");
  const { ready } = useRequireAuth();
  const router = useRouter();
  const [twice, setTwice] = useState(false);
  const details = useQuery({ queryKey: ["sim", paymentId], queryFn: () => paymentApi.simulatorDetails(paymentId), enabled: ready && !!paymentId, retry: false });
  const simulate = useMutation({
    mutationFn: (outcome: "SUCCESS" | "FAILED" | "CANCELLED") => paymentApi.simulate(paymentId, outcome, twice ? 2 : 1),
    onSuccess: (r) => router.replace(`/payment/${r.payment.bookingId ?? bookingId}?paymentId=${paymentId}`),
  });

  if (!ready || details.isLoading) return <div className="container-page py-16"><Skeleton className="mx-auto h-96 max-w-md rounded-3xl" /></div>;
  if (details.isError) return <div className="container-page py-16"><ErrorState error={details.error} title="Payment simulator unavailable" /></div>;
  const d = details.data!;
  return (
    <div className="container-page grid min-h-[80vh] place-items-center py-12">
      <div className="w-full max-w-md overflow-hidden rounded-[2rem] border border-line bg-white shadow-lift">
        <div className="bg-zinc-900 p-6 text-white">
          <p className="flex items-center gap-2 text-xs font-bold uppercase tracking-widest text-amber-300"><FlaskConical className="size-4" />Mock gateway · development only</p>
          <p className="mt-4 text-sm text-white/60">Paying BookMyEvent</p>
          <p className="font-display text-4xl font-extrabold">{inr(Number(d.amount), { decimals: true })}</p>
          <p className="mt-1 text-sm text-white/60">Booking {d.bookingReference} · {String(d.method)} · order {String(d.orderId)}</p>
        </div>
        <div className="space-y-3 p-6">
          <Button className="w-full" size="lg" loading={simulate.isPending && simulate.variables === "SUCCESS"} disabled={simulate.isPending} onClick={() => simulate.mutate("SUCCESS")}>Simulate successful payment</Button>
          <Button className="w-full" size="lg" variant="outline" disabled={simulate.isPending} onClick={() => simulate.mutate("FAILED")}>Simulate failure</Button>
          <Button className="w-full" size="lg" variant="ghost" disabled={simulate.isPending} onClick={() => simulate.mutate("CANCELLED")}>Cancel payment</Button>
          <label className="flex items-start gap-2 pt-2 text-xs text-muted">
            <input type="checkbox" checked={twice} onChange={(e) => setTwice(e.target.checked)} className="mt-0.5 accent-brand-600" />
            Deliver the webhook twice (demonstrates idempotent processing — only one booking/ticket is created)
          </label>
          <p className="flex items-start gap-2 rounded-xl bg-amber-50 p-3 text-xs text-amber-800"><ShieldAlert className="mt-0.5 size-4 shrink-0" />No real money moves. In production this page is replaced by Stripe, Razorpay or PhonePe checkout.</p>
        </div>
      </div>
    </div>
  );
}

export default function PaymentSimulatorPage() {
  return <Suspense><Simulator /></Suspense>;
}
