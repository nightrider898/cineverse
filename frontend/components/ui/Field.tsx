import { forwardRef, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";
import { cn } from "@/lib/cn";

const base = "w-full rounded-xl border border-line bg-white px-3.5 text-sm text-ink placeholder:text-muted/70 transition focus:border-brand-400 focus:outline-none focus:ring-4 focus:ring-brand-100 disabled:bg-mist";

export function Field({ label, error, hint, children, htmlFor, className }: { label: string; error?: string; hint?: string; children: ReactNode; htmlFor?: string; className?: string }) {
  return (
    <div className={className}>
      <label htmlFor={htmlFor} className="mb-1.5 block text-sm font-semibold text-ink-soft">{label}</label>
      {children}
      {error ? <p className="mt-1 text-xs font-medium text-danger" role="alert">{error}</p> : hint ? <p className="mt-1 text-xs text-muted">{hint}</p> : null}
    </div>
  );
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }>(function Input({ className, invalid, ...rest }, ref) {
  return <input ref={ref} aria-invalid={invalid || undefined} className={cn(base, "h-11", invalid && "border-danger focus:ring-rose-100", className)} {...rest} />;
});

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(function Select({ className, children, ...rest }, ref) {
  return <select ref={ref} className={cn(base, "h-11 pr-8", className)} {...rest}>{children}</select>;
});

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(function Textarea({ className, ...rest }, ref) {
  return <textarea ref={ref} className={cn(base, "min-h-24 py-2.5", className)} {...rest} />;
});
