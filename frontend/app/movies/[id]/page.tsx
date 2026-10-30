import type { Metadata } from "next";
import { serverGet } from "@/lib/server-api";
import type { MovieDetail } from "@/types/api";
import { MovieDetailView } from "./MovieDetailView";

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const d = await serverGet<MovieDetail>(`/movies/${id}`);
  if (!d) return { title: "Movie" };
  const m = d.movie;
  const description = `${m.title} (${m.certificate}) · ${m.genres.join(", ")} · ${m.languages.join(", ")}. ${d.synopsis.slice(0, 140)}`;
  return {
    title: `${m.title} — Book tickets`,
    description,
    openGraph: { title: m.title, description, images: [m.backdropUrl ?? m.posterUrl ?? "/media/og-default.svg"], type: "video.movie" },
    twitter: { card: "summary_large_image", title: m.title, description },
  };
}

export default async function MoviePage({ params }: Props) {
  const { id } = await params;
  const initial = await serverGet<MovieDetail>(`/movies/${id}`, 120);
  const jsonLd = initial && {
    "@context": "https://schema.org",
    "@type": "Movie",
    name: initial.movie.title,
    description: initial.synopsis,
    image: initial.movie.posterUrl,
    datePublished: initial.movie.releaseDate,
    contentRating: initial.movie.certificate,
    genre: initial.movie.genres,
    director: initial.directors.map((d) => ({ "@type": "Person", name: d.name })),
    actor: initial.cast.slice(0, 5).map((c) => ({ "@type": "Person", name: c.name })),
    ...(initial.ratingSummary.totalVotes > 0 && {
      aggregateRating: { "@type": "AggregateRating", ratingValue: initial.ratingSummary.average, ratingCount: initial.ratingSummary.totalVotes, bestRating: 5 },
    }),
  };
  return (
    <>
      {jsonLd && <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replace(/</g, "\\u003c") }} />}
      <MovieDetailView id={Number(id)} initial={initial ?? undefined} />
    </>
  );
}
