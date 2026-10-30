import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

export function Chip({ active, onClick, children, className }: { active?: boolean; onClick?: () => void; children: ReactNode; className?: string }) {
  return (
    <button type="button" onClick={onClick} aria-pressed={active}
      className={cn("whitespace-nowrap rounded-full border px-3.5 py-1.5 text-sm font-medium transition",
        active ? "border-brand-500 bg-brand-50 text-brand-700 shadow-[inset_0_0_0_1px_var(--color-brand-500)]" : "border-line bg-white text-ink-soft hover:border-brand-300", className)}>
      {children}
    </button>
  );
}
