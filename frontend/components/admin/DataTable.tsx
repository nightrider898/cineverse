import type { ReactNode } from "react";
import { cn } from "@/lib/cn";
import { Skeleton } from "@/components/ui/Skeleton";

export type Column<T> = { key: string; header: string; cell: (row: T) => ReactNode; className?: string };

export function DataTable<T>({ rows, columns, rowKey, loading, empty = "Nothing here yet", caption }: {
  rows?: T[]; columns: Column<T>[]; rowKey: (r: T) => string | number; loading?: boolean; empty?: string; caption?: string;
}) {
  return (
    <div className="overflow-x-auto rounded-3xl border border-line bg-white">
      <table className="w-full min-w-[720px] text-left text-sm">
        {caption && <caption className="sr-only">{caption}</caption>}
        <thead className="border-b border-line bg-mist/60 text-xs uppercase tracking-wider text-muted">
          <tr>{columns.map((c) => <th key={c.key} scope="col" className={cn("px-4 py-3 font-bold", c.className)}>{c.header}</th>)}</tr>
        </thead>
        <tbody className="divide-y divide-line">
          {loading ? Array.from({ length: 5 }).map((_, i) => (
            <tr key={i}><td colSpan={columns.length} className="px-4 py-3"><Skeleton className="h-6" /></td></tr>
          )) : !rows?.length ? (
            <tr><td colSpan={columns.length} className="px-4 py-12 text-center text-muted">{empty}</td></tr>
          ) : rows.map((r) => (
            <tr key={rowKey(r)} className="hover:bg-mist/40">
              {columns.map((c) => <td key={c.key} className={cn("px-4 py-3 align-middle", c.className)}>{c.cell(r)}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
