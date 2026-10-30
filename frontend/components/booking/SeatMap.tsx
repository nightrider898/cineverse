"use client";

import { memo, useCallback, useMemo, useRef } from "react";
import { motion } from "framer-motion";
import type { Seat, SeatDisplayStatus, SeatPrice } from "@/types/api";
import { inr } from "@/lib/format";
import { cn } from "@/lib/cn";

const TYPE_LABEL: Record<string, string> = { VIP: "VIP Recliner", PREMIUM: "Premium", GOLD: "Gold", SILVER: "Silver" };

type Props = {
  seats: Seat[];
  columns: number;
  pricing: SeatPrice[];
  selected: Set<string>;
  onToggle: (seat: Seat) => void;
  zoom?: number;
};

/**
 * Cinema seat map. Seats are placed on a CSS grid by (rowIndex, colIndex) from the backend layout,
 * so gaps in colIndex render as aisles. Arrow keys move between seats (roving tab index).
 */
export function SeatMap({ seats, columns, pricing, selected, onToggle, zoom = 1 }: Props) {
  const rows = useMemo(() => {
    const byRow = new Map<number, Seat[]>();
    seats.forEach((s) => byRow.set(s.rowIndex, [...(byRow.get(s.rowIndex) ?? []), s]));
    return [...byRow.entries()].sort((a, b) => a[0] - b[0]).map(([, r]) => r.sort((a, b) => a.colIndex - b.colIndex));
  }, [seats]);
  const priceOf = useMemo(() => Object.fromEntries(pricing.map((p) => [p.seatType, p.price])), [pricing]);
  const grid = useRef<HTMLDivElement>(null);
  const firstFocusable = useMemo(() => seats.find((s) => s.status === "AVAILABLE")?.label, [seats]);

  const onKeyDown = useCallback((e: React.KeyboardEvent<HTMLDivElement>) => {
    const target = e.target as HTMLElement;
    const r = Number(target.dataset.row), c = Number(target.dataset.col);
    if (Number.isNaN(r)) return;
    const moves: Record<string, [number, number]> = { ArrowLeft: [0, -1], ArrowRight: [0, 1], ArrowUp: [-1, 0], ArrowDown: [1, 0] };
    const mv = moves[e.key];
    if (!mv) return;
    e.preventDefault();
    const candidates = seats.filter((s) => s.status !== "DISABLED");
    let best: Seat | undefined;
    let bestScore = Infinity;
    for (const s of candidates) {
      const dr = s.rowIndex - r, dc = s.colIndex - c;
      if (mv[0] !== 0 && Math.sign(dr) !== mv[0]) continue;
      if (mv[1] !== 0 && (dr !== 0 || Math.sign(dc) !== mv[1])) continue;
      const score = Math.abs(dr) * 100 + Math.abs(dc);
      if (score < bestScore) { bestScore = score; best = s; }
    }
    if (best) grid.current?.querySelector<HTMLButtonElement>(`[data-label="${best.label}"]`)?.focus();
  }, [seats]);

  let lastType = "";
  return (
    <div style={{ zoom }}>
      <div ref={grid} role="grid" aria-label="Seat map" onKeyDown={onKeyDown} className="mx-auto w-max select-none">
        {rows.map((row, ri) => {
          const type = row[0].seatType;
          const header = type !== lastType;
          lastType = type;
          return (
            <div key={row[0].row} role="row">
              {header && (
                <div className={cn("mb-2 flex items-center gap-3", ri > 0 && "mt-6")} role="presentation">
                  <span className={cn("text-[11px] font-bold uppercase tracking-[0.18em]", type === "VIP" ? "text-gold" : "text-muted")}>
                    {TYPE_LABEL[type] ?? type} · {inr(priceOf[type])}
                  </span>
                  <span className="h-px flex-1 bg-line" />
                </div>
              )}
              <div className="flex items-center gap-3">
                <span className="w-5 text-center text-xs font-bold text-muted" aria-hidden>{row[0].row}</span>
                <div className="grid gap-1.5" style={{ gridTemplateColumns: `repeat(${columns}, 1.9rem)` }}>
                  {row.map((s) => (
                    <SeatButton key={s.label} seat={s} selected={selected.has(s.label)} onToggle={onToggle}
                      tabbable={s.label === (selected.values().next().value ?? firstFocusable)} typeLabel={TYPE_LABEL[s.seatType] ?? s.seatType} />
                  ))}
                </div>
                <span className="w-5 text-center text-xs font-bold text-muted" aria-hidden>{row[0].row}</span>
              </div>
            </div>
          );
        })}
      </div>
      <div className="mx-auto mt-10 max-w-lg" aria-hidden>
        <div className="h-3 rounded-[50%] bg-gradient-to-b from-brand-300 to-transparent opacity-80 shadow-[0_-12px_40px_8px_rgba(249,115,22,0.3)]" style={{ transform: "perspective(300px) rotateX(-30deg)" }} />
        <p className="mt-3 text-center text-[11px] font-bold uppercase tracking-[0.35em] text-muted">Screen this way</p>
      </div>
    </div>
  );
}

const SeatButton = memo(function SeatButton({ seat, selected, onToggle, tabbable, typeLabel }: {
  seat: Seat; selected: boolean; onToggle: (s: Seat) => void; tabbable: boolean; typeLabel: string;
}) {
  if (seat.status === "DISABLED") {
    return <span role="gridcell" aria-label={`Seat ${seat.label}, not available`} style={{ gridColumn: seat.colIndex }} className="grid size-[1.9rem] place-items-center text-[10px] text-line">✕</span>;
  }
  const status: SeatDisplayStatus | "SELECTED" = selected ? "SELECTED" : seat.lockedByYou ? "AVAILABLE" : seat.status;
  const interactive = status === "AVAILABLE" || status === "SELECTED";
  const vip = seat.seatType === "VIP";
  const stateText = status === "SELECTED" ? "selected" : status === "LOCKED" ? "held by another customer" : status === "BOOKED" ? "sold" : "available";
  return (
    <span role="gridcell" style={{ gridColumn: seat.colIndex }}>
      <motion.button
        type="button"
        data-label={seat.label}
        data-row={seat.rowIndex}
        data-col={seat.colIndex}
        tabIndex={tabbable ? 0 : -1}
        disabled={!interactive}
        aria-pressed={status === "SELECTED"}
        aria-label={`Seat ${seat.label}, ${typeLabel}, ${stateText}, ${inr(seat.price)}`}
        onClick={() => onToggle(seat)}
        whileTap={interactive ? { scale: 0.88 } : undefined}
        animate={{ scale: status === "SELECTED" ? 1.12 : 1 }}
        transition={{ type: "spring", stiffness: 500, damping: 22 }}
        className={cn(
          "relative grid size-[1.9rem] place-items-center rounded-t-lg rounded-b-md border text-[10px] font-bold transition-[background,box-shadow,color,border-color] duration-200 focus-visible:z-10",
          status === "AVAILABLE" && (vip ? "border-gold/70 bg-gold-soft text-amber-800 hover:bg-amber-100" : "border-brand-200 bg-white text-brand-700 hover:border-brand-500 hover:bg-brand-50"),
          status === "SELECTED" && "border-transparent bg-brand-gradient text-white shadow-[0_0_0_3px_rgba(249,115,22,0.25),0_6px_16px_-4px_rgba(234,88,12,0.65)]",
          status === "LOCKED" && "cursor-not-allowed border-orange-400 bg-orange-200 text-orange-700",
          status === "BOOKED" && "cursor-not-allowed border-transparent bg-zinc-200 text-zinc-400",
        )}
      >
        {seat.number}
        {vip && status === "AVAILABLE" && <span className="absolute -top-1 size-1.5 rounded-full bg-gold" aria-hidden />}
      </motion.button>
    </span>
  );
});

export function SeatLegend() {
  const items = [
    { label: "Available", cls: "border-brand-200 bg-white" },
    { label: "Selected", cls: "border-transparent bg-brand-gradient" },
    { label: "Being booked", cls: "border-orange-400 bg-orange-200" },
    { label: "Sold", cls: "border-transparent bg-zinc-200" },
    { label: "VIP", cls: "border-gold/70 bg-gold-soft" },
  ];
  return (
    <ul className="flex flex-wrap items-center justify-center gap-x-5 gap-y-2 text-xs text-muted" aria-label="Seat legend">
      {items.map((i) => (
        <li key={i.label} className="flex items-center gap-2"><span className={cn("size-4 rounded-t-md rounded-b-sm border", i.cls)} aria-hidden />{i.label}</li>
      ))}
    </ul>
  );
}
