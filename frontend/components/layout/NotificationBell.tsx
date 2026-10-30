"use client";

import Link from "next/link";
import { Bell } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { engagementApi } from "@/services";
import { useAuth } from "@/features/auth/AuthProvider";

export function NotificationBell({ light }: { light?: boolean }) {
  const { status } = useAuth();
  const { data } = useQuery({
    queryKey: ["notifications", "unread"],
    queryFn: engagementApi.unread,
    enabled: status === "authenticated",
    refetchInterval: 60_000,
    staleTime: 30_000,
  });
  if (status !== "authenticated") return null;
  const n = data?.unread ?? 0;
  return (
    <Link href="/notifications" aria-label={n ? `Notifications, ${n} unread` : "Notifications"}
      className={`relative grid size-10 place-items-center rounded-xl transition ${light ? "text-white hover:bg-white/10" : "text-ink-soft hover:bg-mist"}`}>
      <Bell className="size-5" />
      {n > 0 && (
        <span className="absolute right-1.5 top-1.5 grid min-w-4 place-items-center rounded-full bg-brand-gradient px-1 text-[10px] font-bold leading-4 text-white ring-2 ring-white">
          {n > 9 ? "9+" : n}
        </span>
      )}
    </Link>
  );
}
