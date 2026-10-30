"use client";

import { Area, AreaChart, Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { Point, Ranked } from "@/types/admin";
import { compact, inr } from "@/lib/format";

// Single-series charts in one sequential brand hue; values stay in ink tokens, grid and axes recessive.
const HUE = "#ea580c";
const GRID = "#ebe7f5";
const TICK = { fill: "#6e6886", fontSize: 11 };

const shortDay = (ymd: string) => { const [, m, d] = ymd.split("-"); return `${Number(d)}/${Number(m)}`; };

function TooltipBox({ active, payload, label, money }: { active?: boolean; payload?: { value: number }[]; label?: string; money?: boolean }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-xl border border-line bg-white px-3 py-2 text-xs shadow-soft">
      <p className="text-muted">{label}</p>
      <p className="text-sm font-bold text-ink">{money ? inr(payload[0].value) : payload[0].value.toLocaleString("en-IN")}</p>
    </div>
  );
}

export function TrendArea({ data, money, label, suffix }: { data: Point[]; money?: boolean; label: string; suffix?: string }) {
  return (
    <div className="h-56" role="img" aria-label={`${label} trend`}>
      <ResponsiveContainer>
        <AreaChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <defs><linearGradient id={`fill-${label}`} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor={HUE} stopOpacity={0.25} /><stop offset="1" stopColor={HUE} stopOpacity={0} /></linearGradient></defs>
          <CartesianGrid stroke={GRID} vertical={false} />
          <XAxis dataKey="label" tickFormatter={shortDay} tick={TICK} axisLine={false} tickLine={false} minTickGap={16} />
          <YAxis tick={TICK} axisLine={false} tickLine={false} width={48} tickFormatter={(v) => (money ? `₹${compact(v)}` : `${compact(v)}${suffix ?? ""}`)} />
          <Tooltip content={<TooltipBox money={money} />} cursor={{ stroke: HUE, strokeDasharray: "3 3" }} />
          <Area type="monotone" dataKey="value" stroke={HUE} strokeWidth={2} fill={`url(#fill-${label})`} activeDot={{ r: 5, strokeWidth: 2, stroke: "#fff" }} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

export function TrendBars({ data, label }: { data: Point[]; label: string }) {
  return (
    <div className="h-56" role="img" aria-label={`${label} per day`}>
      <ResponsiveContainer>
        <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }} barCategoryGap={4}>
          <CartesianGrid stroke={GRID} vertical={false} />
          <XAxis dataKey="label" tickFormatter={shortDay} tick={TICK} axisLine={false} tickLine={false} minTickGap={16} />
          <YAxis tick={TICK} axisLine={false} tickLine={false} width={36} allowDecimals={false} />
          <Tooltip content={<TooltipBox />} cursor={{ fill: "#f5f1ff" }} />
          <Bar dataKey="value" fill={HUE} radius={[4, 4, 0, 0]} maxBarSize={28} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Ranked list as horizontal bars with direct labels (no legend needed for one series). */
export function RankBars({ data, money, empty = "No data in this range" }: { data: Ranked[]; money?: boolean; empty?: string }) {
  if (!data.length) return <p className="py-6 text-center text-sm text-muted">{empty}</p>;
  const max = Math.max(...data.map((d) => (money ? d.amount : d.count)), 1);
  return (
    <ul className="space-y-3">
      {data.map((d) => {
        const v = money ? d.amount : d.count;
        return (
          <li key={d.name} className="text-sm">
            <div className="flex justify-between gap-3"><span className="truncate font-medium">{d.name.replace(/_/g, " ")}</span><span className="shrink-0 font-semibold tabular">{money ? inr(v) : v.toLocaleString("en-IN")}</span></div>
            <div className="mt-1 h-2 rounded-full bg-mist" title={`${d.count} bookings · ${inr(d.amount)}`}>
              <div className="h-2 rounded-full" style={{ width: `${(v / max) * 100}%`, background: HUE }} />
            </div>
          </li>
        );
      })}
    </ul>
  );
}

export function StatTile({ label, value, hint, icon }: { label: string; value: string; hint?: string; icon?: React.ReactNode }) {
  return (
    <div className="rounded-3xl border border-line bg-white p-5">
      <div className="flex items-center justify-between"><p className="text-sm text-muted">{label}</p>{icon && <span className="grid size-9 place-items-center rounded-xl bg-brand-50 text-brand-600">{icon}</span>}</div>
      <p className="mt-2 font-display text-3xl font-extrabold tabular">{value}</p>
      {hint && <p className="mt-1 text-xs text-muted">{hint}</p>}
    </div>
  );
}
