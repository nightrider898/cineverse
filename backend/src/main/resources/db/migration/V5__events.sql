CREATE TABLE event_categories (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(30) NOT NULL UNIQUE, -- CONCERT, COMEDY, THEATRE, FESTIVAL, WORKSHOP, CRICKET, FOOTBALL, KABADDI, BOWLING...
    name       VARCHAR(60) NOT NULL,
    vertical   VARCHAR(20) NOT NULL,        -- EVENT, SPORTS, PLAY, ACTIVITY
    icon       VARCHAR(40)
);

CREATE TABLE events (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(160) NOT NULL,
    slug            VARCHAR(180) NOT NULL UNIQUE,
    description     TEXT         NOT NULL,
    category_id     BIGINT       NOT NULL REFERENCES event_categories(id),
    vertical        VARCHAR(20)  NOT NULL, -- denormalised from category for fast filtering
    venue_id        BIGINT       NOT NULL REFERENCES venues(id),
    city_id         BIGINT       NOT NULL REFERENCES cities(id),
    artist          VARCHAR(160),
    team_home       VARCHAR(80),
    team_away       VARCHAR(80),
    languages       VARCHAR(120),
    duration_min    INT,
    age_restriction VARCHAR(20),  -- e.g. 18+, All ages
    poster_url      VARCHAR(500),
    banner_url      VARCHAR(500),
    featured        BOOLEAN      NOT NULL DEFAULT FALSE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED', -- DRAFT, PUBLISHED, CANCELLED
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_events_city_vertical ON events(city_id, vertical) WHERE status = 'PUBLISHED';
CREATE INDEX ix_events_title_trgm ON events USING gin (title gin_trgm_ops);

CREATE TABLE event_partners (
    event_id   BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    partner_id BIGINT NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, partner_id)
);

-- One row per performance / match / activity time slot.
CREATE TABLE event_sessions (
    id          BIGSERIAL PRIMARY KEY,
    event_id    BIGINT      NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    start_time  TIMESTAMPTZ NOT NULL,
    end_time    TIMESTAMPTZ NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    CHECK (end_time > start_time)
);
CREATE INDEX ix_event_sessions_event_start ON event_sessions(event_id, start_time);

-- Quantity-based inventory (non-seated events)
CREATE TABLE event_ticket_categories (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT        NOT NULL REFERENCES event_sessions(id) ON DELETE CASCADE,
    name        VARCHAR(60)   NOT NULL,
    description VARCHAR(200),
    price       NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    capacity    INT           NOT NULL CHECK (capacity >= 0),
    sold        INT           NOT NULL DEFAULT 0 CHECK (sold >= 0),
    held        INT           NOT NULL DEFAULT 0 CHECK (held >= 0),
    max_per_booking INT       NOT NULL DEFAULT 10,
    sort_order  INT           NOT NULL DEFAULT 0,
    CHECK (sold + held <= capacity),
    UNIQUE (session_id, name)
);
