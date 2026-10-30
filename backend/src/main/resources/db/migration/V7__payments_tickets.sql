CREATE TABLE payments (
    id                   BIGSERIAL PRIMARY KEY,
    public_id            UUID          NOT NULL UNIQUE,
    booking_id           BIGINT        NOT NULL REFERENCES bookings(id),
    user_id              BIGINT        NOT NULL REFERENCES users(id),
    provider             VARCHAR(20)   NOT NULL, -- MOCK, STRIPE, RAZORPAY, PHONEPE, VENUE
    method               VARCHAR(20)   NOT NULL, -- CARD, UPI, GPAY, PHONEPE, NETBANKING, WALLET, PAY_AT_VENUE
    status               VARCHAR(20)   NOT NULL, -- INITIATED, PENDING, AUTHORIZED, SUCCESS, FAILED, CANCELLED, REFUNDED, PARTIALLY_REFUNDED
    amount               NUMERIC(10,2) NOT NULL,
    currency             VARCHAR(3)    NOT NULL DEFAULT 'INR',
    provider_order_id    VARCHAR(120),
    provider_payment_id  VARCHAR(120),
    failure_reason       VARCHAR(300),
    refunded_amount      NUMERIC(10,2) NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at         TIMESTAMPTZ,
    version              BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_payments_booking ON payments(booking_id);
CREATE UNIQUE INDEX ux_payments_provider_order ON payments(provider, provider_order_id) WHERE provider_order_id IS NOT NULL;
CREATE INDEX ix_payments_provider_payment ON payments(provider_payment_id);
CREATE INDEX ix_payments_created ON payments(created_at);

-- Append-only log of every state change / provider interaction for a payment
CREATE TABLE payment_transactions (
    id             BIGSERIAL PRIMARY KEY,
    payment_id     BIGINT       NOT NULL REFERENCES payments(id),
    type           VARCHAR(30)  NOT NULL, -- ORDER_CREATED, WEBHOOK, VERIFY, STATUS_CHECK, REFUND
    from_status    VARCHAR(20),
    to_status      VARCHAR(20),
    provider_ref   VARCHAR(120),
    payload        JSONB,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_payment_transactions_payment ON payment_transactions(payment_id);

-- Deduplicates provider webhooks (idempotent processing)
CREATE TABLE webhook_events (
    id            BIGSERIAL PRIMARY KEY,
    provider      VARCHAR(20)  NOT NULL,
    event_id      VARCHAR(160) NOT NULL,
    event_type    VARCHAR(80),
    received_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at  TIMESTAMPTZ,
    outcome       VARCHAR(40),
    UNIQUE (provider, event_id)
);

CREATE TABLE refunds (
    id                 BIGSERIAL PRIMARY KEY,
    payment_id         BIGINT        NOT NULL UNIQUE REFERENCES payments(id), -- one refund per captured payment
    booking_id         BIGINT        NOT NULL REFERENCES bookings(id),
    amount             NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    status             VARCHAR(20)   NOT NULL, -- REQUESTED, PROCESSING, COMPLETED, FAILED
    reason             VARCHAR(200),
    provider_refund_id VARCHAR(120),
    failure_reason     VARCHAR(300),
    attempts           INT           NOT NULL DEFAULT 0,
    requested_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    processed_at       TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_refunds_status ON refunds(status);
CREATE INDEX ix_refunds_booking ON refunds(booking_id);
CREATE INDEX ix_refunds_provider_ref ON refunds(provider_refund_id);

CREATE TABLE tickets (
    id           BIGSERIAL PRIMARY KEY,
    booking_id   BIGINT       NOT NULL UNIQUE REFERENCES bookings(id),
    ticket_code  VARCHAR(40)  NOT NULL UNIQUE,
    status       VARCHAR(20)  NOT NULL DEFAULT 'VALID', -- VALID, USED, CANCELLED, EXPIRED
    issued_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    used_at      TIMESTAMPTZ,
    validated_by BIGINT REFERENCES users(id)
);

CREATE TABLE ticket_qr_codes (
    id          BIGSERIAL PRIMARY KEY,
    ticket_id   BIGINT       NOT NULL UNIQUE REFERENCES tickets(id) ON DELETE CASCADE,
    qr_token    VARCHAR(120) NOT NULL UNIQUE, -- opaque, signed identifier; contains no payment data
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
