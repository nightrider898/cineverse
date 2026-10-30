"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "./AuthProvider";

/**
 * Client-side route guard for pages that need a signed-in user (or specific roles).
 * This is UX only: every protected API call is authorised again by the backend.
 */
export function useRequireAuth(roles?: string[]) {
  const { status, user, hasRole } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const allowed = status === "authenticated" && (!roles || hasRole(...roles));

  useEffect(() => {
    if (status === "anonymous") {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    } else if (status === "authenticated" && roles && !hasRole(...roles)) {
      router.replace("/");
    }
  }, [status, roles, hasRole, router, pathname]);

  return { ready: allowed, status, user };
}
