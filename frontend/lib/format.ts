const IST = "Asia/Kolkata";

export const inr = (v: number | undefined | null, opts: { decimals?: boolean } = {}) =>
  v == null ? "—" : new Intl.NumberFormat("en-IN", {
    style: "currency", currency: "INR", maximumFractionDigits: opts.decimals ? 2 : Number.isInteger(v) ? 0 : 2, minimumFractionDigits: opts.decimals ? 2 : 0,
  }).format(v);

export const time = (iso: string) => new Intl.DateTimeFormat("en-IN", { hour: "numeric", minute: "2-digit", hour12: true, timeZone: IST }).format(new Date(iso));

export const dayLabel = (iso: string) => new Intl.DateTimeFormat("en-IN", { weekday: "short", timeZone: IST }).format(new Date(iso));

export const date = (iso: string, opts: Intl.DateTimeFormatOptions = { day: "numeric", month: "short", year: "numeric" }) =>
  new Intl.DateTimeFormat("en-IN", { ...opts, timeZone: IST }).format(new Date(iso));

export const dateTime = (iso: string) =>
  new Intl.DateTimeFormat("en-IN", { weekday: "short", day: "numeric", month: "short", hour: "numeric", minute: "2-digit", hour12: true, timeZone: IST }).format(new Date(iso));

/** yyyy-mm-dd dates from the API are calendar dates (IST); format without a timezone shift. */
export const calendarDay = (ymd: string) => {
  const [y, m, d] = ymd.split("-").map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d));
  return {
    weekday: new Intl.DateTimeFormat("en-IN", { weekday: "short", timeZone: "UTC" }).format(dt).toUpperCase(),
    day: d,
    month: new Intl.DateTimeFormat("en-IN", { month: "short", timeZone: "UTC" }).format(dt).toUpperCase(),
    long: new Intl.DateTimeFormat("en-IN", { weekday: "long", day: "numeric", month: "long", timeZone: "UTC" }).format(dt),
  };
};

export const todayIST = () => new Intl.DateTimeFormat("en-CA", { timeZone: IST }).format(new Date());

export const duration = (min: number) => `${Math.floor(min / 60)}h ${min % 60}m`;

export const formatLabel = (f: string) => (f === "DOLBY_ATMOS" ? "Dolby Atmos" : f);

export const titleCase = (s: string) => s.toLowerCase().replace(/(^|[_\s])(\w)/g, (_, sp, c) => (sp ? " " : "") + c.toUpperCase());

export const compact = (n: number) => new Intl.NumberFormat("en-IN", { notation: "compact", maximumFractionDigits: 1 }).format(n);

/** Release text that never invents a precise date when only the year is known. */
export const releaseLabel = (m: { releaseDate: string; releaseDateEstimated?: boolean; status: string }, short = false) => {
  const year = m.releaseDate.slice(0, 4);
  if (m.releaseDateEstimated) return m.status === "COMING_SOON" ? `Expected ${year}` : year;
  return date(m.releaseDate + "T00:00:00+05:30", short ? { day: "numeric", month: "short" } : { day: "numeric", month: "short", year: "numeric" });
};
