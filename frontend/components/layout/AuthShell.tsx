import type { ReactNode } from "react";
import { LogoMark } from "./Logo";

export function AuthShell({ title, subtitle, children, footer }: { title: string; subtitle: string; children: ReactNode; footer?: ReactNode }) {
  return (
    <div className="grid min-h-[calc(100vh-7rem)] md:grid-cols-2">
      <div className="relative hidden overflow-hidden bg-brand-gradient-animated md:block">
        <div className="absolute -left-24 top-10 size-96 rounded-full bg-white/10 blur-3xl" />
        <div className="absolute bottom-0 right-0 size-80 rounded-full bg-fuchsia-400/20 blur-3xl" />
        <div className="relative flex h-full flex-col justify-end p-12 text-white">
          <LogoMark className="size-14" />
          <h2 className="mt-6 text-4xl font-extrabold leading-tight">Every show, every seat,<br />one universe.</h2>
          <p className="mt-3 max-w-sm text-white/80">Live seat maps, instant QR tickets and payments you can trust — for movies, concerts, sports and more.</p>
        </div>
      </div>
      <div className="flex items-center justify-center px-4 py-12">
        <div className="w-full max-w-md">
          <h1 className="text-3xl font-extrabold">{title}</h1>
          <p className="mt-1 text-muted">{subtitle}</p>
          <div className="mt-8">{children}</div>
          {footer && <div className="mt-6 text-center text-sm text-muted">{footer}</div>}
        </div>
      </div>
    </div>
  );
}
