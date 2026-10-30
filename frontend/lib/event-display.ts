import type { EventCard } from "@/types/api";

const DEMO_EVENT_TITLES: Record<string, string> = {
  "hyderabad-comedy-night": "Varanasi",
  "neon-music-festival": "OG (Ojas Gambheera)",
  "champions-arena-hawks-mariners": "Spirit",
  "deccan-derby-football": "Fauzi",
  "pro-raiders-kabaddi": "Saalar",
  "the-tin-crown": "Guntur Kaaram",
  "stories-from-the-charminar": "Dragon",
  "strike-night-bowling": "Ranabali",
  "the-vault-escape-room": "Jailer2",
  "indie-garden-sessions": "Raaka",
};

const DEMO_EVENT_TITLES_BY_SOURCE: Record<string, string> = {
  "Hyderabad Comedy Night": "Varanasi",
  "Neon Music Festival": "OG (Ojas Gambheera)",
  "Champions Arena: Hyderabad Hawks vs Mumbai Mariners": "Spirit",
  "Deccan Derby: Hyderabad Hawks FC vs Bengaluru Blaze": "Fauzi",
  "Pro Raiders Kabaddi Night": "Saalar",
  "The Tin Crown": "Guntur Kaaram",
  "Stories from the Charminar": "Dragon",
  "Strike Night Bowling": "Ranabali",
  "The Vault Escape Room": "Jailer2",
  "Indie Garden Sessions": "Raaka",
};

export function displayEventTitle(event: Pick<EventCard, "title"> & Partial<Pick<EventCard, "slug">>): string {
  return (event.slug && DEMO_EVENT_TITLES[event.slug]) ?? DEMO_EVENT_TITLES_BY_SOURCE[event.title] ?? event.title;
}
