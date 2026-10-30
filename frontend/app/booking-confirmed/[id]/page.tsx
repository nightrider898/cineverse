"use client";

import { use, useMemo } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { motion, useReducedMotion } from "framer-motion";
import { PartyPopper } from "lucide-react";
import { bookingApi } from "@/services";
import { useRequireAuth } from "@/features/auth/useRequireAuth";
import { TicketView } from "@/components/booking/TicketView";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorState } from "@/components/ui/States";

function Confetti() {
  const reduce = useReducedMotion();
  const pieces = useMemo(() => Array.from({ length: 36 }, (_, i) => ({
    id: i, x: Math.random() * 100, delay: Math.random() * 0.4, rot: Math.random() * 360,
    color: ["#f97316", "#fdba74", "#f59e0b", "#fb923c", "#22c55e"][i % 5], w: 6 + Math.random() * 6,
  })), []);
  if (reduce) return null;
  return (
    <div className="pointer-events-none fixed inset-0 z-50 overflow-hidden" aria-hidden>
      {pieces.map((p) => (
        <motion.span key={p.id} className="absolute top-0 block rounded-sm" style={{ left: `${p.x}%`, width: p.w, height: p.w * 0.45, background: p.color }}
          initial={{ y: -40, rotate: p.rot, opacity: 1 }} animate={{ y: "105vh", rotate: p.rot + 540, opacity: [1, 1, 0] }}
          transition={{ duration: 2.4 + Math.random(), delay: p.delay, ease: "easeIn" }} />
      ))}
    </div>
  );
}

export default function BookingConfirmedPage({ params }: { params: Promise<{ id: string }> }) {
  const id = Number(use(params).id);
  const { ready } = useRequireAuth();
  const q = useQuery({ queryKey: ["booking", id], queryFn: () => bookingApi.get(id), enabled: ready });
  if (!ready || q.isLoading) return <div className="container-page py-10"><Skeleton className="mx-auto h-[520px] max-w-3xl rounded-[2rem]" /></div>;
  if (q.isError || !q.data) return <div className="container-page py-16"><ErrorState error={q.error} onRetry={() => q.refetch()} /></div>;
  const b = q.data;
  const confirmed = b.status === "CONFIRMED";
  return (
    <div className="bg-gradient-to-b from-brand-50 via-white to-white pb-16">
      {confirmed && <Confetti />}
      <div className="container-page pt-10 text-center">
        <motion.div initial={{ scale: 0.6, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} transition={{ type: "spring", damping: 14 }}
          className="mx-auto grid size-16 place-items-center rounded-2xl bg-brand-gradient text-white shadow-glow">
          <PartyPopper className="size-8" />
        </motion.div>
        <h1 className="mt-4 text-3xl font-extrabold md:text-4xl">{confirmed ? "You're all set!" : b.payAtVenue ? "Seats reserved" : "Booking status"}</h1>
        <p className="mt-2 text-muted">
          {confirmed ? <>Booking <b className="font-mono text-ink">{b.reference}</b> is confirmed. We've sent the details to your email.</>
            : b.payAtVenue ? "Pay at the box office before the hold ends to confirm your tickets." : `This booking is ${b.status.toLowerCase().replace("_", " ")}.`}
        </p>
      </div>
      <div className="container-page mt-8"><TicketView booking={b} /></div>
      <p className="no-print mt-8 text-center text-sm"><Link href="/bookings" className="font-semibold text-brand-700 hover:underline">Go to My Bookings</Link></p>
    </div>
  );
}
