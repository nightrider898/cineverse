import { Star } from "lucide-react";
import { cn } from "@/lib/cn";
import { compact } from "@/lib/format";

export function RatingPill({ rating, votes, className, dark }: { rating: number; votes?: number; className?: string; dark?: boolean }) {
  if (!rating) return <span className={cn("text-xs font-semibold", dark ? "text-white/80" : "text-muted", className)}>New</span>;
  return (
    <span className={cn("inline-flex items-center gap-1 text-xs font-bold", dark ? "text-white" : "text-ink", className)}>
      <Star className="size-3.5 fill-amber-400 text-amber-400" aria-hidden />
      {Number(rating).toFixed(1)}<span className="sr-only"> out of 5</span>
      {votes != null && votes > 0 && <span className={cn("font-medium", dark ? "text-white/70" : "text-muted")}>· {compact(votes)} votes</span>}
    </span>
  );
}
