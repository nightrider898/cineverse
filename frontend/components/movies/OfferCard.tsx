"use client";

import { useState } from "react";
import { Check, Copy, TicketPercent } from "lucide-react";
import type { Offer } from "@/types/api";
import { media } from "@/lib/media";
import { date } from "@/lib/format";

export function OfferCard({ offer }: { offer: Offer }) {
  const [copied, setCopied] = useState(false);
  return (
    <article className="relative flex min-h-44 w-72 shrink-0 flex-col justify-between overflow-hidden rounded-3xl p-5 text-white shadow-soft md:w-auto"
      style={{ backgroundImage: `url(${media(offer.imageUrl)})`, backgroundSize: "cover" }}>
      <div className="absolute inset-0 bg-gradient-to-br from-black/10 to-black/30" />
      <div className="relative">
        <span className="inline-flex items-center gap-1 rounded-full bg-white/20 px-2.5 py-1 text-[11px] font-bold uppercase tracking-wide backdrop-blur">
          <TicketPercent className="size-3.5" /> {offer.offerType.replace("_", " ")}
        </span>
        <h3 className="mt-3 text-lg font-bold leading-tight">{offer.title}</h3>
        <p className="mt-1 text-sm text-white/85">{offer.description}</p>
      </div>
      <div className="relative mt-4 flex items-center justify-between gap-2">
        {offer.couponCode ? (
          <button onClick={() => { navigator.clipboard?.writeText(offer.couponCode!); setCopied(true); setTimeout(() => setCopied(false), 1500); }}
            className="flex items-center gap-2 rounded-xl border border-dashed border-white/60 bg-white/10 px-3 py-1.5 font-mono text-sm font-bold tracking-wider backdrop-blur hover:bg-white/20"
            aria-label={`Copy coupon ${offer.couponCode}`}>
            {offer.couponCode} {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
          </button>
        ) : <span className="text-xs text-white/80">Auto-applied at the venue</span>}
        <span className="text-[11px] text-white/75">Till {date(offer.validTo, { day: "numeric", month: "short" })}</span>
      </div>
    </article>
  );
}
