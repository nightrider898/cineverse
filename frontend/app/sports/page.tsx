import type { Metadata } from "next";
import { EventListing } from "@/components/events/EventListing";

export const metadata: Metadata = { title: "Sports", description: "Cricket, football and kabaddi — book your place with BookMyEvent." };

export default function Page() {
  return <EventListing vertical="sports" title="Sports" subtitle="Cricket, football and kabaddi — get into the stands" />;
}
