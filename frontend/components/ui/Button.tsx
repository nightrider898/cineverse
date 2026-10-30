"use client";

import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from "react";
import Link from "next/link";
import { Loader2 } from "lucide-react";
import { cn } from "@/lib/cn";

type Variant = "primary" | "secondary" | "ghost" | "outline" | "danger" | "light";
type Size = "sm" | "md" | "lg";

const variants: Record<Variant, string> = {
  primary: "bg-brand-gradient text-white shadow-[0_8px_24px_-10px_rgba(234,88,12,0.55)] hover:brightness-110 active:brightness-95",
  secondary: "bg-brand-50 text-brand-700 hover:bg-brand-100",
  ghost: "text-ink-soft hover:bg-mist",
  outline: "border border-line bg-white text-ink hover:border-brand-300 hover:text-brand-700",
  danger: "bg-danger text-white hover:brightness-110",
  light: "bg-white/15 text-white ring-1 ring-white/30 backdrop-blur hover:bg-white/25",
};
const sizes: Record<Size, string> = {
  sm: "h-9 px-3.5 text-sm gap-1.5 rounded-xl",
  md: "h-11 px-5 text-sm gap-2 rounded-xl",
  lg: "h-13 px-7 text-base gap-2 rounded-2xl",
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  icon?: ReactNode;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = "primary", size = "md", loading, icon, className, children, disabled, ...rest }, ref,
) {
  return (
    <button
      ref={ref}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(
        "inline-flex select-none items-center justify-center font-semibold transition-all duration-200 disabled:cursor-not-allowed disabled:opacity-55",
        variants[variant], sizes[size], className,
      )}
      {...rest}
    >
      {loading ? <Loader2 className="size-4 animate-spin" aria-hidden /> : icon}
      {children}
    </button>
  );
});

export function ButtonLink({ href, variant = "primary", size = "md", className, children, icon }: {
  href: string; variant?: Variant; size?: Size; className?: string; children: ReactNode; icon?: ReactNode;
}) {
  return (
    <Link href={href} className={cn("inline-flex items-center justify-center font-semibold transition-all duration-200", variants[variant], sizes[size], className)}>
      {icon}
      {children}
    </Link>
  );
}
