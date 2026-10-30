import { describe, expect, it } from "vitest";
import { displayEventTitle } from "@/lib/event-display";

describe("displayEventTitle", () => {
  it("uses the requested display titles for the first ten demo events", () => {
    const titles = [
      ["hyderabad-comedy-night", "Varanasi"],
      ["neon-music-festival", "OG (Ojas Gambheera)"],
      ["champions-arena-hawks-mariners", "Spirit"],
      ["deccan-derby-football", "Fauzi"],
      ["pro-raiders-kabaddi", "Saalar"],
      ["the-tin-crown", "Guntur Kaaram"],
      ["stories-from-the-charminar", "Dragon"],
      ["strike-night-bowling", "Ranabali"],
      ["the-vault-escape-room", "Jailer2"],
      ["indie-garden-sessions", "Raaka"],
    ] as const;

    for (const [slug, title] of titles) {
      expect(displayEventTitle({ slug, title: "Original demo title" })).toBe(title);
    }
  });

  it("leaves other event names unchanged", () => {
    expect(displayEventTitle({ slug: "harbour-cup-final", title: "Harbour Cup Final" })).toBe("Harbour Cup Final");
  });
});
