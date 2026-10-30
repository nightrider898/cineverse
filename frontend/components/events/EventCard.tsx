"use client";

import Link from "next/link";
import { motion } from "framer-motion";
import { CalendarDays, MapPin } from "lucide-react";
import type { EventCard as EventCardT } from "@/types/api";
import { Poster } from "@/components/ui/Poster";
import { Badge } from "@/components/ui/Badge";
import { dateTime, inr } from "@/lib/format";
import { displayEventTitle } from "@/lib/event-display";
import { cn } from "@/lib/cn";

export function EventCard({ event, className }: { event: EventCardT; className?: string }) {
  const title = displayEventTitle(event);
  return (
    <motion.article whileHover={{ y: -6 }} transition={{ type: "spring", stiffness: 300, damping: 22 }} className={cn("group w-56 shrink-0 md:w-auto", className)}>
      <Link href={`/events/${event.id}`} className="block">
        <div className="relative aspect-[2/3] overflow-hidden rounded-2xl bg-brand-950 shadow-soft transition-shadow group-hover:shadow-lift">
          <Poster src={event.posterUrl} alt={`${title} poster`} className="transition-transform duration-700 group-hover:scale-110" />
          <div className="absolute left-2 top-2 flex flex-wrap gap-1">
            <Badge tone="dark">{event.category.name}</Badge>
            {event.soldOut && <Badge tone="danger">Sold out</Badge>}
          </div>
          {event.nextStartTime && (
            <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/85 to-transparent p-3 pt-10 text-xs font-semibold text-white">
              <span className="flex items-center gap-1"><CalendarDays className="size-3.5" />{dateTime(event.nextStartTime)}</span>
            </div>
          )}
        </div>
        <h3 className="mt-2.5 line-clamp-2 font-display text-[15px] font-bold leading-snug group-hover:text-brand-700">
          {title !== event.title ? title : event.teamHome && event.teamAway ? `${event.teamHome} vs ${event.teamAway}` : title}
        </h3>
        <p className="mt-0.5 flex items-center gap-1 truncate text-xs text-muted"><MapPin className="size-3" />{event.venueName}</p>
        {event.minPrice != null && <p className="mt-1 text-sm font-semibold">{inr(event.minPrice)} <span className="text-xs font-normal text-muted">onwards</span></p>}
      </Link>
    </motion.article>
  );
}
