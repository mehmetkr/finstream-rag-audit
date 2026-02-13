CREATE TABLE transactions (
    id           UUID PRIMARY KEY,
    amount       NUMERIC(19, 4)  NOT NULL,
    currency     VARCHAR(3)      NOT NULL,
    from_account VARCHAR(12)     NOT NULL,
    to_account   VARCHAR(12)     NOT NULL,
    description  TEXT,
    occurred_at  TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_transactions_from_account ON transactions (from_account);
CREATE INDEX idx_transactions_occurred_at ON transactions (occurred_at);
