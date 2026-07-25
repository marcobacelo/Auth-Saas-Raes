CREATE TABLE IF NOT EXISTS tenants (
    tenant_id UUID PRIMARY KEY,
    slug      VARCHAR(63)  NOT NULL UNIQUE,
    status    VARCHAR(32)  NOT NULL
);

CREATE TABLE IF NOT EXISTS identities (
    identity_id   UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL REFERENCES tenants (tenant_id),
    username      VARCHAR(255) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    password_hash TEXT         NOT NULL,
    UNIQUE (tenant_id, username)
);
