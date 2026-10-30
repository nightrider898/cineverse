"use client";

import type { ReactNode } from "react";
import { AlertTriangle, RefreshCw, WifiOff, Inbox } from "lucide-react";
import { Button } from "./Button";
import { ApiError } from "@/lib/api-client";
import { cn } from "@/lib/cn";

export function ErrorState({ error, title, onRetry, className }: { error?: unknown; title?: string; onRetry?: () => void; className?: string }) {
  const network = error instanceof ApiError && error.isNetwork;
  const message = network ? "Unable to connect to BookMyEvent servers." : error instanceof ApiError ? error.message : "Something went wrong.";
  return (
    <div role="alert" className={cn("flex flex-col items-center rounded-3xl border border-line bg-mist/60 px-6 py-12 text-center", className)}>
      <div className="grid size-14 place-items-center rounded-2xl bg-white shadow-soft">
        {network ? <WifiOff className="size-6 text-brand-600" /> : <AlertTriangle className="size-6 text-amber-500" />}
      </div>
      <h3 className="mt-4 text-lg font-bold">{title ?? (network ? "You're offline from BookMyEvent" : "Something went wrong")}</h3>
      <p className="mt-1 max-w-md text-sm text-muted">{message}</p>
      {onRetry && <Button className="mt-5" variant="secondary" icon={<RefreshCw className="size-4" />} onClick={onRetry}>Retry</Button>}
    </div>
  );
}

export function EmptyState({ title, message, icon, action, className }: { title: string; message?: string; icon?: ReactNode; action?: ReactNode; className?: string }) {
  return (
    <div className={cn("flex flex-col items-center rounded-3xl border border-dashed border-brand-200 bg-gradient-to-b from-brand-50/60 to-white px-6 py-14 text-center", className)}>
      <div className="grid size-16 place-items-center rounded-2xl bg-white text-brand-600 shadow-soft">{icon ?? <Inbox className="size-7" />}</div>
      <h3 className="mt-4 text-lg font-bold">{title}</h3>
      {message && <p className="mt-1 max-w-sm text-sm text-muted">{message}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}
