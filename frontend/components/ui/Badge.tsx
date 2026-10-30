import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

const tones = {
  brand: "bg-brand-50 text-brand-700 ring-brand-100",
  neutral: "bg-mist text-ink-soft ring-line",
  success: "bg-emerald-50 text-emerald-700 ring-emerald-100",
  warning: "bg-amber-50 text-amber-700 ring-amber-100",
  danger: "bg-rose-50 text-rose-700 ring-rose-100",
  gold: "bg-gold-soft text-amber-800 ring-amber-200",
  dark: "bg-black/55 text-white ring-white/10 backdrop-blur",
} as const;

export function Badge({ children, tone = "neutral", className }: { children: ReactNode; tone?: keyof typeof tones; className?: string }) {
  return (
    <span className={cn("inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset", tones[tone], className)}>
      {children}
    </span>
  );
}

const STATUS_TONES: Record<string, keyof typeof tones> = {
  CONFIRMED: "success", SUCCESS: "success", COMPLETED: "success", VALID: "success", APPROVED: "success",
  PENDING: "warning", PAYMENT_PENDING: "warning", PROCESSING: "warning", REQUESTED: "warning", REFUND_PENDING: "warning", INITIATED: "warning",
  CANCELLED: "danger", FAILED: "danger", EXPIRED: "neutral", REJECTED: "danger",
  REFUNDED: "brand", PARTIALLY_REFUNDED: "brand", USED: "neutral",
};

export function StatusBadge({ status }: { status: string }) {
  const label = status.replace(/_/g, " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
  return <Badge tone={STATUS_TONES[status] ?? "neutral"}>{label}</Badge>;
}
