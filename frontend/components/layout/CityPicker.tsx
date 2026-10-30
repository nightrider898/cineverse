"use client";

import { useMemo, useState } from "react";
import { Crosshair, MapPin, Search } from "lucide-react";
import { Modal } from "@/components/ui/Modal";
import { Input } from "@/components/ui/Field";
import { useCity } from "@/features/location/CityProvider";
import { cn } from "@/lib/cn";
import { toast } from "sonner";

const CITY_GLYPH: Record<string, string> = {
  hyderabad: "🕌", bengaluru: "🌳", chennai: "🏖️", mumbai: "🌉", delhi: "🏛️", pune: "⛰️", kolkata: "🚋", vijayawada: "🌊", visakhapatnam: "⚓",
};

export function CityPicker() {
  const { pickerOpen, closePicker, cities, setCity, citySlug, detect } = useCity();
  const [q, setQ] = useState("");
  const [detecting, setDetecting] = useState(false);
  const filtered = useMemo(() => cities.filter((c) => c.name.toLowerCase().includes(q.trim().toLowerCase())), [cities, q]);
  const popular = filtered.filter((c) => c.popular);
  const others = filtered.filter((c) => !c.popular);

  async function onDetect() {
    setDetecting(true);
    const c = await detect();
    setDetecting(false);
    if (c) { setCity(c); toast.success(`Showing ${c.name}`); }
    else toast.error("Couldn't detect your location. Please pick a city.");
  }

  const tile = (c: (typeof cities)[number]) => (
    <button key={c.id} onClick={() => setCity(c)} aria-pressed={c.slug === citySlug}
      className={cn("flex flex-col items-center gap-1.5 rounded-2xl border p-3 text-sm font-semibold transition hover:-translate-y-0.5 hover:border-brand-300 hover:shadow-soft",
        c.slug === citySlug ? "border-brand-400 bg-brand-50 text-brand-700" : "border-line bg-white")}>
      <span className="text-2xl" aria-hidden>{CITY_GLYPH[c.slug] ?? "📍"}</span>
      {c.name}
    </button>
  );

  return (
    <Modal open={pickerOpen} onClose={closePicker} title="Select your city" wide>
      <div className="flex flex-col gap-3 sm:flex-row">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted" aria-hidden />
          <Input aria-label="Search city" placeholder="Search for your city" value={q} onChange={(e) => setQ(e.target.value)} className="pl-10" />
        </div>
        <button onClick={onDetect} disabled={detecting} className="flex h-11 items-center justify-center gap-2 rounded-xl px-4 text-sm font-semibold text-brand-700 hover:bg-brand-50 disabled:opacity-60">
          <Crosshair className={cn("size-4", detecting && "animate-spin")} /> {detecting ? "Detecting…" : "Detect my location"}
        </button>
      </div>
      {popular.length > 0 && (
        <>
          <p className="mb-3 mt-6 text-xs font-bold uppercase tracking-wider text-muted">Popular cities</p>
          <div className="grid grid-cols-3 gap-2.5 sm:grid-cols-4 md:grid-cols-7">{popular.map(tile)}</div>
        </>
      )}
      {others.length > 0 && (
        <>
          <p className="mb-3 mt-6 text-xs font-bold uppercase tracking-wider text-muted">Other cities</p>
          <div className="flex flex-wrap gap-2">
            {others.map((c) => (
              <button key={c.id} onClick={() => setCity(c)} className="flex items-center gap-1.5 rounded-full border border-line px-3.5 py-1.5 text-sm hover:border-brand-300 hover:text-brand-700">
                <MapPin className="size-3.5" /> {c.name}
              </button>
            ))}
          </div>
        </>
      )}
      {filtered.length === 0 && <p className="py-8 text-center text-sm text-muted">No city matches “{q}”. BookMyEvent isn't there yet.</p>}
    </Modal>
  );
}
