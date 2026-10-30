"use client";

import Link from "next/link";
import { Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { z } from "zod";
import { toast } from "sonner";
import { registerSchema } from "@/schemas/auth";
import { useAuth } from "@/features/auth/AuthProvider";
import { AuthShell } from "@/components/layout/AuthShell";
import { Field, Input } from "@/components/ui/Field";
import { Button } from "@/components/ui/Button";
import { ApiError } from "@/lib/api-client";

function RegisterForm() {
  const { register: signUp } = useAuth();
  const router = useRouter();
  const nextParam = useSearchParams().get("next");
  const next = nextParam && nextParam.startsWith("/") && !nextParam.startsWith("//") ? nextParam : "/";
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<z.infer<typeof registerSchema>>({ resolver: zodResolver(registerSchema) });

  const onSubmit = handleSubmit(async ({ confirm: _c, ...v }) => {
    try {
      await signUp({ ...v, phone: v.phone || undefined });
      toast.success("Account created — welcome to BookMyEvent!");
      router.replace(next);
    } catch (e) {
      const err = e as ApiError;
      if (err.fields) Object.entries(err.fields).forEach(([k, m]) => setError(k as "email", { message: m }));
      else if (err.code === "EMAIL_ALREADY_REGISTERED") setError("email", { message: "An account with this email already exists" });
      else setError("root", { message: err.message });
    }
  });

  return (
    <AuthShell title="Create your account" subtitle="Get ₹100 off your first booking with WELCOME100." footer={<>Already have an account? <Link href="/login" className="font-semibold text-brand-700 hover:underline">Sign in</Link></>}>
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <Field label="Full name" htmlFor="fullName" error={errors.fullName?.message}><Input id="fullName" autoComplete="name" invalid={!!errors.fullName} {...register("fullName")} /></Field>
        <Field label="Email" htmlFor="email" error={errors.email?.message}><Input id="email" type="email" autoComplete="email" invalid={!!errors.email} {...register("email")} /></Field>
        <Field label="Mobile (optional)" htmlFor="phone" error={errors.phone?.message}><Input id="phone" type="tel" autoComplete="tel" placeholder="+91" {...register("phone")} /></Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Password" htmlFor="password" error={errors.password?.message} hint="8+ characters, a letter and a number"><Input id="password" type="password" autoComplete="new-password" invalid={!!errors.password} {...register("password")} /></Field>
          <Field label="Confirm password" htmlFor="confirm" error={errors.confirm?.message}><Input id="confirm" type="password" autoComplete="new-password" invalid={!!errors.confirm} {...register("confirm")} /></Field>
        </div>
        {errors.root && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{errors.root.message}</p>}
        <Button type="submit" size="lg" className="w-full" loading={isSubmitting}>Create account</Button>
      </form>
    </AuthShell>
  );
}

export default function RegisterPage() {
  return <Suspense><RegisterForm /></Suspense>;
}
