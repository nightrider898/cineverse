"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { ChevronLeft } from "lucide-react";
import { BookingProgress, type BookingStep } from "@/components/ui/BookingProgress";

export function BookingHeader({ back, title, subtitle, step, right }: { back: string; title: string; subtitle?: string; step: BookingStep; right?: ReactNode }) {
  return (
    <div className="border-b border-line bg-white">
      <div className="container-page flex flex-col gap-4 py-4 md:flex-row md:items-center md:justify-between">
        <div className="flex items-center gap-3">
          <Link href={back} aria-label="Back" className="grid size-10 shrink-0 place-items-center rounded-xl border border-line hover:border-brand-300 hover:text-brand-700"><ChevronLeft className="size-5" /></Link>
          <div className="min-w-0">
            <h1 className="truncate font-display text-lg font-bold md:text-xl">{title}</h1>
            {subtitle && <p className="truncate text-xs text-muted md:text-sm">{subtitle}</p>}
          </div>
        </div>
        <div className="flex flex-col gap-3 md:w-[440px] md:items-end">
          <BookingProgress current={step} />
          {right}
        </div>
      </div>
    </div>
  );
}
