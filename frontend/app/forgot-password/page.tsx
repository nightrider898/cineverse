"use client";

import Link from "next/link";
import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { MailCheck } from "lucide-react";
import { authApi } from "@/services";
import { resetSchema } from "@/schemas/auth";
import { AuthShell } from "@/components/layout/AuthShell";
import { Field, Input } from "@/components/ui/Field";
import { Button } from "@/components/ui/Button";
import { ApiError } from "@/lib/api-client";

function Inner() {
  const token = useSearchParams().get("token");
  return token ? <ResetForm token={token} /> : <RequestForm />;
}

function RequestForm() {
  const [sent, setSent] = useState(false);
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<{ email: string }>({ resolver: zodResolver(z.object({ email: z.string().email("Enter a valid email") })) });
  const onSubmit = handleSubmit(async ({ email }) => {
    try { await authApi.forgotPassword(email); setSent(true); } catch (e) { toast.error((e as ApiError).message); }
  });
  return (
    <AuthShell title="Reset your password" subtitle="We'll email you a secure link that's valid for 30 minutes." footer={<Link href="/login" className="font-semibold text-brand-700 hover:underline">Back to sign in</Link>}>
      {sent ? (
        <div className="rounded-3xl bg-brand-50 p-6 text-center" role="status">
          <MailCheck className="mx-auto size-10 text-brand-600" />
          <p className="mt-3 font-semibold">Check your inbox</p>
          <p className="mt-1 text-sm text-muted">If an account exists for that email, a reset link is on its way. (In development the link is printed in the backend log.)</p>
        </div>
      ) : (
        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          <Field label="Email" htmlFor="email" error={errors.email?.message}><Input id="email" type="email" autoComplete="email" {...register("email")} /></Field>
          <Button type="submit" size="lg" className="w-full" loading={isSubmitting}>Send reset link</Button>
        </form>
      )}
    </AuthShell>
  );
}

function ResetForm({ token }: { token: string }) {
  const router = useRouter();
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<z.infer<typeof resetSchema>>({ resolver: zodResolver(resetSchema) });
  const onSubmit = handleSubmit(async (v) => {
    try { await authApi.resetPassword(token, v.newPassword); toast.success("Password updated. Please sign in."); router.replace("/login"); }
    catch (e) { setError("root", { message: (e as ApiError).message }); }
  });
  return (
    <AuthShell title="Choose a new password" subtitle="You'll be signed out on all devices.">
      <form onSubmit={onSubmit} className="space-y-4" noValidate>
        <Field label="New password" htmlFor="np" error={errors.newPassword?.message}><Input id="np" type="password" autoComplete="new-password" {...register("newPassword")} /></Field>
        <Field label="Confirm password" htmlFor="cp" error={errors.confirm?.message}><Input id="cp" type="password" autoComplete="new-password" {...register("confirm")} /></Field>
        {errors.root && <p role="alert" className="rounded-xl bg-rose-50 p-3 text-sm text-rose-700">{errors.root.message}</p>}
        <Button type="submit" size="lg" className="w-full" loading={isSubmitting}>Update password</Button>
      </form>
    </AuthShell>
  );
}

export default function ForgotPasswordPage() {
  return <Suspense><Inner /></Suspense>;
}
