"use client";

import type { ReactNode } from "react";
import { Toaster } from "sonner";
import { QueryProvider } from "@/features/query/QueryProvider";
import { AuthProvider } from "@/features/auth/AuthProvider";
import { CityProvider } from "@/features/location/CityProvider";

export function Providers({ children }: { children: ReactNode }) {
  return (
    <QueryProvider>
      <AuthProvider>
        <CityProvider>
          {children}
          <Toaster position="top-center" richColors closeButton toastOptions={{ className: "font-sans" }} />
        </CityProvider>
      </AuthProvider>
    </QueryProvider>
  );
}
