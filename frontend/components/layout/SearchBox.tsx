"use client";

import { useEffect, useId, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { AnimatePresence, motion } from "framer-motion";
import { Clapperboard, Clock, Film, MapPin, Search, Sparkles, TrendingUp, User as UserIcon, X } from "lucide-react";
import { searchApi } from "@/services";
import { useCity } from "@/features/location/CityProvider";
import { displayEventTitle } from "@/lib/event-display";
import { useDebounced } from "@/hooks/useDebounced";
import { cn } from "@/lib/cn";

type Item = { key: string; label: string; sub?: string; href: string; icon: React.ReactNode; group: string };

/** Debounced, grouped autocomplete backed by GET /api/v1/search, with recent and trending searches. */
export function SearchBox({ compact, className, autoFocus }: { compact?: boolean; className?: string; autoFocus?: boolean }) {
  const router = useRouter();
  const { citySlug } = useCity();
  const [q, setQ] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const debounced = useDebounced(q.trim(), 250);
  const listId = useId();
  const wrap = useRef<HTMLDivElement>(null);

  const { data: results, isFetching } = useQuery({
    queryKey: ["search", debounced, citySlug],
    queryFn: () => searchApi.search(debounced, citySlug),
    enabled: debounced.length >= 2,
    staleTime: 30_000,
  });
  const { data: suggestions } = useQuery({ queryKey: ["search-suggestions"], queryFn: searchApi.suggestions, enabled: open, staleTime: 60_000 });

  const items: Item[] = useMemo(() => {
    if (debounced.length < 2 || !results) return [];
    return [
      ...results.movies.slice(0, 4).map((m) => ({ key: "m" + m.id, label: m.title, sub: `${m.languages.slice(0, 2).join(", ")} · ${m.certificate}`, href: `/movies/${m.id}`, icon: <Film className="size-4" />, group: "Movies" })),
      ...results.events.slice(0, 3).map((e) => ({ key: "e" + e.id, label: displayEventTitle(e), sub: `${e.category.name} · ${e.venueName}`, href: `/events/${e.id}`, icon: <Sparkles className="size-4" />, group: "Events" })),
      ...results.cinemas.slice(0, 3).map((c) => ({ key: "c" + c.id, label: c.name, sub: c.locality ?? c.city, href: `/cinemas/${c.id}`, icon: <Clapperboard className="size-4" />, group: "Cinemas" })),
      ...results.people.slice(0, 3).map((p) => ({ key: "p" + p.id, label: p.name, sub: "Cast", href: `/search?q=${encodeURIComponent(p.name)}`, icon: <UserIcon className="size-4" />, group: "People" })),
      ...results.venues.slice(0, 2).map((v) => ({ key: "v" + v.id, label: v.name, sub: v.city, href: `/search?q=${encodeURIComponent(v.name)}`, icon: <MapPin className="size-4" />, group: "Venues" })),
    ];
  }, [results, debounced]);

  useEffect(() => { setActive(-1); }, [debounced]);
  useEffect(() => {
    const close = (e: MouseEvent) => { if (!wrap.current?.contains(e.target as Node)) setOpen(false); };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  function submit(term = q) {
    const t = term.trim();
    if (!t) return;
    setOpen(false);
    router.push(`/search?q=${encodeURIComponent(t)}`);
  }

  function onKey(e: React.KeyboardEvent) {
    if (e.key === "ArrowDown") { e.preventDefault(); setOpen(true); setActive((a) => Math.min(items.length - 1, a + 1)); }
    else if (e.key === "ArrowUp") { e.preventDefault(); setActive((a) => Math.max(-1, a - 1)); }
    else if (e.key === "Enter") { e.preventDefault(); if (active >= 0 && items[active]) { setOpen(false); router.push(items[active].href); } else submit(); }
    else if (e.key === "Escape") setOpen(false);
  }

  let lastGroup = "";
  return (
    <div ref={wrap} className={cn("relative", className)}>
      <div className={cn("flex items-center gap-2 rounded-2xl border border-line bg-mist/70 px-3.5 transition focus-within:border-brand-300 focus-within:bg-white focus-within:ring-4 focus-within:ring-brand-100", compact ? "h-10" : "h-12")}>
        <Search className="size-4 shrink-0 text-muted" aria-hidden />
        <input
          role="combobox" aria-expanded={open} aria-controls={listId} aria-autocomplete="list" aria-label="Search movies, cinemas, events"
          aria-activedescendant={active >= 0 ? `${listId}-${active}` : undefined}
          autoFocus={autoFocus}
          value={q} onChange={(e) => { setQ(e.target.value); setOpen(true); }} onFocus={() => setOpen(true)} onKeyDown={onKey}
          placeholder="Search movies, cinemas, events…" className="h-full w-full bg-transparent text-sm outline-none placeholder:text-muted/80"
        />
        {q && <button aria-label="Clear search" onClick={() => setQ("")} className="text-muted hover:text-ink"><X className="size-4" /></button>}
      </div>
      <AnimatePresence>
        {open && (
          <motion.div id={listId} role="listbox" initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -6 }} transition={{ duration: 0.15 }}
            className="absolute left-0 right-0 top-full z-50 mt-2 max-h-[70vh] overflow-y-auto rounded-2xl border border-line bg-white p-2 shadow-lift">
            {debounced.length < 2 ? (
              <div className="p-2">
                {!!suggestions?.recent.length && (
                  <>
                    <p className="px-2 pb-1.5 text-[11px] font-bold uppercase tracking-wider text-muted">Recent</p>
                    <div className="mb-3 flex flex-wrap gap-1.5 px-1">
                      {suggestions.recent.map((r) => (
                        <button key={r} onClick={() => submit(r)} className="flex items-center gap-1 rounded-full bg-mist px-3 py-1 text-xs font-medium hover:bg-brand-50"><Clock className="size-3" />{r}</button>
                      ))}
                    </div>
                  </>
                )}
                <p className="px-2 pb-1.5 text-[11px] font-bold uppercase tracking-wider text-muted">Trending</p>
                <div className="flex flex-wrap gap-1.5 px-1">
                  {(suggestions?.trending ?? []).map((t) => (
                    <button key={t} onClick={() => submit(t)} className="flex items-center gap-1 rounded-full bg-brand-50 px-3 py-1 text-xs font-semibold capitalize text-brand-700 hover:bg-brand-100"><TrendingUp className="size-3" />{t}</button>
                  ))}
                </div>
              </div>
            ) : items.length === 0 ? (
              <p className="px-3 py-6 text-center text-sm text-muted">{isFetching ? "Searching…" : `No results for “${debounced}”`}</p>
            ) : (
              items.map((it, i) => {
                const header = it.group !== lastGroup ? (lastGroup = it.group) : null;
                return (
                  <div key={it.key}>
                    {header && <p className="px-3 pb-1 pt-2 text-[11px] font-bold uppercase tracking-wider text-muted">{header}</p>}
                    <button id={`${listId}-${i}`} role="option" aria-selected={i === active} onMouseEnter={() => setActive(i)}
                      onClick={() => { setOpen(false); router.push(it.href); }}
                      className={cn("flex w-full items-center gap-3 rounded-xl px-3 py-2 text-left", i === active ? "bg-brand-50" : "hover:bg-mist")}>
                      <span className="grid size-8 place-items-center rounded-lg bg-white text-brand-600 ring-1 ring-line">{it.icon}</span>
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-semibold">{it.label}</span>
                        {it.sub && <span className="block truncate text-xs text-muted">{it.sub}</span>}
                      </span>
                    </button>
                  </div>
                );
              })
            )}
            {debounced.length >= 2 && (
              <button onClick={() => submit()} className="mt-1 w-full rounded-xl px-3 py-2.5 text-left text-sm font-semibold text-brand-700 hover:bg-brand-50">
                See all results for “{debounced}”
              </button>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
