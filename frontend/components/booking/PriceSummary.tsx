import type { Booking } from "@/types/api";
import { inr } from "@/lib/format";

export function PriceSummary({ booking, compact }: { booking: Booking; compact?: boolean }) {
  const p = booking.price;
  const ticketLabel = booking.type === "MOVIE" ? `Tickets (${booking.seats.length})` : `Tickets (${booking.tickets.reduce((n, t) => n + t.quantity, 0)})`;
  return (
    <dl className="space-y-2.5 text-sm">
      <Row label={ticketLabel} value={inr(p.ticketSubtotal)} />
      {!compact && booking.seats.length > 0 && (
        <p className="-mt-1 text-xs text-muted">{booking.seats.map((s) => `${s.label} (${s.seatType.toLowerCase()})`).join(", ")}</p>
      )}
      {p.foodSubtotal > 0 && <Row label="Food & beverages" value={inr(p.foodSubtotal)} />}
      <Row label="Convenience fee" value={inr(p.convenienceFee, { decimals: true })} />
      <Row label="GST on convenience fee" value={inr(p.tax, { decimals: true })} />
      {p.discount > 0 && <Row label={`Discount${booking.couponCode ? ` (${booking.couponCode})` : ""}`} value={`− ${inr(p.discount, { decimals: true })}`} className="text-emerald-600" />}
      <div className="border-t border-dashed border-line pt-3">
        <Row label="Amount payable" value={inr(p.grandTotal, { decimals: true })} strong />
      </div>
    </dl>
  );
}

function Row({ label, value, strong, className }: { label: string; value: string; strong?: boolean; className?: string }) {
  return (
    <div className={`flex items-center justify-between gap-4 ${className ?? ""}`}>
      <dt className={strong ? "font-bold" : "text-ink-soft"}>{label}</dt>
      <dd className={`tabular ${strong ? "font-display text-lg font-extrabold" : "font-semibold"}`}>{value}</dd>
    </div>
  );
}
