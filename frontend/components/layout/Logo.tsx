import Link from "next/link";
import { cn } from "@/lib/cn";

export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 40 40" className={cn("size-9", className)} aria-hidden>
      <defs>
        <linearGradient id="cv-g" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#c2410c" />
          <stop offset=".55" stopColor="#f97316" />
          <stop offset="1" stopColor="#fdba74" />
        </linearGradient>
      </defs>
      <rect width="40" height="40" rx="12" fill="url(#cv-g)" />
      <rect x="8" y="10" width="24" height="21" rx="4" fill="#fff" />
      <path d="M13 8v5m14-5v5M9 16h22" fill="none" stroke="#c2410c" strokeWidth="2" strokeLinecap="round" />
      <circle cx="20" cy="23" r="3" fill="#f97316" />
    </svg>
  );
}

export function Logo({ light, className }: { light?: boolean; className?: string }) {
  return (
    <Link href="/" className={cn("group flex items-center gap-2.5", className)} aria-label="BookMyEvent home">
      <LogoMark className="transition-transform duration-300 group-hover:rotate-[-8deg]" />
      <span className={cn("font-display text-xl font-extrabold tracking-tight", light ? "text-white" : "text-ink")}>
        BookMy<span className={light ? "text-brand-200" : "text-brand-600"}>Event</span>
      </span>
    </Link>
  );
}
