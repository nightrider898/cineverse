"use client";

import Link from "next/link";
import { useEffect } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { z } from "zod";
import { toast } from "sonner";
import { Bell, Bookmark, CreditCard, Heart, LogOut, MapPin, Star, Ticket } from "lucide-react";
import { authApi, engagementApi, userApi } from "@/services";
import { tokenStore } from "@/lib/api-client";
import { useAuth } from "@/features/auth/AuthProvider";
import { useRequireAuth } from "@/features/auth/useRequireAuth";
import { useCity } from "@/features/location/CityProvider";
import { changePasswordSchema, profileSchema } from "@/schemas/auth";
import { Field, Input } from "@/components/ui/Field";
import { Button } from "@/components/ui/Button";
import { StatusBadge } from "@/components/ui/Badge";
import { Skeleton } from "@/components/ui/Skeleton";
import { ApiError } from "@/lib/api-client";
import { date, dateTime, inr } from "@/lib/format";

export default function ProfilePage() {
  const { ready, user } = useRequireAuth();
  const { setUser, logout } = useAuth();
  const { city, openPicker } = useCity();
  const payments = useQuery({ queryKey: ["my-payments"], queryFn: userApi.payments, enabled: ready });
  const reviews = useQuery({ queryKey: ["my-reviews"], queryFn: engagementApi.myReviews, enabled: ready });

  const profile = useForm<z.infer<typeof profileSchema>>({ resolver: zodResolver(profileSchema) });
  useEffect(() => { if (user) profile.reset({ fullName: user.fullName, phone: user.phone ?? "", avatarUrl: user.avatarUrl ?? "" }); }, [user]); // eslint-disable-line react-hooks/exhaustive-deps
  const save = useMutation({
    mutationFn: (v: z.infer<typeof profileSchema>) => userApi.update({ fullName: v.fullName, phone: v.phone || undefined, avatarUrl: v.avatarUrl || undefined, preferredCityId: city?.id }),
    onSuccess: (u) => { setUser(u); toast.success("Profile saved"); },
    onError: (e: ApiError) => toast.error(e.message),
  });

  const pw = useForm<z.infer<typeof changePasswordSchema>>({ resolver: zodResolver(changePasswordSchema) });
  const change = useMutation({
    mutationFn: (v: z.infer<typeof changePasswordSchema>) => authApi.changePassword(v.currentPassword, v.newPassword),
    onSuccess: (auth) => { tokenStore.set(auth); pw.reset(); toast.success("Password changed. Other devices were signed out."); },
    onError: (e: ApiError) => pw.setError("currentPassword", { message: e.message }),
  });

  if (!ready || !user) return <div className="container-page py-10"><Skeleton className="h-64 rounded-3xl" /></div>;
  const links = [
    { href: "/bookings", label: "Bookings", icon: Ticket }, { href: "/favorites", label: "Favorites", icon: Heart },
    { href: "/watchlist", label: "Watchlist", icon: Bookmark }, { href: "/notifications", label: "Notifications", icon: Bell },
  ];

  return (
    <div className="container-page py-8 pb-24 md:py-12">
      <div className="flex flex-col gap-6 rounded-[2rem] bg-brand-gradient-animated p-6 text-white md:flex-row md:items-center md:p-8">
        <div className="grid size-20 place-items-center rounded-3xl bg-white/20 font-display text-3xl font-extrabold ring-4 ring-white/30">
          {user.fullName.split(" ").map((p) => p[0]).slice(0, 2).join("")}
        </div>
        <div className="flex-1">
          <h1 className="text-3xl font-extrabold">{user.fullName}</h1>
          <p className="text-white/80">{user.email}{user.phone && ` · ${user.phone}`}</p>
          <button onClick={openPicker} className="mt-2 inline-flex items-center gap-1 text-sm font-semibold text-white/90 hover:underline"><MapPin className="size-4" />{city?.name ?? "Choose city"} · change</button>
        </div>
        <Button variant="light" onClick={logout} icon={<LogOut className="size-4" />}>Sign out</Button>
      </div>

      <nav className="mt-6 grid grid-cols-2 gap-3 md:grid-cols-4" aria-label="Account sections">
        {links.map(({ href, label, icon: Icon }) => (
          <Link key={href} href={href} className="flex items-center gap-3 rounded-2xl border border-line p-4 font-semibold hover:border-brand-300 hover:shadow-soft"><Icon className="size-5 text-brand-600" />{label}</Link>
        ))}
      </nav>

      <div className="mt-8 grid gap-6 lg:grid-cols-2">
        <section className="rounded-3xl border border-line p-6">
          <h2 className="text-lg font-bold">Personal details</h2>
          <form className="mt-4 space-y-4" onSubmit={profile.handleSubmit((v) => save.mutate(v))} noValidate>
            <Field label="Full name" htmlFor="fn" error={profile.formState.errors.fullName?.message}><Input id="fn" {...profile.register("fullName")} /></Field>
            <Field label="Mobile" htmlFor="ph" error={profile.formState.errors.phone?.message}><Input id="ph" type="tel" {...profile.register("phone")} /></Field>
            <Field label="Profile photo URL" htmlFor="av" error={profile.formState.errors.avatarUrl?.message} hint="Optional"><Input id="av" {...profile.register("avatarUrl")} /></Field>
            <Field label="Email" htmlFor="em" hint="Contact support to change your email"><Input id="em" value={user.email} disabled readOnly /></Field>
            <Button type="submit" loading={save.isPending}>Save changes</Button>
          </form>
        </section>

        <section className="rounded-3xl border border-line p-6">
          <h2 className="text-lg font-bold">Change password</h2>
          <form className="mt-4 space-y-4" onSubmit={pw.handleSubmit((v) => change.mutate(v))} noValidate>
            <Field label="Current password" htmlFor="cp" error={pw.formState.errors.currentPassword?.message}><Input id="cp" type="password" autoComplete="current-password" {...pw.register("currentPassword")} /></Field>
            <Field label="New password" htmlFor="np" error={pw.formState.errors.newPassword?.message}><Input id="np" type="password" autoComplete="new-password" {...pw.register("newPassword")} /></Field>
            <Field label="Confirm new password" htmlFor="cnp" error={pw.formState.errors.confirm?.message}><Input id="cnp" type="password" autoComplete="new-password" {...pw.register("confirm")} /></Field>
            <Button type="submit" variant="secondary" loading={change.isPending}>Update password</Button>
          </form>
        </section>

        <section className="rounded-3xl border border-line p-6">
          <h2 className="flex items-center gap-2 text-lg font-bold"><CreditCard className="size-5 text-brand-600" />Payment history</h2>
          {payments.isLoading ? <Skeleton className="mt-4 h-32" /> : !payments.data?.length ? <p className="mt-4 text-sm text-muted">No payments yet.</p> : (
            <ul className="mt-4 divide-y divide-line">
              {payments.data.slice(0, 10).map((p) => (
                <li key={p.paymentId} className="flex items-center justify-between gap-3 py-3 text-sm">
                  <div><Link href={`/bookings/${p.bookingId}`} className="font-semibold hover:text-brand-700">{p.bookingReference}</Link><p className="text-xs text-muted">{p.method.replace("_", " ")} · {dateTime(p.updatedAt)}</p></div>
                  <div className="text-right"><p className="font-semibold">{inr(p.amount, { decimals: true })}</p><StatusBadge status={p.status} /></div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="rounded-3xl border border-line p-6">
          <h2 className="flex items-center gap-2 text-lg font-bold"><Star className="size-5 text-brand-600" />Your reviews</h2>
          {!reviews.data?.length ? <p className="mt-4 text-sm text-muted">Reviews you write after watching appear here.</p> : (
            <ul className="mt-4 space-y-3">
              {reviews.data.map((r) => (
                <li key={r.id} className="rounded-2xl bg-mist p-3 text-sm">
                  <div className="flex justify-between"><Link href={`/movies/${r.movieId}`} className="font-semibold hover:text-brand-700">{r.movieTitle}</Link><StatusBadge status={r.status} /></div>
                  <p className="text-xs text-muted">{r.rating}★ · {date(r.createdAt)}</p>
                  {r.title && <p className="mt-1 font-medium">{r.title}</p>}
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
