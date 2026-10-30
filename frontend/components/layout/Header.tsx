"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { ChevronDown, MapPin, Search } from "lucide-react";
import { Logo } from "./Logo";
import { SearchBox } from "./SearchBox";
import { NotificationBell } from "./NotificationBell";
import { AccountMenu } from "./AccountMenu";
import { useCity } from "@/features/location/CityProvider";
import { cn } from "@/lib/cn";

export const NAV = [
  { href: "/movies", label: "Movies" },
  { href: "/events", label: "Events" },
  { href: "/sports", label: "Sports" },
  { href: "/plays", label: "Plays" },
  { href: "/activities", label: "Activities" },
  { href: "/offers", label: "Offers" },
];

export function Header() {
  const pathname = usePathname();
  const { city, citySlug, openPicker } = useCity();
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const on = () => setScrolled(window.scrollY > 8);
    on();
    window.addEventListener("scroll", on, { passive: true });
    return () => window.removeEventListener("scroll", on);
  }, []);

  const cityName = city?.name ?? citySlug.replace(/^\w/, (c) => c.toUpperCase());
  return (
    <header className={cn("sticky top-0 z-40 transition-all", scrolled ? "glass border-b border-line/70 shadow-[0_6px_24px_-18px_rgba(63,27,122,0.4)]" : "bg-white")}>
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-2 focus:z-50 focus:rounded-lg focus:bg-white focus:px-3 focus:py-2">Skip to content</a>
      <div className="container-page flex h-16 items-center gap-3 md:h-[72px] md:gap-6">
        <Logo />
        <SearchBox compact className="hidden max-w-md flex-1 md:block" />
        <div className="ml-auto flex items-center gap-1.5 md:gap-2">
          <button onClick={openPicker} className="flex items-center gap-1 rounded-xl px-2.5 py-2 text-sm font-semibold text-ink-soft hover:bg-mist" aria-label={`City: ${cityName}. Change city`}>
            <MapPin className="size-4 text-brand-600" />
            <span className="max-w-24 truncate">{cityName}</span>
            <ChevronDown className="size-3.5 text-muted" />
          </button>
          <Link href="/search" className="grid size-10 place-items-center rounded-xl text-ink-soft hover:bg-mist md:hidden" aria-label="Search"><Search className="size-5" /></Link>
          <NotificationBell />
          <div className="hidden md:block"><AccountMenu /></div>
        </div>
      </div>
      <nav aria-label="Primary" className="container-page hidden h-11 items-center gap-1 border-t border-line/60 md:flex">
        {NAV.map((n) => {
          const active = pathname.startsWith(n.href);
          return (
            <Link key={n.href} href={n.href} aria-current={active ? "page" : undefined}
              className={cn("relative rounded-lg px-3 py-1.5 text-sm font-semibold transition", active ? "text-brand-700" : "text-ink-soft hover:text-ink")}>
              {n.label}
              {active && <span className="absolute inset-x-3 -bottom-[5px] h-0.5 rounded-full bg-brand-gradient" />}
            </Link>
          );
        })}
        <div className="ml-auto flex items-center gap-4 text-xs font-medium text-muted">
          <Link href="/cinemas" className="hover:text-brand-700">Cinemas near you</Link>
          <Link href="/bookings" className="hover:text-brand-700">My bookings</Link>
        </div>
      </nav>
    </header>
  );
}
