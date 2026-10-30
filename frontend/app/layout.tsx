import type { Metadata, Viewport } from "next";
import "./globals.css";
import { Providers } from "./providers";
import { AppChrome } from "@/components/layout/AppChrome";

const APP = process.env.NEXT_PUBLIC_APP_NAME || "BookMyEvent";

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL || "http://localhost:3000"),
  title: { default: `${APP} — Movies, Events & Live Experiences`, template: `%s | ${APP}` },
  description: "Book movie tickets, concerts, comedy, plays, sports and activities with live seat selection and instant QR tickets.",
  openGraph: { type: "website", siteName: APP, images: ["/media/og-default.svg"] },
  icons: { icon: "/icon.svg" },
};

export const viewport: Viewport = {
  themeColor: "#ea580c",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en-IN">
      <body>
        <Providers>
          <AppChrome>{children}</AppChrome>
        </Providers>
      </body>
    </html>
  );
}
