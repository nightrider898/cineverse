"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { motion } from "framer-motion";
import { Clapperboard, Home, Sparkles, Ticket, UserRound } from "lucide-react";
import { cn } from "@/lib/cn";

const ITEMS = [
  { href: "/", label: "Home", icon: Home, match: (p: string) => p === "/" },
  { href: "/movies", label: "Movies", icon: Clapperboard, match: (p: string) => p.startsWith("/movies") },
  { href: "/events", label: "Live", icon: Sparkles, match: (p: string) => ["/events", "/sports", "/plays", "/activities"].some((x) => p.startsWith(x)) },
  { href: "/bookings", label: "Tickets", icon: Ticket, match: (p: string) => p.startsWith("/bookings") },
  { href: "/profile", label: "Profile", icon: UserRound, match: (p: string) => ["/profile", "/login", "/favorites", "/watchlist"].some((x) => p.startsWith(x)) },
];

/** Phone bottom navigation. Hidden inside the booking funnel where a sticky CTA takes its place. */
export function MobileNav() {
  const pathname = usePathname();
  if (["/booking/", "/checkout/", "/payment/", "/dev/"].some((p) => pathname.startsWith(p))) return null;
  return (
    <nav aria-label="Mobile" className="glass fixed inset-x-0 bottom-0 z-40 border-t border-line pb-[env(safe-area-inset-bottom)] md:hidden">
      <ul className="grid grid-cols-5">
        {ITEMS.map(({ href, label, icon: Icon, match }) => {
          const active = match(pathname);
          return (
            <li key={href}>
              <Link href={href} aria-current={active ? "page" : undefined} className={cn("relative flex flex-col items-center gap-0.5 py-2 text-[11px] font-semibold", active ? "text-brand-700" : "text-muted")}>
                {active && <motion.span layoutId="mobile-nav" className="absolute top-0 h-0.5 w-8 rounded-full bg-brand-gradient" />}
                <Icon className="size-5" />
                {label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
