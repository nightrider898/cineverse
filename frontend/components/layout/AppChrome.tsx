"use client";

import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { Header } from "./Header";
import { MobileNav } from "./MobileNav";
import { Footer } from "./Footer";
import { CityPicker } from "./CityPicker";

/** Customer chrome (header, footer, bottom nav). The admin console renders its own shell. */
export function AppChrome({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  if (pathname.startsWith("/admin")) return <>{children}</>;
  const funnel = ["/booking/", "/checkout/", "/payment/"].some((p) => pathname.startsWith(p));
  return (
    <>
      <Header />
      <main id="main" className="min-h-[70vh]">{children}</main>
      {!funnel && <Footer />}
      <MobileNav />
      <CityPicker />
    </>
  );
}
