CREATE TABLE partners (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(160) NOT NULL,
    contact_email VARCHAR(160),
    user_id       BIGINT REFERENCES users(id) ON DELETE SET NULL,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cinemas (
    id                   BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(160) NOT NULL,
    slug                 VARCHAR(180) NOT NULL UNIQUE,
    city_id              BIGINT       NOT NULL REFERENCES cities(id),
    location_id          BIGINT REFERENCES locations(id),
    address              VARCHAR(300) NOT NULL,
    latitude             NUMERIC(9,6),
    longitude            NUMERIC(9,6),
    facilities           VARCHAR(300) NOT NULL DEFAULT '', -- comma separated: PARKING,FOOD_COURT,WHEELCHAIR,M_TICKET,RECLINER
    allow_pay_at_venue   BOOLEAN      NOT NULL DEFAULT FALSE,
    cancellation_allowed BOOLEAN      NOT NULL DEFAULT TRUE,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_cinemas_city ON cinemas(city_id) WHERE active;
CREATE INDEX ix_cinemas_name_trgm ON cinemas USING gin (name gin_trgm_ops);

CREATE TABLE cinema_partners (
    cinema_id  BIGINT NOT NULL REFERENCES cinemas(id) ON DELETE CASCADE,
    partner_id BIGINT NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    PRIMARY KEY (cinema_id, partner_id)
);

CREATE TABLE seat_types (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(20)  NOT NULL UNIQUE, -- SILVER, GOLD, PREMIUM, VIP, RECLINER
    name         VARCHAR(40)  NOT NULL,
    price_delta  NUMERIC(10,2) NOT NULL DEFAULT 0, -- added to show base price
    sort_order   INT          NOT NULL DEFAULT 0
);

CREATE TABLE cinema_screens (
    id           BIGSERIAL PRIMARY KEY,
    cinema_id    BIGINT      NOT NULL REFERENCES cinemas(id) ON DELETE CASCADE,
    name         VARCHAR(60) NOT NULL,
    screen_type  VARCHAR(30) NOT NULL DEFAULT 'STANDARD', -- STANDARD, IMAX, 4DX, DOLBY
    formats      VARCHAR(120) NOT NULL DEFAULT '2D',
    capacity     INT         NOT NULL DEFAULT 0,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (cinema_id, name)
);

CREATE TABLE screen_seats (
    id           BIGSERIAL PRIMARY KEY,
    screen_id    BIGINT      NOT NULL REFERENCES cinema_screens(id) ON DELETE CASCADE,
    row_label    VARCHAR(4)  NOT NULL,
    seat_number  INT         NOT NULL,
    label        VARCHAR(10) NOT NULL,
    row_index    INT         NOT NULL,
    col_index    INT         NOT NULL, -- gaps in col_index render aisles
    seat_type_id BIGINT      NOT NULL REFERENCES seat_types(id),
    disabled     BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (screen_id, label),
    UNIQUE (screen_id, row_index, col_index)
);

CREATE TABLE venues (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(160) NOT NULL,
    slug         VARCHAR(180) NOT NULL UNIQUE,
    city_id      BIGINT       NOT NULL REFERENCES cities(id),
    address      VARCHAR(300) NOT NULL,
    latitude     NUMERIC(9,6),
    longitude    NUMERIC(9,6),
    capacity     INT,
    facilities   VARCHAR(300) NOT NULL DEFAULT '',
    image_url    VARCHAR(500),
    allow_pay_at_venue BOOLEAN NOT NULL DEFAULT FALSE,
    active       BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_venues_city ON venues(city_id);
CREATE INDEX ix_venues_name_trgm ON venues USING gin (name gin_trgm_ops);
