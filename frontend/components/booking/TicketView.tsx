"use client";

import Image from "next/image";
import Link from "next/link";
import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { CalendarPlus, Download, MapPin, RotateCcw, Store, XCircle } from "lucide-react";
import { toast } from "sonner";
import type { Booking } from "@/types/api";
import { bookingApi } from "@/services";
import { Poster } from "@/components/ui/Poster";
import { StatusBadge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { Textarea } from "@/components/ui/Field";
import { PriceSummary } from "./PriceSummary";
import { ApiError } from "@/lib/api-client";
import { date, dateTime, inr, time } from "@/lib/format";
import { cn } from "@/lib/cn";

export function TicketView({ booking }: { booking: Booking }) {
  const b = booking;
  const qc = useQueryClient();
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [reason, setReason] = useState("");
  const cancel = useMutation({
    mutationFn: () => bookingApi.cancel(b.id, reason || undefined),
    onSuccess: (updated) => {
      qc.setQueryData(["booking", b.id], updated);
      qc.invalidateQueries({ queryKey: ["bookings"] });
      setConfirmCancel(false);
      toast.success(updated.refund ? "Booking cancelled. Your refund has been initiated." : "Booking cancelled.");
    },
    onError: (e: ApiError) => toast.error(e.message),
  });

  const confirmed = b.status === "CONFIRMED";
  const reserved = b.payAtVenue && b.status === "PAYMENT_PENDING";

  return (
    <div className="mx-auto max-w-3xl">
      <article className="ticket-print overflow-hidden rounded-[2rem] bg-white shadow-lift ring-1 ring-line">
        <div className="relative isolate overflow-hidden bg-brand-950 p-6 text-white">
          <div className="absolute inset-0 -z-10 opacity-40"><Poster src={b.posterUrl} alt="" sizes="700px" className="scale-110 blur-md" /></div>
          <div className="absolute inset-0 -z-10 bg-gradient-to-r from-brand-950 via-brand-950/90 to-brand-900/70" />
          <div className="flex gap-5">
            <div className="relative aspect-[2/3] w-24 shrink-0 overflow-hidden rounded-2xl ring-1 ring-white/20"><Poster src={b.posterUrl} alt="" sizes="96px" /></div>
            <div className="min-w-0">
              <StatusBadge status={reserved ? "PAYMENT_PENDING" : b.status} />
              <h2 className="mt-2 font-display text-2xl font-extrabold leading-tight">{b.title}</h2>
              <p className="mt-1 text-sm text-white/75">{[b.certificate, b.language, b.format].filter(Boolean).join(" · ")}</p>
              <p className="mt-2 flex items-center gap-1.5 text-sm text-white/85"><MapPin className="size-4" />{b.venueName}{b.screenName && ` · ${b.screenName}`}</p>
            </div>
          </div>
        </div>

        <div className="grid gap-6 p-6 md:grid-cols-[1fr_220px]">
          <dl className="grid grid-cols-2 gap-4 text-sm">
            <Info label="Date" value={date(b.startTime, { weekday: "short", day: "numeric", month: "short", year: "numeric" })} />
            <Info label="Show time" value={time(b.startTime)} />
            {b.seats.length > 0 && <Info label={`Seats (${b.seats.length})`} value={b.seats.map((s) => s.label).join(", ")} />}
            {b.tickets.length > 0 && <Info label="Tickets" value={b.tickets.map((t) => `${t.quantity} × ${t.name}`).join(", ")} />}
            {b.food.length > 0 && <Info label="Food" value={b.food.map((f) => `${f.quantity} × ${f.name}`).join(", ")} />}
            <Info label="Booking ID" value={b.reference} mono />
            <Info label={reserved ? "Pay at counter" : "Total paid"} value={inr(b.price.grandTotal, { decimals: true })} />
            {b.paymentMethod && <Info label="Payment" value={b.paymentMethod.replace("_", " ")} />}
          </dl>

          <div className="relative flex flex-col items-center justify-center border-t-2 border-dashed border-line pt-6 md:border-l-2 md:border-t-0 md:pl-6 md:pt-0">
            <span className="absolute -left-3 -top-3 hidden size-6 rounded-full bg-mist md:block" aria-hidden />
            {confirmed && b.ticket?.qrImage ? (
              <>
                <Image src={b.ticket.qrImage} alt={`Entry QR code for booking ${b.reference}`} width={180} height={180} unoptimized
                  className={cn("rounded-xl", b.ticket.status !== "VALID" && "opacity-30 grayscale")} />
                <p className="mt-2 font-mono text-xs font-bold tracking-wider">{b.ticket.ticketCode}</p>
                <p className="text-xs text-muted">{b.ticket.status === "USED" ? "Already scanned" : "Show this at the entrance"}</p>
              </>
            ) : reserved ? (
              <div className="text-center">
                <Store className="mx-auto size-10 text-brand-600" />
                <p className="mt-2 text-sm font-semibold">Pay at the box office</p>
                <p className="text-xs text-muted">Quote {b.reference} before {b.expiresAt ? dateTime(b.expiresAt) : "the show"}</p>
              </div>
            ) : (
              <div className="text-center text-sm text-muted"><XCircle className="mx-auto mb-2 size-10 text-line" />Ticket not active</div>
            )}
          </div>
        </div>

        {b.refund && (
          <div className="border-t border-line p-6">
            <h3 className="font-bold">Refund</h3>
            <RefundTimeline status={b.refund.status} />
            <p className="mt-3 text-sm text-muted">
              {inr(b.refund.amount, { decimals: true })} to your original payment method.
              {b.refund.status === "COMPLETED" ? ` Completed ${b.refund.processedAt ? date(b.refund.processedAt) : ""}. It can take 5–7 working days to appear on your statement.` : " We'll notify you as soon as the payment provider confirms it."}
            </p>
          </div>
        )}

        <details className="border-t border-line p-6">
          <summary className="cursor-pointer font-semibold">Price breakdown</summary>
          <div className="mt-4"><PriceSummary booking={b} /></div>
        </details>
      </article>

      <div className="no-print mt-5 flex flex-wrap justify-center gap-2">
        {confirmed && <Button variant="outline" icon={<Download className="size-4" />} onClick={() => window.print()}>Download ticket</Button>}
        {(confirmed || reserved) && <Button variant="outline" icon={<CalendarPlus className="size-4" />} onClick={() => downloadIcs(b)}>Add to calendar</Button>}
        {(b.cancellable || reserved) && (
          <Button variant="ghost" className="text-rose-600 hover:bg-rose-50" icon={<RotateCcw className="size-4" />} onClick={() => setConfirmCancel(true)}>
            {confirmed ? "Cancel & request refund" : "Cancel reservation"}
          </Button>
        )}
        {confirmed && !b.cancellable && b.cancellationDeadline && <p className="w-full text-center text-xs text-muted">Cancellation closed at {dateTime(b.cancellationDeadline)}.</p>}
        {b.type === "MOVIE" && b.movieId && confirmed && new Date(b.endTime) < new Date() && (
          <Link href={`/movies/${b.movieId}#reviews`} className="text-sm font-semibold text-brand-700 hover:underline">Rate this movie</Link>
        )}
      </div>

      <Modal open={confirmCancel} onClose={() => setConfirmCancel(false)} title="Cancel this booking?">
        <p className="text-sm text-ink-soft">
          {confirmed ? "Your seats will be released and a refund request will be created. Convenience fees are not refundable." : "Your reserved seats will be released."}
        </p>
        <Textarea className="mt-4" placeholder="Reason (optional)" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200} aria-label="Cancellation reason" />
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="ghost" onClick={() => setConfirmCancel(false)}>Keep booking</Button>
          <Button variant="danger" loading={cancel.isPending} onClick={() => cancel.mutate()}>Cancel booking</Button>
        </div>
      </Modal>
    </div>
  );
}

function Info({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <dt className="text-xs font-semibold uppercase tracking-wider text-muted">{label}</dt>
      <dd className={cn("mt-0.5 font-semibold", mono && "font-mono")}>{value}</dd>
    </div>
  );
}

const STEPS = ["REQUESTED", "PROCESSING", "COMPLETED"] as const;
function RefundTimeline({ status }: { status: string }) {
  const failed = status === "FAILED";
  const idx = failed ? 1 : STEPS.indexOf(status as (typeof STEPS)[number]);
  return (
    <ol className="mt-4 flex items-center gap-2" aria-label={`Refund status: ${status.toLowerCase()}`}>
      {STEPS.map((s, i) => (
        <li key={s} className="flex flex-1 items-center gap-2">
          <span className={cn("grid size-7 shrink-0 place-items-center rounded-full text-[11px] font-bold",
            i < idx || (i === idx && s === "COMPLETED") ? "bg-emerald-500 text-white" : i === idx ? (failed ? "bg-rose-500 text-white" : "bg-brand-gradient text-white") : "bg-mist text-muted")}>{i + 1}</span>
          <span className="text-xs font-semibold">{failed && i === 1 ? "Retrying" : s.charAt(0) + s.slice(1).toLowerCase()}</span>
          {i < STEPS.length - 1 && <span className={cn("h-0.5 flex-1 rounded", i < idx ? "bg-emerald-400" : "bg-line")} />}
        </li>
      ))}
    </ol>
  );
}

function downloadIcs(b: Booking) {
  const fmt = (iso: string) => new Date(iso).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
  const ics = [
    "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//BookMyEvent//Booking//EN", "BEGIN:VEVENT",
    `UID:${b.reference}@cineverse`, `DTSTAMP:${fmt(new Date().toISOString())}`, `DTSTART:${fmt(b.startTime)}`, `DTEND:${fmt(b.endTime)}`,
    `SUMMARY:${b.title} (${b.reference})`, `LOCATION:${b.venueName}, ${b.venueAddress}`.replace(/,/g, "\\,"),
    `DESCRIPTION:Seats: ${b.seats.map((s) => s.label).join(" ") || b.tickets.map((t) => `${t.quantity}x ${t.name}`).join(" ")}`,
    "END:VEVENT", "END:VCALENDAR",
  ].join("\r\n");
  const url = URL.createObjectURL(new Blob([ics], { type: "text/calendar" }));
  const a = document.createElement("a");
  a.href = url;
  a.download = `${b.reference}.ics`;
  a.click();
  URL.revokeObjectURL(url);
}
