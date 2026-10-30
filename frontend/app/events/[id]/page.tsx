import type { Metadata } from "next";
import { serverGet } from "@/lib/server-api";
import { displayEventTitle } from "@/lib/event-display";
import type { EventDetail } from "@/types/api";
import { EventDetailView } from "./EventDetailView";

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const d = await serverGet<EventDetail>(`/events/${id}`);
  if (!d) return { title: "Event" };
  const title = displayEventTitle(d.event);
  return {
    title,
    description: `${title} — ${d.event.category.name} at ${d.venue.name}, ${d.venue.city}. ${d.description.slice(0, 140)}`,
    openGraph: { title, images: [d.event.bannerUrl ?? d.event.posterUrl ?? "/media/og-default.svg"] },
  };
}

export default async function EventPage({ params }: Props) {
  const { id } = await params;
  const initial = await serverGet<EventDetail>(`/events/${id}`, 60);
  const title = initial ? displayEventTitle(initial.event) : undefined;
  const ld = initial && {
    "@context": "https://schema.org", "@type": initial.event.vertical === "SPORTS" ? "SportsEvent" : "Event",
    name: title, description: initial.description, image: initial.event.posterUrl,
    startDate: initial.sessions[0]?.startTime, endDate: initial.sessions[0]?.endTime,
    location: { "@type": "Place", name: initial.venue.name, address: initial.venue.address },
    offers: initial.sessions[0]?.categories.map((c) => ({ "@type": "Offer", name: c.name, price: c.price, priceCurrency: "INR", availability: c.soldOut ? "https://schema.org/SoldOut" : "https://schema.org/InStock" })),
  };
  return (
    <>
      {ld && <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(ld).replace(/</g, "\\u003c") }} />}
      <EventDetailView id={Number(id)} initial={initial ?? undefined} />
    </>
  );
}
