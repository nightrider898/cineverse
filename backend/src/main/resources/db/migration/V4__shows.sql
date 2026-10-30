CREATE TABLE shows (
    id           BIGSERIAL PRIMARY KEY,
    movie_id     BIGINT        NOT NULL REFERENCES movies(id),
    cinema_id    BIGINT        NOT NULL REFERENCES cinemas(id),
    screen_id    BIGINT        NOT NULL REFERENCES cinema_screens(id),
    show_date    DATE          NOT NULL,
    start_time   TIMESTAMPTZ   NOT NULL,
    end_time     TIMESTAMPTZ   NOT NULL,
    format       VARCHAR(20)   NOT NULL DEFAULT '2D',
    language     VARCHAR(30)   NOT NULL,
    base_price   NUMERIC(10,2) NOT NULL CHECK (base_price >= 0),
    status       VARCHAR(20)   NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED, CANCELLED, COMPLETED
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (end_time > start_time),
    -- Database-level guarantee: no two active shows overlap on the same screen.
    CONSTRAINT ex_shows_no_overlap EXCLUDE USING gist (
        screen_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status <> 'CANCELLED')
);
CREATE INDEX ix_shows_movie_date ON shows(movie_id, show_date);
CREATE INDEX ix_shows_cinema_date ON shows(cinema_id, show_date);
CREATE INDEX ix_shows_date ON shows(show_date);

CREATE TABLE seat_locks (
    id           UUID PRIMARY KEY,
    show_id      BIGINT       NOT NULL REFERENCES shows(id),
    user_id      BIGINT       NOT NULL REFERENCES users(id),
    seat_labels  VARCHAR(400) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, EXPIRED, RELEASED, CONSUMED
    expires_at   TIMESTAMPTZ  NOT NULL,
    hold_type    VARCHAR(20)  NOT NULL DEFAULT 'CHECKOUT', -- CHECKOUT, PAY_AT_VENUE
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    released_at  TIMESTAMPTZ
);
CREATE INDEX ix_seat_locks_active_expiry ON seat_locks(expires_at) WHERE status = 'ACTIVE';
CREATE INDEX ix_seat_locks_user_show ON seat_locks(user_id, show_id) WHERE status = 'ACTIVE';

CREATE TABLE show_seats (
    id             BIGSERIAL PRIMARY KEY,
    show_id        BIGINT        NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    screen_seat_id BIGINT        NOT NULL REFERENCES screen_seats(id),
    label          VARCHAR(10)   NOT NULL,
    seat_type_code VARCHAR(20)   NOT NULL,
    price          NUMERIC(10,2) NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE, LOCKED, BOOKED, BLOCKED
    lock_id        UUID REFERENCES seat_locks(id),
    version        BIGINT        NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (show_id, label)
);
CREATE INDEX ix_show_seats_show_status ON show_seats(show_id, status);
CREATE INDEX ix_show_seats_lock ON show_seats(lock_id) WHERE lock_id IS NOT NULL;
