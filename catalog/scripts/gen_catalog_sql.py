"""Generate V101__real_catalog.sql from movies.json + cinemas.json.

Usage (from the repo root):
  python3 catalog/scripts/gen_catalog_sql.py catalog backend/src/main/resources/db/migration/V101__real_catalog.sql
Never edit an already-applied migration in place: write new data to a new version number instead.
"""
import json
import re
import sys
import unicodedata
from pathlib import Path

DATA = Path(sys.argv[1])
OUT = Path(sys.argv[2])

movies = json.loads((DATA / "movies.json").read_text(encoding="utf-8"))
cinemas = json.loads((DATA / "cinemas.json").read_text(encoding="utf-8"))

DEMO_MOVIES = ["galactic-dawn", "midnight-protocol", "veera", "the-last-signal", "neon-city", "monsoon-letters",
               "kaveri-express", "iron-tide", "whispers-of-hampi", "orbit-kids"]
DEMO_CINEMAS = ["cineverse-galleria-hyd", "purple-square-hyd", "galaxy-mall-hyd", "metroplex-hyd", "cineverse-orion-blr",
                "metroplex-whitefield-blr", "purple-square-chn", "galaxy-andheri-mum", "cineverse-saket-del",
                "metroplex-hinjewadi-pun", "purple-square-kol", "galaxy-talkies-vja", "cineverse-seaside-vzg"]
CITY_CODE = {"hyderabad": "hyd", "bengaluru": "blr", "chennai": "chn", "mumbai": "mum", "delhi": "del", "pune": "pun",
             "kolkata": "kol", "vijayawada": "vja", "visakhapatnam": "vzg"}

# Certificates in the pasted lists are a mix of Indian CBFC ratings and UK-style ratings (IMDb locale).
# Map everything to CBFC categories; unknown -> 'UA' until confirmed.
CERT = {"U": "U", "PG": "U", "UA": "UA", "UA 7+": "UA7+", "UA 13+": "UA13+", "UA 16+": "UA16+", "A": "A",
        "12": "UA13+", "12A": "UA13+", "PG-13": "UA13+", "15": "UA16+", "18": "A", "X": "A", "AA": "A",
        "Not Rated": "UA"}
GENRE_SLUG = {"Horror": "horror", "Thriller": "thriller", "Action": "action", "Romance": "romance", "Comedy": "comedy",
              "Family": "family", "Sports": "sports", "Historical": "historical", "Mythology": "mythology",
              "Drama": "drama"}


def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "TRUE" if v else "FALSE"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


def slugify(s):
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn").lower()
    return re.sub(r"(^-|-$)", "", re.sub(r"[^a-z0-9]+", "-", s))[:150] or "movie"


# ---- movies
used = set(DEMO_MOVIES)
rows, langs, genres, directors, cast = [], [], [], [], []
# Featured hero carousel: the two most-voted current releases in each Indian language.
featured = set()
for lang in ("Telugu", "Hindi", "Tamil", "Malayalam", "Kannada"):
    pool = sorted([m for m in movies if m["status"] == "NOW_SHOWING" and lang in m["languages"]], key=lambda m: -(m["votes"] or 0))
    featured |= {id(m) for m in pool[:2]}
for m in movies:
    base = slugify(m["title"]) + (f"-{m['year']}" if m["year"] else "")
    slug, i = base, 2
    while slug in used:
        slug, i = f"{base}-{i}", i + 1
    used.add(slug)
    status = m["status"]
    if m["releaseDate"]:
        release, est = m["releaseDate"], False
    elif m["year"]:
        release, est = (f"{m['year']}-01-01" if status != "COMING_SOON" else f"{m['year']}-12-31"), True
    else:
        release, est = "2027-12-31", True
    if status == "CLASSIC":
        synopsis = m["synopsis"] or "A celebrated classic, back on the big screen for a limited re-release."
    else:
        synopsis = m["synopsis"] or "Story details will be announced soon."
    runtime = m["runtimeMin"] or 150
    cert = CERT.get(m["certificate"] or "", "UA")
    formats = "2D" if status == "CLASSIC" else "2D,DOLBY_ATMOS"
    rows.append("(" + ", ".join([
        q(m["title"]), q(slug), q(synopsis), q(runtime), q(cert), q(release), q(formats), q(status),
        q(id(m) in featured), q(m["rating"]), q(m["votes"]), q("CLASSICS" if status == "CLASSIC" else None),
        q(m["runtimeMin"] is None), q(est), q(m.get("top250Rank"))]) + ")")
    for l in m["languages"]:
        langs.append(f"({q(slug)}, {q(l)})")
    for g in m["genres"]:
        genres.append(f"({q(slug)}, {q(GENRE_SLUG[g])})")
    for d in m["directors"]:
        directors.append(f"({q(slug)}, {q(d)})")
    for k, a in enumerate(m["cast"]):
        cast.append(f"({q(slug)}, {q(a)}, {k})")

# ---- cinemas
cin_rows = []
for c in cinemas:
    slug = slugify(c["name"]) + "-" + CITY_CODE[c["city"]]
    single = c["chain"] in ("Single screen", "Government")
    if single:
        n = 1
        fac = "M_TICKET,WHEELCHAIR"
    elif c["premium"]:
        n = min(c["screens"], 4)
        fac = "PARKING,FOOD_COURT,WHEELCHAIR,M_TICKET,RECLINER"
    else:
        n = min(c["screens"], 3)
        fac = "PARKING,FOOD_COURT,WHEELCHAIR,M_TICKET"
    cin_rows.append("(" + ", ".join([q(c["name"]), q(slug), q(c["city"]), q(c["area"]), q(c["address"]),
                                     q(c["lat"]), q(c["lng"]), q(fac), q(single), q(c["chain"]), q(n)]) + ")")

sql = f"""-- Real Indian cinemas and the 2026 regional releases + all-time classics supplied by the product owner.
-- Generated by scripts/gen_catalog_sql.py from catalog/movies.json and catalog/cinemas.json. Insert-only and
-- idempotent on slug, so admin edits are never overwritten. Posters, backdrops, trailers and missing details
-- are filled in at runtime by the TMDB media sync (see MediaSyncService) once TMDB_API_KEY is configured.
-- Cinema names, areas and approximate coordinates are real; screen counts, layouts and formats are illustrative.

-- ---------------------------------------------------------------- schema
ALTER TABLE movies
    ADD COLUMN tmdb_id                INT,
    ADD COLUMN imdb_rating            NUMERIC(3,1),
    ADD COLUMN imdb_votes             INT,
    ADD COLUMN collection             VARCHAR(30),           -- e.g. CLASSICS
    ADD COLUMN top250_rank            INT,
    ADD COLUMN catalog_source         VARCHAR(20) NOT NULL DEFAULT 'ADMIN', -- ADMIN, IMPORT, DEMO
    ADD COLUMN runtime_estimated      BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN release_date_estimated BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN media_locked           BOOLEAN     NOT NULL DEFAULT FALSE, -- poster/trailer set by an admin: sync never overwrites
    ADD COLUMN media_synced_at        TIMESTAMPTZ,
    ADD COLUMN media_sync_note        VARCHAR(200);
CREATE INDEX ix_movies_tmdb ON movies(tmdb_id);
CREATE INDEX ix_movies_collection ON movies(collection) WHERE collection IS NOT NULL;

ALTER TABLE cinemas
    ADD COLUMN chain          VARCHAR(60),
    ADD COLUMN catalog_source VARCHAR(20) NOT NULL DEFAULT 'ADMIN';

-- The fictional launch catalog (dev profile only) is retired by the dev seeder once this catalog exists.
UPDATE movies  SET catalog_source = 'DEMO' WHERE slug IN ({", ".join(q(s) for s in DEMO_MOVIES)});
UPDATE cinemas SET catalog_source = 'DEMO' WHERE slug IN ({", ".join(q(s) for s in DEMO_CINEMAS)});

INSERT INTO genres (name, slug) VALUES ('Sports', 'sports'), ('Mythology', 'mythology'), ('Biography', 'biography')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------- movies
CREATE TEMP TABLE import_movies (
    title TEXT, slug TEXT, synopsis TEXT, duration_min INT, certificate TEXT, release_date DATE, formats TEXT,
    status TEXT, featured BOOLEAN, imdb_rating NUMERIC(3,1), imdb_votes INT, collection TEXT,
    runtime_estimated BOOLEAN, release_date_estimated BOOLEAN, top250_rank INT
) ON COMMIT DROP;
INSERT INTO import_movies VALUES
{",\n".join(rows)};

INSERT INTO movies (title, slug, synopsis, duration_min, certificate, release_date, formats, status, featured,
                    imdb_rating, imdb_votes, collection, runtime_estimated, release_date_estimated, top250_rank, catalog_source)
SELECT title, slug, synopsis, duration_min, certificate, release_date, formats, status, featured,
       imdb_rating, imdb_votes, collection, runtime_estimated, release_date_estimated, top250_rank, 'IMPORT'
FROM import_movies
ON CONFLICT (slug) DO NOTHING;

INSERT INTO ratings (movie_id) SELECT m.id FROM movies m WHERE NOT EXISTS (SELECT 1 FROM ratings r WHERE r.movie_id = m.id);

CREATE TEMP TABLE import_languages (slug TEXT, language TEXT) ON COMMIT DROP;
INSERT INTO import_languages VALUES
{",\n".join(langs)};
INSERT INTO movie_languages (movie_id, language)
SELECT m.id, x.language FROM import_languages x JOIN movies m ON m.slug = x.slug
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE import_genres (slug TEXT, genre TEXT) ON COMMIT DROP;
INSERT INTO import_genres VALUES
{",\n".join(genres)};
INSERT INTO movie_genres (movie_id, genre_id)
SELECT m.id, g.id FROM import_genres x JOIN movies m ON m.slug = x.slug JOIN genres g ON g.slug = x.genre
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE import_directors (slug TEXT, name TEXT) ON COMMIT DROP;
INSERT INTO import_directors VALUES
{",\n".join(directors)};
INSERT INTO directors (name)
SELECT DISTINCT x.name FROM import_directors x WHERE NOT EXISTS (SELECT 1 FROM directors d WHERE d.name = x.name);
INSERT INTO movie_directors (movie_id, director_id)
SELECT DISTINCT m.id, (SELECT min(d.id) FROM directors d WHERE d.name = x.name)
FROM import_directors x JOIN movies m ON m.slug = x.slug
WHERE m.catalog_source = 'IMPORT'
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE import_cast (slug TEXT, name TEXT, billing INT) ON COMMIT DROP;
INSERT INTO import_cast VALUES
{",\n".join(cast)};
INSERT INTO actors (name)
SELECT DISTINCT x.name FROM import_cast x WHERE NOT EXISTS (SELECT 1 FROM actors a WHERE a.name = x.name);
INSERT INTO movie_cast (movie_id, actor_id, billing_order)
SELECT m.id, (SELECT min(a.id) FROM actors a WHERE a.name = x.name), x.billing
FROM import_cast x JOIN movies m ON m.slug = x.slug
WHERE m.catalog_source = 'IMPORT'
  AND NOT EXISTS (SELECT 1 FROM movie_cast mc WHERE mc.movie_id = m.id);

-- ---------------------------------------------------------------- cinemas
CREATE TEMP TABLE import_cinemas (
    name TEXT, slug TEXT, city TEXT, area TEXT, address TEXT, lat NUMERIC(9,6), lng NUMERIC(9,6), facilities TEXT,
    pay_at_venue BOOLEAN, chain TEXT, screens INT
) ON COMMIT DROP;
INSERT INTO import_cinemas VALUES
{",\n".join(cin_rows)};

INSERT INTO locations (city_id, name)
SELECT DISTINCT c.id, x.area FROM import_cinemas x JOIN cities c ON c.slug = x.city
ON CONFLICT (city_id, name) DO NOTHING;

INSERT INTO cinemas (name, slug, city_id, location_id, address, latitude, longitude, facilities, allow_pay_at_venue, chain, catalog_source)
SELECT x.name, x.slug, c.id, l.id, x.address, x.lat, x.lng, x.facilities, x.pay_at_venue, x.chain, 'IMPORT'
FROM import_cinemas x JOIN cities c ON c.slug = x.city LEFT JOIN locations l ON l.city_id = c.id AND l.name = x.area
ON CONFLICT (slug) DO NOTHING;

-- Screens: 1 for single screens, up to 3 for multiplexes, up to 4 for flagships. Even-numbered screens are
-- Dolby Atmos auditoriums with a recliner row.
INSERT INTO cinema_screens (cinema_id, name, screen_type, formats)
SELECT ci.id, 'Screen ' || n,
       CASE WHEN n % 2 = 0 THEN 'DOLBY' ELSE 'STANDARD' END,
       CASE WHEN n % 2 = 0 THEN '2D,3D,DOLBY_ATMOS' WHEN n = 1 THEN '2D,3D' ELSE '2D' END
FROM import_cinemas x JOIN cinemas ci ON ci.slug = x.slug
CROSS JOIN LATERAL generate_series(1, x.screens) n
WHERE ci.catalog_source = 'IMPORT'
ON CONFLICT (cinema_id, name) DO NOTHING;

-- Seat layout (same grammar as the demo screens): 10 rows A-J x 14 seats, aisles after seats 4 and 10.
-- Row A: VIP recliners on Dolby screens, otherwise PREMIUM; B-C PREMIUM; D-G GOLD; H-J SILVER.
DO $$
DECLARE
  scr RECORD;
  r INT; s INT; col INT; seat_type BIGINT; rl TEXT;
BEGIN
  FOR scr IN SELECT cs.id, cs.screen_type FROM cinema_screens cs
             WHERE NOT EXISTS (SELECT 1 FROM screen_seats ss WHERE ss.screen_id = cs.id) LOOP
    FOR r IN 1..10 LOOP
      rl := chr(64 + r);
      IF r = 1 AND scr.screen_type IN ('IMAX', '4DX', 'DOLBY') THEN
        SELECT id INTO seat_type FROM seat_types WHERE code = 'VIP';
      ELSIF r <= 3 THEN
        SELECT id INTO seat_type FROM seat_types WHERE code = 'PREMIUM';
      ELSIF r <= 7 THEN
        SELECT id INTO seat_type FROM seat_types WHERE code = 'GOLD';
      ELSE
        SELECT id INTO seat_type FROM seat_types WHERE code = 'SILVER';
      END IF;
      FOR s IN 1..14 LOOP
        col := s + CASE WHEN s > 10 THEN 2 WHEN s > 4 THEN 1 ELSE 0 END;
        INSERT INTO screen_seats (screen_id, row_label, seat_number, label, row_index, col_index, seat_type_id, disabled)
        VALUES (scr.id, rl, s, rl || s, r, col, seat_type, (r = 10 AND s IN (1, 14)));
      END LOOP;
    END LOOP;
    UPDATE cinema_screens SET capacity = (SELECT count(*) FROM screen_seats WHERE screen_id = scr.id AND NOT disabled) WHERE id = scr.id;
  END LOOP;
END $$;
"""
OUT.write_text(sql, encoding="utf-8")
print(f"{len(rows)} movies, {len(cin_rows)} cinemas -> {OUT} ({len(sql)//1024} KB)")
