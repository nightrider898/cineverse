"use client";

import { useEffect, useRef, useState } from "react";
import { Timer } from "lucide-react";
import { cn } from "@/lib/cn";

/**
 * Seconds remaining until `expiresAt`, corrected for clock skew using the server's own timestamp,
 * updated every second. Calls onExpire once.
 */
export function useCountdown(expiresAt?: string, serverTime?: string, onExpire?: () => void) {
  const skew = useRef(0);
  const [left, setLeft] = useState<number | null>(null);
  const fired = useRef(false);

  useEffect(() => {
    skew.current = serverTime ? new Date(serverTime).getTime() - Date.now() : 0;
    fired.current = false;
  }, [serverTime, expiresAt]);

  useEffect(() => {
    if (!expiresAt) { setLeft(null); return; }
    const tick = () => {
      const ms = new Date(expiresAt).getTime() - (Date.now() + skew.current);
      const s = Math.max(0, Math.floor(ms / 1000));
      setLeft(s);
      if (s === 0 && !fired.current) { fired.current = true; onExpire?.(); }
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [expiresAt, onExpire]);

  return left;
}

export const mmss = (s: number) => `${String(Math.floor(s / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;

export function LockTimer({ seconds, className }: { seconds: number | null; className?: string }) {
  if (seconds == null) return null;
  const urgent = seconds <= 60;
  return (
    <div role="timer" aria-live={urgent ? "assertive" : "off"}
      className={cn("inline-flex items-center gap-2 rounded-full px-3.5 py-1.5 text-sm font-semibold ring-1 ring-inset tabular",
        urgent ? "bg-rose-50 text-rose-700 ring-rose-200" : "bg-brand-50 text-brand-700 ring-brand-100", className)}>
      <Timer className={cn("size-4", urgent && "animate-pulse")} aria-hidden />
      Seats reserved for {mmss(seconds)}
    </div>
  );
}
