"use client";

import { useEffect, useRef, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { AnimatePresence, motion } from "framer-motion";
import { Camera, CameraOff, CheckCircle2, Search, ShieldX, Store, XCircle } from "lucide-react";
import { toast } from "sonner";
import { staffApi } from "@/services";
import { useRequireAuth } from "@/features/auth/useRequireAuth";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Field";
import { Tabs } from "@/components/ui/Tabs";
import { StatusBadge } from "@/components/ui/Badge";
import { Skeleton } from "@/components/ui/Skeleton";
import { ApiError } from "@/lib/api-client";
import { dateTime, inr } from "@/lib/format";
import type { Booking, TicketValidation } from "@/types/api";
import { cn } from "@/lib/cn";

type BarcodeDetectorLike = { detect: (src: CanvasImageSource) => Promise<{ rawValue: string }[]> };

export default function StaffDeskPage() {
  const { ready } = useRequireAuth(["VENUE_STAFF", "ADMIN"]);
  const [tab, setTab] = useState<"scan" | "counter">("scan");
  if (!ready) return <div className="container-page py-10"><Skeleton className="h-96 rounded-3xl" /></div>;
  return (
    <div className="container-page max-w-3xl py-8 pb-24">
      <h1 className="text-3xl font-extrabold">Venue staff desk</h1>
      <p className="mt-1 text-muted">Validate QR tickets at the gate and settle pay-at-venue reservations.</p>
      <Tabs id="staff" className="mt-6 w-fit" value={tab} onChange={setTab} tabs={[{ value: "scan", label: "Scan tickets" }, { value: "counter", label: "Box office" }]} />
      <div className="mt-6">{tab === "scan" ? <Scanner /> : <Counter />}</div>
    </div>
  );
}

function Scanner() {
  const video = useRef<HTMLVideoElement>(null);
  const [camera, setCamera] = useState(false);
  const [manual, setManual] = useState("");
  const [result, setResult] = useState<TicketValidation | null>(null);
  const busy = useRef(false);
  const validate = useMutation({
    mutationFn: staffApi.validate,
    onSuccess: (r) => { setResult(r); if (navigator.vibrate) navigator.vibrate(r.result === "VALID" ? 80 : [60, 60, 60]); },
    onError: (e: ApiError) => toast.error(e.message),
    onSettled: () => { setTimeout(() => { busy.current = false; }, 1500); },
  });

  useEffect(() => {
    if (!camera) return;
    const Detector = (window as unknown as { BarcodeDetector?: new (o: { formats: string[] }) => BarcodeDetectorLike }).BarcodeDetector;
    if (!Detector) { toast.info("This browser can't scan QR codes; paste the code instead."); setCamera(false); return; }
    const detector = new Detector({ formats: ["qr_code"] });
    let stream: MediaStream | null = null;
    let raf = 0;
    navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } }).then((s) => {
      stream = s;
      if (video.current) { video.current.srcObject = s; video.current.play(); }
      const loop = async () => {
        if (video.current && video.current.readyState >= 2 && !busy.current) {
          const codes = await detector.detect(video.current).catch(() => []);
          if (codes[0]?.rawValue) { busy.current = true; validate.mutate(codes[0].rawValue); }
        }
        raf = requestAnimationFrame(loop);
      };
      loop();
    }).catch(() => { toast.error("Camera permission denied"); setCamera(false); });
    return () => { cancelAnimationFrame(raf); stream?.getTracks().forEach((t) => t.stop()); };
  }, [camera]); // eslint-disable-line react-hooks/exhaustive-deps

  const good = result?.result === "VALID";
  return (
    <div className="space-y-4">
      <div className="relative aspect-video overflow-hidden rounded-3xl bg-ink">
        {camera ? <video ref={video} className="size-full object-cover" muted playsInline /> : (
          <div className="grid size-full place-items-center text-white/60"><Camera className="size-10" /></div>
        )}
        <div className="pointer-events-none absolute inset-10 rounded-3xl border-2 border-white/40" aria-hidden />
      </div>
      <div className="flex flex-wrap gap-2">
        <Button variant={camera ? "outline" : "primary"} onClick={() => setCamera((c) => !c)} icon={camera ? <CameraOff className="size-4" /> : <Camera className="size-4" />}>{camera ? "Stop camera" : "Start camera"}</Button>
        <form className="flex flex-1 gap-2" onSubmit={(e) => { e.preventDefault(); if (manual.trim()) validate.mutate(manual.trim()); }}>
          <Input aria-label="QR token" placeholder="…or paste the QR token (CV1.…)" value={manual} onChange={(e) => setManual(e.target.value)} className="font-mono" />
          <Button type="submit" variant="secondary" loading={validate.isPending}>Validate</Button>
        </form>
      </div>
      <AnimatePresence mode="wait">
        {result && (
          <motion.div key={result.bookingReference + result.result} initial={{ opacity: 0, scale: 0.97 }} animate={{ opacity: 1, scale: 1 }} role="status" aria-live="assertive"
            className={cn("rounded-3xl p-6", good ? "bg-emerald-50 ring-2 ring-emerald-300" : "bg-rose-50 ring-2 ring-rose-300")}>
            <div className="flex items-center gap-3">
              {good ? <CheckCircle2 className="size-10 text-emerald-600" /> : result.result === "INVALID" ? <ShieldX className="size-10 text-rose-600" /> : <XCircle className="size-10 text-rose-600" />}
              <div><p className="text-2xl font-extrabold">{good ? result.message : result.result.replace("_", " ")}</p><p className="text-sm text-ink-soft">{good ? "Ticket marked as used" : result.message}</p></div>
            </div>
            {result.bookingReference && (
              <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
                <div><dt className="text-muted">Booking</dt><dd className="font-mono font-semibold">{result.bookingReference}</dd></div>
                <div><dt className="text-muted">Show</dt><dd className="font-semibold">{result.title}</dd></div>
                <div><dt className="text-muted">Venue</dt><dd>{result.venue}{result.screen && ` · ${result.screen}`}</dd></div>
                <div><dt className="text-muted">Start</dt><dd>{result.startTime && dateTime(result.startTime)}</dd></div>
                {result.seats.length > 0 && <div className="col-span-2"><dt className="text-muted">Seats</dt><dd className="font-bold">{result.seats.join(", ")}</dd></div>}
              </dl>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function Counter() {
  const [ref, setRef] = useState("");
  const [booking, setBooking] = useState<Booking | null>(null);
  const lookup = useMutation({ mutationFn: staffApi.lookup, onSuccess: setBooking, onError: (e: ApiError) => { setBooking(null); toast.error(e.message); } });
  const settle = useMutation({
    mutationFn: (action: "PAID" | "CANCELLED") => staffApi.counter(booking!.reference, action),
    onSuccess: (s) => { toast.success(s.bookingStatus === "CONFIRMED" ? "Payment received — tickets issued" : "Reservation cancelled"); lookup.mutate(booking!.reference); },
    onError: (e: ApiError) => toast.error(e.message),
  });
  return (
    <div className="space-y-4">
      <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); if (ref.trim()) lookup.mutate(ref.trim()); }}>
        <Input aria-label="Booking reference" placeholder="Booking ID, e.g. CV-2026-000123" value={ref} onChange={(e) => setRef(e.target.value.toUpperCase())} className="font-mono" />
        <Button type="submit" loading={lookup.isPending} icon={<Search className="size-4" />}>Find</Button>
      </form>
      {booking && (
        <div className="rounded-3xl border border-line p-6">
          <div className="flex items-start justify-between gap-3">
            <div><p className="font-mono text-sm text-muted">{booking.reference}</p><h2 className="text-xl font-bold">{booking.title}</h2><p className="text-sm text-muted">{booking.venueName} · {dateTime(booking.startTime)}</p></div>
            <StatusBadge status={booking.status} />
          </div>
          <p className="mt-3 text-sm">{booking.seats.map((s) => s.label).join(", ") || booking.tickets.map((t) => `${t.quantity} × ${t.name}`).join(", ")}</p>
          <p className="mt-1 font-display text-2xl font-extrabold">{inr(booking.price.grandTotal, { decimals: true })}</p>
          {booking.payAtVenue && booking.status === "PAYMENT_PENDING" ? (
            <div className="mt-5 flex gap-2">
              <Button loading={settle.isPending && settle.variables === "PAID"} disabled={settle.isPending} onClick={() => settle.mutate("PAID")} icon={<Store className="size-4" />}>Mark paid & issue tickets</Button>
              <Button variant="outline" disabled={settle.isPending} onClick={() => settle.mutate("CANCELLED")}>Cancel reservation</Button>
            </div>
          ) : <p className="mt-4 text-sm text-muted">No counter payment pending for this booking.</p>}
        </div>
      )}
    </div>
  );
}
