"use client";

import Link from "next/link";
import { Suspense, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { z } from "zod";
import { toast } from "sonner";
import { loginSchema } from "@/schemas/auth";
import { useAuth } from "@/features/auth/AuthProvider";
import { AuthShell } from "@/components/layout/AuthShell";
import { Field, Input } from "@/components/ui/Field";
import { Button } from "@/components/ui/Button";
import { ApiError } from "@/lib/api-client";

const DEMO = [
  { label: "Customer", email: "demo@cineverse.com" },
  { label: "Admin", email: "admin@cineverse.com" },
  { label: "Venue staff", email: "staff@cineverse.com" },
];

function safeNext(next: string | null) {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/";
}

function LoginForm() {
  const { login, status } = useAuth();
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const form = useForm<z.infer<typeof loginSchema>>({ resolver: zodResolver(loginSchema), defaultValues: { email: "", password: "" } });
  const { register, handleSubmit, formState: { errors, isSubmitting }, setError, setValue } = form;

  useEffect(() => { if (status === "authenticated") router.replace(next); }, [status, next, router]);

  const onSubmit = handleSubmit(async (v) => {
    try {
      const user = await login(v.email, v.password);
      toast.success(`Welcome back, ${user.fullName.split(" ")[0]}!`);
      router.replace(next);
    } catch (e) {
      const err = e as ApiError;
      setError("root", { message: err.isNetwork ? err.message : err.code === "ACCOUNT_DISABLED" ? "This account has been disabled." : "Incorrect email or password." });
    }
  });

  return (
    <AuthShell title="Welcome back" subtitle="Sign in to book tickets and see your bookings." footer={<>New to BookMyEvent? <Link href={`/register?next=${encodeURIComponent(next)}`} className="font-semibold text-brand-700 hover:underline">Create an account</Link></>}>
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <Field label="Email" htmlFor="email" error={errors.email?.message}><Input id="email" type="email" autoComplete="email" invalid={!!errors.email} {...register("email")} /></Field>
        <Field label="Password" htmlFor="password" error={errors.password?.message}><Input id="password" type="password" autoComplete="current-password" invalid={!!errors.password} {...register("password")} /></Field>
        <div className="flex justify-end"><Link href="/forgot-password" className="text-sm font-semibold text-brand-700 hover:underline">Forgot password?</Link></div>
        {errors.root && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm font-medium text-rose-700">{errors.root.message}</p>}
        <Button type="submit" size="lg" className="w-full" loading={isSubmitting}>Sign in</Button>
      </form>
      {process.env.NODE_ENV !== "production" && (
        <div className="mt-6 rounded-2xl border border-dashed border-brand-200 bg-brand-50/50 p-4">
          <p className="text-xs font-bold uppercase tracking-wider text-brand-700">Development accounts</p>
          <p className="mt-1 text-xs text-muted">Password is the value of <code>DEMO_USER_PASSWORD</code> (default <code>Demo@12345</code>).</p>
          <div className="mt-3 flex flex-wrap gap-2">
            {DEMO.map((d) => (
              <button key={d.email} type="button" onClick={() => { setValue("email", d.email); setValue("password", "Demo@12345"); }}
                className="rounded-full bg-white px-3 py-1 text-xs font-semibold ring-1 ring-line hover:ring-brand-300">{d.label}</button>
            ))}
          </div>
        </div>
      )}
    </AuthShell>
  );
}

export default function LoginPage() {
  return <Suspense><LoginForm /></Suspense>;
}
