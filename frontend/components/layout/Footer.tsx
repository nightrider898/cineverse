import Link from "next/link";
import { Logo } from "./Logo";
import { brand } from "@/lib/brand";

export function Footer() {
  return (
    <footer className="mt-20 border-t border-line bg-white pb-24 pt-14 text-muted md:pb-10">
      <div className="container-page grid gap-10 md:grid-cols-4">
        <div className="md:col-span-2">
          <Logo />
          <p className="mt-4 max-w-sm text-sm leading-relaxed">
            Movies, concerts, comedy, sports and weekend plans — booked in a few taps with real-time seats and instant QR tickets.
          </p>
          <p className="mt-3 font-display text-sm font-semibold italic text-brand-700">“{brand.motto}”</p>
          <ul className="mt-5 flex flex-wrap gap-2" aria-label="Social media">
            {brand.socials.map((s) => (
              <li key={s.label}><a href={s.href} target="_blank" rel="noopener noreferrer" className="inline-flex rounded-full border border-line px-3 py-1.5 text-xs font-semibold text-ink-soft transition hover:border-brand-300 hover:text-brand-700">{s.label}</a></li>
            ))}
          </ul>
          <p className="mt-6 text-xs text-muted">Event listings are examples. Cinema seat layouts and showtimes are illustrative.</p>
        </div>
        <div>
          <h3 className="text-sm font-bold text-ink">Explore</h3>
          <ul className="mt-3 space-y-2 text-sm">
            {[["/movies", "Movies"], ["/events", "Events"], ["/sports", "Sports"], ["/plays", "Plays"], ["/activities", "Activities"], ["/offers", "Offers"]].map(([h, l]) => (
              <li key={h}><Link href={h} className="hover:text-brand-700">{l}</Link></li>
            ))}
          </ul>
        </div>
        <div>
          <h3 className="text-sm font-bold text-ink">Help</h3>
          <ul className="mt-3 space-y-2 text-sm">
            <li><Link href="/bookings" className="hover:text-brand-700">My bookings</Link></li>
            <li><Link href="/cinemas" className="hover:text-brand-700">Cinemas</Link></li>
            <li><Link href="/profile" className="hover:text-brand-700">Account</Link></li>
          </ul>
        </div>
      </div>
      <div className="container-page mt-10 flex flex-col gap-3 border-t border-line pt-6 text-xs text-muted md:flex-row md:items-center md:justify-between">
        <span>© {new Date().getFullYear()} {brand.name}. Help: <a href={`mailto:${brand.supportEmail}`} className="hover:text-brand-700">{brand.supportEmail}</a></span>
        {/* Attribution required by the TMDB API terms of use */}
        <span>Posters and movie data from <a href="https://www.themoviedb.org/" target="_blank" rel="noopener noreferrer" className="font-semibold hover:text-brand-700">TMDB</a>. This product uses the TMDB API but is not endorsed or certified by TMDB. Trailers play from YouTube.</span>
      </div>
    </footer>
  );
}
