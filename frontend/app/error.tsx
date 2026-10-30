"use client";

import { ErrorState } from "@/components/ui/States";

export default function GlobalError({ error, reset }: { error: Error; reset: () => void }) {
  return (
    <div className="container-page py-16">
      <ErrorState error={error} title="Something went wrong" onRetry={reset} />
    </div>
  );
}
