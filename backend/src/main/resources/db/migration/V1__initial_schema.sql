-- CineVerse: identity, geography, audit and infrastructure tables
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE cities (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL UNIQUE,
    state       VARCHAR(80)  NOT NULL,
    slug        VARCHAR(80)  NOT NULL UNIQUE,
    latitude    NUMERIC(9,6),
    longitude   NUMERIC(9,6),
    is_popular  BOOLEAN      NOT NULL DEFAULT FALSE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE locations (
    id          BIGSERIAL PRIMARY KEY,
    city_id     BIGINT       NOT NULL REFERENCES cities(id),
    name        VARCHAR(120) NOT NULL,
    pincode     VARCHAR(12),
    UNIQUE (city_id, name)
);

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(40) NOT NULL UNIQUE,
    description VARCHAR(200)
);

CREATE TABLE permissions (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(200)
);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(160) NOT NULL,
    password_hash       VARCHAR(100) NOT NULL,
    full_name           VARCHAR(120) NOT NULL,
    phone               VARCHAR(20),
    avatar_url          VARCHAR(500),
    preferred_city_id   BIGINT REFERENCES cities(id),
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Admin profile metadata for staff accounts (the account itself lives in users)
CREATE TABLE admin_users (
    user_id     BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(80),
    mfa_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  VARCHAR(64),
    user_agent   VARCHAR(300),
    ip_address   VARCHAR(64),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens(user_id);

CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action       VARCHAR(80)  NOT NULL,
    resource     VARCHAR(80)  NOT NULL,
    resource_id  VARCHAR(80),
    ip_address   VARCHAR(64),
    request_id   VARCHAR(64),
    metadata     JSONB,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_logs_created ON audit_logs(created_at DESC);
CREATE INDEX ix_audit_logs_resource ON audit_logs(resource, resource_id);

-- Stored responses for Idempotency-Key protected operations
CREATE TABLE idempotency_records (
    id              BIGSERIAL PRIMARY KEY,
    idem_key        VARCHAR(100) NOT NULL,
    user_id         BIGINT,
    scope           VARCHAR(60)  NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    status          VARCHAR(20)  NOT NULL, -- IN_PROGRESS, COMPLETED
    response_status INT,
    response_body   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_idempotency ON idempotency_records(scope, COALESCE(user_id, 0), idem_key);

CREATE TABLE search_history (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id) ON DELETE CASCADE,
    query       VARCHAR(120) NOT NULL,
    city_id     BIGINT REFERENCES cities(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_search_history_user ON search_history(user_id, created_at DESC);
CREATE INDEX ix_search_history_created ON search_history(created_at DESC);
