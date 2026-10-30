import { cn } from "@/lib/cn";

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn("skeleton rounded-xl", className)} />;
}

export function MovieCardSkeleton() {
  return (
    <div className="w-40 shrink-0 sm:w-48 md:w-auto" aria-hidden>
      <Skeleton className="aspect-[2/3] w-full rounded-2xl" />
      <Skeleton className="mt-3 h-4 w-3/4" />
      <Skeleton className="mt-2 h-3 w-1/2" />
    </div>
  );
}

export function RowSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className="space-y-3" role="status" aria-label="Loading">
      {Array.from({ length: rows }).map((_, i) => <Skeleton key={i} className="h-24 w-full rounded-2xl" />)}
    </div>
  );
}
