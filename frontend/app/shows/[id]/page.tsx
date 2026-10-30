import { redirect } from "next/navigation";

/** /shows/:id is a stable share link for a showtime; the booking funnel lives under /booking. */
export default async function ShowRedirect({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  redirect(`/booking/${id}/seats`);
}
