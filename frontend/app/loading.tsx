import { Skeleton } from "@/components/ui/Skeleton";

export default function Loading() {
  return (
    <div className="container-page py-8" role="status" aria-label="Loading">
      <Skeleton className="h-64 w-full rounded-3xl md:h-96" />
      <div className="mt-8 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
        {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="aspect-[2/3] rounded-2xl" />)}
      </div>
    </div>
  );
}
