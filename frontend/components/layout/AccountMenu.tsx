"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { AnimatePresence, motion } from "framer-motion";
import { Bookmark, Heart, LayoutDashboard, LogOut, ScanLine, Ticket, UserRound } from "lucide-react";
import { useAuth } from "@/features/auth/AuthProvider";
import { ButtonLink } from "@/components/ui/Button";

export function AccountMenu() {
  const { user, status, logout, hasRole } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const router = useRouter();

  useEffect(() => {
    const close = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false); };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  if (status === "loading") return <div className="size-10 rounded-full skeleton" aria-hidden />;
  if (!user) return <ButtonLink href="/login" size="sm">Sign in</ButtonLink>;

  const initials = user.fullName.split(" ").map((p) => p[0]).slice(0, 2).join("").toUpperCase();
  const links = [
    { href: "/bookings", label: "My bookings", icon: Ticket },
    { href: "/profile", label: "Profile", icon: UserRound },
    { href: "/favorites", label: "Favorites", icon: Heart },
    { href: "/watchlist", label: "Watchlist", icon: Bookmark },
    ...(hasRole("ADMIN", "PARTNER") ? [{ href: "/admin", label: "Admin console", icon: LayoutDashboard }] : []),
    ...(hasRole("VENUE_STAFF", "ADMIN") ? [{ href: "/staff", label: "Venue staff desk", icon: ScanLine }] : []),
  ];
  return (
    <div ref={ref} className="relative">
      <button onClick={() => setOpen((o) => !o)} aria-haspopup="menu" aria-expanded={open} aria-label="Account menu"
        className="grid size-10 place-items-center rounded-full bg-brand-gradient text-sm font-bold text-white ring-2 ring-white shadow-soft">
        {initials}
      </button>
      <AnimatePresence>
        {open && (
          <motion.div role="menu" initial={{ opacity: 0, y: -6, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }} exit={{ opacity: 0, y: -6 }}
            className="absolute right-0 top-12 z-50 w-64 rounded-2xl border border-line bg-white p-2 shadow-lift">
            <div className="px-3 py-2.5">
              <p className="truncate text-sm font-bold">{user.fullName}</p>
              <p className="truncate text-xs text-muted">{user.email}</p>
            </div>
            <div className="my-1 h-px bg-line" />
            {links.map(({ href, label, icon: Icon }) => (
              <Link key={href} href={href} role="menuitem" onClick={() => setOpen(false)} className="flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium hover:bg-mist">
                <Icon className="size-4 text-brand-600" /> {label}
              </Link>
            ))}
            <div className="my-1 h-px bg-line" />
            <button role="menuitem" onClick={async () => { setOpen(false); await logout(); router.push("/"); }}
              className="flex w-full items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium text-rose-600 hover:bg-rose-50">
              <LogOut className="size-4" /> Sign out
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
