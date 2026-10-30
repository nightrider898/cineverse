"use client";

import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BellRing, CheckCheck, CircleAlert, CreditCard, Megaphone, RotateCcw, Ticket, TimerReset } from "lucide-react";
import { engagementApi } from "@/services";
import { useRequireAuth } from "@/features/auth/useRequireAuth";
import { Button } from "@/components/ui/Button";
import { RowSkeleton } from "@/components/ui/Skeleton";
import { EmptyState, ErrorState } from "@/components/ui/States";
import { dateTime } from "@/lib/format";
import { cn } from "@/lib/cn";

const ICONS: Record<string, React.ReactNode> = {
  BOOKING_CONFIRMED: <Ticket className="size-5" />, PAYMENT_SUCCESS: <CreditCard className="size-5" />, PAYMENT_FAILED: <CircleAlert className="size-5" />,
  SHOW_REMINDER: <TimerReset className="size-5" />, CANCELLATION: <RotateCcw className="size-5" />, REFUND: <RotateCcw className="size-5" />, PROMOTION: <Megaphone className="size-5" />,
};

export default function NotificationsPage() {
  const { ready } = useRequireAuth();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ["notifications", "list"], queryFn: () => engagementApi.notifications(), enabled: ready });
  const refresh = () => { qc.invalidateQueries({ queryKey: ["notifications"] }); };
  const readAll = useMutation({ mutationFn: engagementApi.markAllRead, onSuccess: refresh });
  const readOne = useMutation({ mutationFn: engagementApi.markRead, onSuccess: refresh });
  const unread = q.data?.content.filter((n) => !n.read).length ?? 0;
  return (
    <div className="container-page max-w-3xl py-8 pb-24 md:py-12">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-extrabold">Notifications</h1>
        {unread > 0 && <Button variant="ghost" size="sm" onClick={() => readAll.mutate()} icon={<CheckCheck className="size-4" />}>Mark all read</Button>}
      </div>
      <div className="mt-6">
        {!ready || q.isLoading ? <RowSkeleton /> : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} />
          : !q.data?.content.length ? <EmptyState icon={<BellRing className="size-7" />} title="No notifications" message="Booking confirmations, reminders and refund updates will show up here." />
          : (
            <ul className="space-y-2">
              {q.data.content.map((n) => (
                <li key={n.id}>
                  <Link href={n.link ?? "#"} onClick={() => !n.read && readOne.mutate(n.id)}
                    className={cn("flex gap-4 rounded-2xl border p-4 transition hover:shadow-soft", n.read ? "border-line bg-white" : "border-brand-200 bg-brand-50/60")}>
                    <span className={cn("grid size-10 shrink-0 place-items-center rounded-xl", n.read ? "bg-mist text-muted" : "bg-brand-gradient text-white")}>{ICONS[n.type] ?? <BellRing className="size-5" />}</span>
                    <span className="min-w-0 flex-1">
                      <span className="flex items-start justify-between gap-2"><span className="font-semibold">{n.title}</span>{!n.read && <span className="mt-1.5 size-2 shrink-0 rounded-full bg-brand-500" aria-label="Unread" />}</span>
                      <span className="block text-sm text-ink-soft">{n.body}</span>
                      <span className="mt-1 block text-xs text-muted">{dateTime(n.createdAt)}</span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
      </div>
    </div>
  );
}
