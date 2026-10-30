import type { Booking } from "@/types/api";

/** Where to send the customer when their hold expires or a booking can no longer be paid. */
export function selectionUrl(b: Booking) {
  return b.type === "MOVIE" ? `/booking/${b.showId}/seats` : `/events/${b.eventId}`;
}

export function isOpen(b: Booking) {
  return b.status === "PENDING" || b.status === "PAYMENT_PENDING";
}
