CREATE TABLE food_items (
    id           BIGSERIAL PRIMARY KEY,
    cinema_id    BIGINT REFERENCES cinemas(id) ON DELETE CASCADE, -- NULL = available at all cinemas
    name         VARCHAR(80)   NOT NULL,
    description  VARCHAR(200),
    category     VARCHAR(30)   NOT NULL, -- POPCORN, BEVERAGE, SNACK, COMBO
    price        NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    image_url    VARCHAR(500),
    veg          BOOLEAN       NOT NULL DEFAULT TRUE,
    available    BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order   INT           NOT NULL DEFAULT 0
);
CREATE INDEX ix_food_items_cinema ON food_items(cinema_id);

CREATE TABLE coupons (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(30)   NOT NULL,
    description         VARCHAR(200)  NOT NULL,
    discount_type       VARCHAR(10)   NOT NULL, -- PERCENT, FLAT
    discount_value      NUMERIC(10,2) NOT NULL CHECK (discount_value > 0),
    max_discount        NUMERIC(10,2),
    min_order_amount    NUMERIC(10,2) NOT NULL DEFAULT 0,
    valid_from          TIMESTAMPTZ   NOT NULL,
    valid_to            TIMESTAMPTZ   NOT NULL,
    movie_id            BIGINT REFERENCES movies(id),
    city_id             BIGINT REFERENCES cities(id),
    cinema_id           BIGINT REFERENCES cinemas(id),
    payment_method      VARCHAR(20),  -- CARD, UPI, NETBANKING, WALLET...
    first_booking_only  BOOLEAN       NOT NULL DEFAULT FALSE,
    usage_limit         INT,          -- total redemptions
    per_user_limit      INT           NOT NULL DEFAULT 1,
    times_used          INT           NOT NULL DEFAULT 0,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_coupons_code ON coupons(upper(code));

CREATE TABLE offers (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(120) NOT NULL,
    description  VARCHAR(300) NOT NULL,
    offer_type   VARCHAR(20)  NOT NULL, -- WEEKEND, STUDENT, BANK, UPI, FIRST_BOOKING, MOVIE
    coupon_code  VARCHAR(30),
    city_id      BIGINT REFERENCES cities(id),   -- NULL = all cities
    movie_id     BIGINT REFERENCES movies(id),
    image_url    VARCHAR(500),
    terms        TEXT,
    valid_from   TIMESTAMPTZ  NOT NULL,
    valid_to     TIMESTAMPTZ  NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order   INT          NOT NULL DEFAULT 0
);

CREATE SEQUENCE booking_reference_seq START 100;

CREATE TABLE bookings (
    id                  BIGSERIAL PRIMARY KEY,
    reference           VARCHAR(30)   NOT NULL UNIQUE, -- CV-2026-000123
    user_id             BIGINT        NOT NULL REFERENCES users(id),
    booking_type        VARCHAR(10)   NOT NULL, -- MOVIE, EVENT
    show_id             BIGINT REFERENCES shows(id),
    event_session_id    BIGINT REFERENCES event_sessions(id),
    seat_lock_id        UUID REFERENCES seat_locks(id),
    status              VARCHAR(20)   NOT NULL, -- PENDING, PAYMENT_PENDING, CONFIRMED, CANCELLED, EXPIRED, REFUND_PENDING, REFUNDED
    -- price snapshot (never recomputed after confirmation)
    ticket_subtotal     NUMERIC(10,2) NOT NULL DEFAULT 0,
    food_subtotal       NUMERIC(10,2) NOT NULL DEFAULT 0,
    convenience_fee     NUMERIC(10,2) NOT NULL DEFAULT 0,
    tax                 NUMERIC(10,2) NOT NULL DEFAULT 0,
    discount            NUMERIC(10,2) NOT NULL DEFAULT 0,
    grand_total         NUMERIC(10,2) NOT NULL DEFAULT 0,
    coupon_id           BIGINT REFERENCES coupons(id),
    coupon_code         VARCHAR(30),
    payment_method      VARCHAR(20),
    pay_at_venue        BOOLEAN       NOT NULL DEFAULT FALSE,
    expires_at          TIMESTAMPTZ,
    confirmed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    cancel_reason       VARCHAR(200),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version             BIGINT        NOT NULL DEFAULT 0,
    CHECK ((booking_type = 'MOVIE' AND show_id IS NOT NULL) OR (booking_type = 'EVENT' AND event_session_id IS NOT NULL))
);
CREATE INDEX ix_bookings_user_created ON bookings(user_id, created_at DESC);
CREATE INDEX ix_bookings_status ON bookings(status);
CREATE INDEX ix_bookings_show ON bookings(show_id);
CREATE INDEX ix_bookings_pending_expiry ON bookings(expires_at) WHERE status IN ('PENDING', 'PAYMENT_PENDING');

CREATE TABLE booking_seats (
    id            BIGSERIAL PRIMARY KEY,
    booking_id    BIGINT        NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    show_seat_id  BIGINT        NOT NULL REFERENCES show_seats(id),
    seat_label    VARCHAR(10)   NOT NULL,
    seat_type     VARCHAR(20)   NOT NULL,
    price         NUMERIC(10,2) NOT NULL
);
CREATE INDEX ix_booking_seats_booking ON booking_seats(booking_id);

-- Quantity line items for event tickets
CREATE TABLE booking_items (
    id                  BIGSERIAL PRIMARY KEY,
    booking_id          BIGINT        NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    ticket_category_id  BIGINT        NOT NULL REFERENCES event_ticket_categories(id),
    category_name       VARCHAR(60)   NOT NULL,
    unit_price          NUMERIC(10,2) NOT NULL,
    quantity            INT           NOT NULL CHECK (quantity > 0)
);
CREATE INDEX ix_booking_items_booking ON booking_items(booking_id);

CREATE TABLE food_orders (
    id          BIGSERIAL PRIMARY KEY,
    booking_id  BIGINT        NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE CASCADE,
    subtotal    NUMERIC(10,2) NOT NULL DEFAULT 0,
    status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING', -- PENDING, CONFIRMED, CANCELLED, COLLECTED
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE food_order_items (
    id            BIGSERIAL PRIMARY KEY,
    food_order_id BIGINT        NOT NULL REFERENCES food_orders(id) ON DELETE CASCADE,
    food_item_id  BIGINT        NOT NULL REFERENCES food_items(id),
    item_name     VARCHAR(80)   NOT NULL,
    unit_price    NUMERIC(10,2) NOT NULL,
    quantity      INT           NOT NULL CHECK (quantity > 0 AND quantity <= 20)
);

CREATE TABLE coupon_usage (
    id          BIGSERIAL PRIMARY KEY,
    coupon_id   BIGINT        NOT NULL REFERENCES coupons(id),
    user_id     BIGINT        NOT NULL REFERENCES users(id),
    booking_id  BIGINT        NOT NULL UNIQUE REFERENCES bookings(id),
    discount    NUMERIC(10,2) NOT NULL,
    used_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_coupon_usage_user ON coupon_usage(coupon_id, user_id);
