"use client";

import { motion } from "framer-motion";
import { cn } from "@/lib/cn";

export function Tabs<T extends string>({ tabs, value, onChange, className, id = "tabs" }: {
  tabs: { value: T; label: string; count?: number }[]; value: T; onChange: (v: T) => void; className?: string; id?: string;
}) {
  return (
    <div role="tablist" className={cn("no-scrollbar flex gap-1 overflow-x-auto rounded-2xl bg-mist p-1", className)}>
      {tabs.map((t) => {
        const active = t.value === value;
        return (
          <button key={t.value} role="tab" aria-selected={active} onClick={() => onChange(t.value)}
            className={cn("relative whitespace-nowrap rounded-xl px-4 py-2 text-sm font-semibold transition-colors", active ? "text-brand-700" : "text-muted hover:text-ink")}>
            {active && <motion.span layoutId={`${id}-pill`} className="absolute inset-0 rounded-xl bg-white shadow-soft" transition={{ type: "spring", damping: 30, stiffness: 400 }} />}
            <span className="relative">{t.label}{t.count != null && <span className="ml-1.5 text-xs text-muted">{t.count}</span>}</span>
          </button>
        );
      })}
    </div>
  );
}
