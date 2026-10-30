import type { Metadata } from "next";
import { EventListing } from "@/components/events/EventListing";

export const metadata: Metadata = { title: "Activities", description: "Bowling, escape rooms, theme parks and adventures — book a time slot on BookMyEvent." };

export default function Page() {
  return <EventListing vertical="activities" title="Activities" subtitle="Bowling, escape rooms, theme parks and adventures — pick a time slot" />;
}
