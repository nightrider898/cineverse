CREATE TABLE genres (
    id    BIGSERIAL PRIMARY KEY,
    name  VARCHAR(40) NOT NULL UNIQUE,
    slug  VARCHAR(40) NOT NULL UNIQUE
);

CREATE TABLE movies (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(160) NOT NULL,
    slug            VARCHAR(180) NOT NULL UNIQUE,
    tagline         VARCHAR(240),
    synopsis        TEXT         NOT NULL,
    duration_min    INT          NOT NULL CHECK (duration_min > 0),
    certificate     VARCHAR(10)  NOT NULL, -- U, UA, UA13+, A
    release_date    DATE         NOT NULL,
    poster_url      VARCHAR(500),
    backdrop_url    VARCHAR(500),
    trailer_url     VARCHAR(500),
    formats         VARCHAR(120) NOT NULL DEFAULT '2D', -- comma separated: 2D,3D,IMAX,4DX,DOLBY_ATMOS
    status          VARCHAR(20)  NOT NULL DEFAULT 'NOW_SHOWING', -- NOW_SHOWING, COMING_SOON, ARCHIVED
    featured        BOOLEAN      NOT NULL DEFAULT FALSE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_movies_title_trgm ON movies USING gin (title gin_trgm_ops);
CREATE INDEX ix_movies_status_release ON movies(status, release_date);

CREATE TABLE movie_genres (
    movie_id BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    genre_id BIGINT NOT NULL REFERENCES genres(id),
    PRIMARY KEY (movie_id, genre_id)
);
CREATE INDEX ix_movie_genres_genre ON movie_genres(genre_id);

CREATE TABLE movie_languages (
    movie_id BIGINT      NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    language VARCHAR(30) NOT NULL,
    PRIMARY KEY (movie_id, language)
);
CREATE INDEX ix_movie_languages_lang ON movie_languages(language);

CREATE TABLE movie_images (
    id        BIGSERIAL PRIMARY KEY,
    movie_id  BIGINT       NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    url       VARCHAR(500) NOT NULL,
    caption   VARCHAR(200),
    sort_order INT         NOT NULL DEFAULT 0
);

CREATE TABLE actors (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    photo_url  VARCHAR(500),
    bio        TEXT
);
CREATE INDEX ix_actors_name_trgm ON actors USING gin (name gin_trgm_ops);

CREATE TABLE directors (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    photo_url  VARCHAR(500)
);

CREATE TABLE movie_directors (
    movie_id    BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    director_id BIGINT NOT NULL REFERENCES directors(id),
    PRIMARY KEY (movie_id, director_id)
);

-- Cast and crew. Crew rows (music, cinematography...) use crew_role with no actor character.
CREATE TABLE movie_cast (
    id             BIGSERIAL PRIMARY KEY,
    movie_id       BIGINT       NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    actor_id       BIGINT       NOT NULL REFERENCES actors(id),
    character_name VARCHAR(120),
    crew_role      VARCHAR(60),
    billing_order  INT          NOT NULL DEFAULT 0
);
CREATE INDEX ix_movie_cast_movie ON movie_cast(movie_id);
