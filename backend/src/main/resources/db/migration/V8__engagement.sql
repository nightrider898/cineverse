CREATE TABLE favorites (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type  VARCHAR(10) NOT NULL, -- MOVIE, CINEMA, EVENT
    target_id    BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, target_type, target_id)
);

CREATE TABLE watchlists (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    movie_id      BIGINT      NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    remind_me     BOOLEAN     NOT NULL DEFAULT TRUE,
    reminded_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, movie_id)
);

CREATE TABLE reviews (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    movie_id     BIGINT      NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    booking_id   BIGINT REFERENCES bookings(id),
    rating       SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title        VARCHAR(120),
    body         TEXT,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, movie_id)
);
CREATE INDEX ix_reviews_movie_status ON reviews(movie_id, status, created_at DESC);

-- Backend-maintained aggregate, recomputed from approved reviews
CREATE TABLE ratings (
    movie_id       BIGINT PRIMARY KEY REFERENCES movies(id) ON DELETE CASCADE,
    average        NUMERIC(3,2) NOT NULL DEFAULT 0,
    total_votes    INT          NOT NULL DEFAULT 0,
    count_1        INT NOT NULL DEFAULT 0,
    count_2        INT NOT NULL DEFAULT 0,
    count_3        INT NOT NULL DEFAULT 0,
    count_4        INT NOT NULL DEFAULT 0,
    count_5        INT NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(30)  NOT NULL, -- BOOKING_CONFIRMED, PAYMENT_SUCCESS, PAYMENT_FAILED, SHOW_REMINDER, CANCELLATION, REFUND, PROMOTION
    title       VARCHAR(160) NOT NULL,
    body        VARCHAR(500) NOT NULL,
    link        VARCHAR(300),
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user ON notifications(user_id, created_at DESC);
CREATE INDEX ix_notifications_user_unread ON notifications(user_id) WHERE read_at IS NULL;
