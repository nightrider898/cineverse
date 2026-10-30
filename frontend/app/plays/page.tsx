import type { Metadata } from "next";
import { EventListing } from "@/components/events/EventListing";

export const metadata: Metadata = { title: "Plays", description: "Theatre and live performances on BookMyEvent." };

export default function Page() {
  return <EventListing vertical="plays" title="Plays" subtitle="Theatre and live performances" />;
}
