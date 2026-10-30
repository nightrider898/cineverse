"use client";

import Link from "next/link";
import { motion } from "framer-motion";
import { ChevronRight } from "lucide-react";
import type { EventCard as EventCardT } from "@/types/api";
import { EventCard } from "./EventCard";
import { Skeleton } from "@/components/ui/Skeleton";

export function EventRail({ title, subtitle, events, loading, href }: { title: string; subtitle?: string; events?: EventCardT[]; loading?: boolean; href?: string }) {
  if (!loading && (!events || events.length === 0)) return null;
  return (
    <motion.section initial={{ opacity: 0, y: 24 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true, margin: "-80px" }} transition={{ duration: 0.5 }} className="py-6" aria-label={title}>
      <div className="container-page mb-4 flex items-end justify-between">
        <div>
          <h2 className="text-xl font-bold md:text-2xl">{title}</h2>
          {subtitle && <p className="mt-0.5 text-sm text-muted">{subtitle}</p>}
        </div>
        {href && <Link href={href} className="flex items-center text-sm font-semibold text-brand-700 hover:underline">See all <ChevronRight className="size-4" /></Link>}
      </div>
      <div className="no-scrollbar flex gap-4 overflow-x-auto px-4 pb-4 md:gap-5 md:px-6 xl:px-[max(2rem,calc((100vw-1320px)/2+2rem))]">
        {loading ? Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="aspect-[2/3] w-56 shrink-0 rounded-2xl" />)
          : events!.map((e) => <EventCard key={e.id} event={e} className="md:w-56" />)}
      </div>
    </motion.section>
  );
}
