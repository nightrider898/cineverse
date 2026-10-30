"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Clapperboard, Drama, PartyPopper, Trophy, Tent, ArrowRight } from "lucide-react";
import { catalogApi, eventApi, movieApi } from "@/services";
import { useCity } from "@/features/location/CityProvider";
import { useAuth } from "@/features/auth/AuthProvider";
import { HeroCarousel } from "@/components/movies/HeroCarousel";
import { MovieRail } from "@/components/movies/MovieRail";
import { TrailerModal } from "@/components/movies/TrailerModal";
import { OfferCard } from "@/components/movies/OfferCard";
import { EventRail } from "@/components/events/EventRail";
import { ErrorState } from "@/components/ui/States";
import type { Movie } from "@/types/api";

const CATEGORIES = [
  { href: "/movies", label: "Movies", icon: Clapperboard, tint: "from-violet-500 to-fuchsia-500" },
  { href: "/events", label: "Events", icon: PartyPopper, tint: "from-pink-500 to-rose-500" },
  { href: "/sports", label: "Sports", icon: Trophy, tint: "from-emerald-500 to-teal-500" },
  { href: "/plays", label: "Plays", icon: Drama, tint: "from-amber-500 to-orange-500" },
  { href: "/activities", label: "Activities", icon: Tent, tint: "from-sky-500 to-indigo-500" },
];

export default function HomePage() {
  const { citySlug, city } = useCity();
  const { user } = useAuth();
  const [trailer, setTrailer] = useState<Movie | null>(null);
  const home = useQuery({ queryKey: ["home", citySlug, user?.id], queryFn: () => movieApi.home(citySlug) });
  const events = useQuery({ queryKey: ["events", "home", citySlug], queryFn: () => eventApi.list("events", { city: citySlug }) });
  const sports = useQuery({ queryKey: ["sports", "home", citySlug], queryFn: () => eventApi.list("sports", { city: citySlug }) });
  const offers = useQuery({ queryKey: ["offers", citySlug], queryFn: () => catalogApi.offers(citySlug) });
  const cityName = city?.name ?? "your city";

  return (
    <div className="pb-8">
      {home.isError ? (
        <div className="container-page pt-6"><ErrorState error={home.error} onRetry={() => home.refetch()} /></div>
      ) : (
        <HeroCarousel movies={home.data?.featured} loading={home.isLoading} onTrailer={setTrailer} />
      )}

      <nav aria-label="Categories" className="container-page mt-6">
        <ul className="no-scrollbar -mx-1 flex gap-3 overflow-x-auto px-1 pb-1 md:grid md:grid-cols-5">
          {CATEGORIES.map(({ href, label, icon: Icon, tint }, i) => (
            <motion.li key={href} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.05 * i }}>
              <Link href={href} className="group flex min-w-36 items-center gap-3 rounded-2xl border border-line bg-white p-3 pr-4 transition hover:-translate-y-0.5 hover:border-brand-200 hover:shadow-soft">
                <span className={`grid size-11 place-items-center rounded-xl bg-gradient-to-br ${tint} text-white shadow-soft transition-transform group-hover:scale-110 group-hover:rotate-[-6deg]`}>
                  <Icon className="size-5" />
                </span>
                <span className="font-display text-sm font-bold">{label}</span>
              </Link>
            </motion.li>
          ))}
        </ul>
      </nav>

      <MovieRail title={`Now showing in ${cityName}`} subtitle="Book the best seats before they go" movies={home.data?.nowShowing} loading={home.isLoading} href="/movies" onTrailer={setTrailer} />
      <MovieRail title="Trending this week" movies={home.data?.trending} loading={home.isLoading} onTrailer={setTrailer} />
      {user && <MovieRail title="Recommended for you" subtitle="Based on what you've watched" movies={home.data?.recommended} onTrailer={setTrailer} />}

      {!!offers.data?.length && (
        <section className="py-6" aria-label="Offers">
          <div className="container-page mb-4 flex items-end justify-between">
            <div><h2 className="text-xl font-bold md:text-2xl">Offers for you</h2><p className="text-sm text-muted">Apply the code at checkout</p></div>
            <Link href="/offers" className="flex items-center text-sm font-semibold text-brand-700 hover:underline">All offers <ArrowRight className="ml-1 size-4" /></Link>
          </div>
          <div className="no-scrollbar flex gap-4 overflow-x-auto px-4 pb-2 md:grid md:grid-cols-3 md:px-6 xl:px-[max(2rem,calc((100vw-1320px)/2+2rem))]">
            {offers.data.slice(0, 3).map((o) => <OfferCard key={o.id} offer={o} />)}
          </div>
        </section>
      )}

      <MovieRail title="Top rated" subtitle="Highest rated films showing now" movies={home.data?.topRated} onTrailer={setTrailer} />
      <EventRail title={`Live events in ${cityName}`} subtitle="Comedy, music, festivals and more" events={events.data?.content} loading={events.isLoading} href="/events" />

      <section className="container-page py-6">
        <div className="relative overflow-hidden rounded-[2rem] bg-brand-gradient-animated p-8 text-white md:p-12">
          <div className="absolute -right-16 -top-16 size-72 rounded-full bg-white/10 blur-2xl" />
          <div className="absolute -bottom-24 left-1/3 size-72 rounded-full bg-fuchsia-400/20 blur-3xl" />
          <div className="relative max-w-xl">
            <p className="text-sm font-semibold uppercase tracking-widest text-white/75">New to BookMyEvent?</p>
            <h2 className="mt-2 text-3xl font-extrabold md:text-4xl">Your first booking is on us — ₹100 off</h2>
            <p className="mt-2 text-white/85">Use code <b className="font-mono">WELCOME100</b> at checkout. Pay with UPI, cards or at the venue.</p>
            <Link href={user ? "/movies" : "/register"} className="mt-6 inline-flex h-12 items-center gap-2 rounded-2xl bg-white px-6 font-bold text-brand-700 transition hover:-translate-y-0.5">
              {user ? "Find a movie" : "Create free account"} <ArrowRight className="size-4" />
            </Link>
          </div>
        </div>
      </section>

      <EventRail title="Sports near you" events={sports.data?.content} href="/sports" />
      <MovieRail title="Coming soon" subtitle="Add to your watchlist and we'll remind you" movies={home.data?.comingSoon} onTrailer={setTrailer} />
      <MovieRail title="All-time classics" subtitle="Weekend re-release screenings of the greatest films ever made" movies={home.data?.classics} href="/movies?status=CLASSIC" onTrailer={setTrailer} />
      <TrailerModal movie={trailer} onClose={() => setTrailer(null)} />
    </div>
  );
}
