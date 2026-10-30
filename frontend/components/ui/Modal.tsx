"use client";

import { AnimatePresence, motion } from "framer-motion";
import { X } from "lucide-react";
import { useEffect, useRef, type ReactNode } from "react";
import { cn } from "@/lib/cn";

/** Accessible dialog: focus moves in, Escape closes, background is inert; slides up as a sheet on phones. */
export function Modal({ open, onClose, title, children, className, wide }: {
  open: boolean; onClose: () => void; title: string; children: ReactNode; className?: string; wide?: boolean;
}) {
  const panel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const prev = document.activeElement as HTMLElement | null;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    setTimeout(() => panel.current?.querySelector<HTMLElement>("input,button,[tabindex]")?.focus(), 30);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
      prev?.focus();
    };
  }, [open, onClose]);

  return (
    <AnimatePresence>
      {open && (
        <motion.div className="fixed inset-0 z-[80] flex items-end justify-center sm:items-center" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          <div className="absolute inset-0 bg-ink/45 backdrop-blur-sm" onClick={onClose} aria-hidden />
          <motion.div
            ref={panel}
            role="dialog"
            aria-modal="true"
            aria-label={title}
            initial={{ y: 40, opacity: 0, scale: 0.98 }}
            animate={{ y: 0, opacity: 1, scale: 1 }}
            exit={{ y: 30, opacity: 0 }}
            transition={{ type: "spring", damping: 28, stiffness: 320 }}
            className={cn("relative max-h-[92vh] w-full overflow-y-auto rounded-t-3xl bg-white p-5 shadow-lift sm:rounded-3xl sm:p-7", wide ? "sm:max-w-3xl" : "sm:max-w-lg", className)}
          >
            <div className="mb-4 flex items-start justify-between gap-4">
              <h2 className="text-xl font-bold">{title}</h2>
              <button onClick={onClose} className="grid size-9 place-items-center rounded-full text-muted hover:bg-mist" aria-label="Close">
                <X className="size-5" />
              </button>
            </div>
            {children}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
