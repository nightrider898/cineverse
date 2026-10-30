"use client";

import { motion } from "framer-motion";
import { Check } from "lucide-react";
import { cn } from "@/lib/cn";

const STEPS = ["Movie", "Show", "Seats", "Food", "Payment"] as const;
export type BookingStep = (typeof STEPS)[number];

export function BookingProgress({ current, className }: { current: BookingStep; className?: string }) {
  const idx = STEPS.indexOf(current);
  return (
    <nav aria-label="Booking progress" className={cn("w-full", className)}>
      <ol className="flex items-center gap-1.5 sm:gap-2">
        {STEPS.map((s, i) => {
          const done = i < idx, active = i === idx;
          return (
            <li key={s} className="flex flex-1 items-center gap-1.5 sm:gap-2" aria-current={active ? "step" : undefined}>
              <div className="flex items-center gap-1.5">
                <span className={cn("grid size-6 shrink-0 place-items-center rounded-full text-[11px] font-bold transition-colors",
                  done ? "bg-brand-600 text-white" : active ? "bg-brand-gradient text-white shadow-glow" : "bg-mist text-muted")}>
                  {done ? <Check className="size-3.5" /> : i + 1}
                </span>
                <span className={cn("hidden text-xs font-semibold sm:inline", active ? "text-ink" : "text-muted")}>{s}</span>
              </div>
              {i < STEPS.length - 1 && (
                <div className="relative h-1 flex-1 overflow-hidden rounded-full bg-mist">
                  <motion.div className="absolute inset-y-0 left-0 bg-brand-gradient" initial={false} animate={{ width: done ? "100%" : "0%" }} transition={{ duration: 0.5, ease: "easeOut" }} />
                </div>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
