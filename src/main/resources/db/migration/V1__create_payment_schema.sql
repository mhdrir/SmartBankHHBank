-- Flyway migration V1: versioned, immutable. Never edit an applied migration; add V3, V4, ...
-- Flyway records applied versions in the table flyway_schema_history.

CREATE TABLE accounts (
    iban        VARCHAR(34)    PRIMARY KEY,
    owner_name  VARCHAR(255)   NOT NULL,
    balance     NUMERIC(19, 2) NOT NULL CHECK (balance >= 0),   -- DB enforces the rule too (defence in depth)
    currency    VARCHAR(3)     NOT NULL,
    version     BIGINT         NOT NULL DEFAULT 0              -- optimistic locking (@Version)
);

CREATE TABLE payments (
    id               UUID           PRIMARY KEY,
    idempotency_key  VARCHAR(100)   NOT NULL,
    request_hash     VARCHAR(64)    NOT NULL,
    debtor_iban      VARCHAR(34)    NOT NULL,
    creditor_iban    VARCHAR(34)    NOT NULL,
    amount           NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)     NOT NULL,
    remittance_info  VARCHAR(140),
    status           VARCHAR(20)    NOT NULL,
    rejection_reason VARCHAR(255),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key)   -- the real idempotency guarantee
);

CREATE INDEX idx_payments_status ON payments (status);

CREATE TABLE outbox_events (
    id            UUID          PRIMARY KEY,
    aggregate_id  VARCHAR(100)  NOT NULL,
    topic         VARCHAR(100)  NOT NULL,
    message_key   VARCHAR(100)  NOT NULL,
    event_type    VARCHAR(100)  NOT NULL,
    payload       TEXT          NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at  TIMESTAMP WITH TIME ZONE
);

-- Partial index: only unpublished rows are indexed -> the relay query stays fast even with millions of old rows.
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    event_id      UUID          PRIMARY KEY,
    consumer      VARCHAR(100)  NOT NULL,
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
