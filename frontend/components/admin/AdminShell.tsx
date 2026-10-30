"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";
import {
  BarChart3, Building2, CalendarClock, Clapperboard, CreditCard, FileClock, Gauge, LayoutGrid, Menu, MessageSquareQuote,
  Popcorn, ReceiptText, RotateCcw, Sparkles, TicketPercent, Megaphone, Users, X, ExternalLink,
} from "lucide-react";
import { useRequireAuth } from "@/features/auth/useRequireAuth";
import { useAuth } from "@/features/auth/AuthProvider";
import { Logo } from "@/components/layout/Logo";
import { Skeleton } from "@/components/ui/Skeleton";
import { cn } from "@/lib/cn";

const NAV = [
  { group: "Overview", items: [{ href: "/admin", label: "Dashboard", icon: Gauge, admin: true }, { href: "/admin/analytics", label: "Analytics", icon: BarChart3, admin: true }] },
  { group: "Catalog", items: [
    { href: "/admin/movies", label: "Movies", icon: Clapperboard, admin: true },
    { href: "/admin/cinemas", label: "Cinemas", icon: Building2 },
    { href: "/admin/screens", label: "Screens & seats", icon: LayoutGrid },
    { href: "/admin/shows", label: "Shows & pricing", icon: CalendarClock },
    { href: "/admin/events", label: "Events", icon: Sparkles, admin: true },
    { href: "/admin/food", label: "Food menu", icon: Popcorn },
  ] },
  { group: "Commerce", items: [
    { href: "/admin/bookings", label: "Bookings", icon: ReceiptText, admin: true },
    { href: "/admin/payments", label: "Payments", icon: CreditCard, admin: true },
    { href: "/admin/refunds", label: "Refunds", icon: RotateCcw, admin: true },
    { href: "/admin/coupons", label: "Coupons", icon: TicketPercent, admin: true },
    { href: "/admin/offers", label: "Offers", icon: Megaphone, admin: true },
  ] },
  { group: "People", items: [
    { href: "/admin/users", label: "Users", icon: Users, admin: true },
    { href: "/admin/reviews", label: "Reviews", icon: MessageSquareQuote, admin: true },
    { href: "/admin/audit-logs", label: "Audit log", icon: FileClock, admin: true },
  ] },
];

export function AdminShell({ children }: { children: ReactNode }) {
  const { ready } = useRequireAuth(["ADMIN", "PARTNER"]);
  const { user, hasRole } = useAuth();
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const isAdmin = hasRole("ADMIN");

  const nav = (
    <nav aria-label="Admin" className="space-y-6">
      {NAV.map((g) => {
        const items = g.items.filter((i) => isAdmin || !("admin" in i && i.admin));
        if (!items.length) return null;
        return (
          <div key={g.group}>
            <p className="px-3 pb-2 text-[11px] font-bold uppercase tracking-widest text-white/40">{g.group}</p>
            <ul className="space-y-0.5">
              {items.map(({ href, label, icon: Icon }) => {
                const active = href === "/admin" ? pathname === "/admin" : pathname.startsWith(href);
                return (
                  <li key={href}>
                    <Link href={href} onClick={() => setOpen(false)} aria-current={active ? "page" : undefined}
                      className={cn("flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition", active ? "bg-white/12 text-white" : "text-white/65 hover:bg-white/6 hover:text-white")}>
                      <Icon className={cn("size-4", active && "text-brand-300")} />{label}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </div>
        );
      })}
    </nav>
  );

  if (!ready) return <div className="p-8"><Skeleton className="h-[80vh] rounded-3xl" /></div>;
  return (
    <div className="min-h-screen bg-mist lg:grid lg:grid-cols-[250px_1fr]">
      <aside className="hidden bg-brand-950 p-5 lg:sticky lg:top-0 lg:block lg:h-screen lg:overflow-y-auto">
        <Logo light />
        <p className="mb-6 mt-1 pl-12 text-[11px] font-semibold uppercase tracking-widest text-brand-300">{isAdmin ? "Admin" : "Partner"} console</p>
        {nav}
        <Link href="/" className="mt-8 flex items-center gap-2 px-3 text-xs text-white/50 hover:text-white"><ExternalLink className="size-3.5" />Customer site</Link>
      </aside>
      <div className="min-w-0">
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-line bg-white/90 px-4 backdrop-blur md:px-8">
          <button className="grid size-10 place-items-center rounded-xl hover:bg-mist lg:hidden" onClick={() => setOpen(true)} aria-label="Open admin menu"><Menu className="size-5" /></button>
          <p className="hidden text-sm text-muted lg:block">Signed in as <b className="text-ink">{user?.fullName}</b></p>
          <span className="rounded-full bg-brand-50 px-3 py-1 text-xs font-semibold text-brand-700">{user?.roles.join(" · ")}</span>
        </header>
        <main id="main" className="p-4 md:p-8">{children}</main>
      </div>
      {open && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="absolute inset-0 bg-ink/50" onClick={() => setOpen(false)} />
          <div className="absolute inset-y-0 left-0 w-72 overflow-y-auto bg-brand-950 p-5">
            <div className="mb-6 flex items-center justify-between"><Logo light /><button onClick={() => setOpen(false)} aria-label="Close menu" className="text-white"><X /></button></div>
            {nav}
          </div>
        </div>
      )}
    </div>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div><h1 className="text-2xl font-extrabold md:text-3xl">{title}</h1>{subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}</div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function Panel({ title, children, className, actions }: { title?: string; children: ReactNode; className?: string; actions?: ReactNode }) {
  return (
    <section className={cn("rounded-3xl border border-line bg-white p-5", className)}>
      {(title || actions) && <div className="mb-4 flex items-center justify-between gap-2">{title && <h2 className="font-bold">{title}</h2>}{actions}</div>}
      {children}
    </section>
  );
}
