import type { Metadata } from "next";
import { EventListing } from "@/components/events/EventListing";

export const metadata: Metadata = { title: "Events", description: "Concerts, comedy, festivals and live experiences on BookMyEvent." };

export default function Page() {
  return <EventListing vertical="events" title="Events" subtitle="Concerts, comedy, festivals and live experiences" />;
}
