import { ButtonLink } from "@/components/ui/Button";

export default function NotFound() {
  return (
    <div className="container-page flex min-h-[60vh] flex-col items-center justify-center py-20 text-center">
      <p className="font-display text-7xl font-extrabold text-brand-gradient">404</p>
      <h1 className="mt-3 text-2xl font-bold">This scene didn't make the final cut</h1>
      <p className="mt-2 text-muted">The page you're looking for doesn't exist or has moved.</p>
      <ButtonLink href="/" className="mt-6">Back to BookMyEvent</ButtonLink>
    </div>
  );
}
