-- =============================================================================
-- V1__init.sql — Initial schema for The Bank of Banco Imobiliário
-- All money amounts are stored in centavos (BIGINT) to avoid floating-point errors.
-- Every entity is scoped by game_id so multiple games can run concurrently.
-- =============================================================================

CREATE TABLE games (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_games_name_not_blank CHECK (btrim(name) <> '')
);

CREATE TABLE accounts (
    id                    BIGSERIAL    PRIMARY KEY,
    game_id               BIGINT       NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    type                  VARCHAR(16)  NOT NULL,
    name                  VARCHAR(60)  NOT NULL,
    color                 VARCHAR(32),
    initial_balance_cents BIGINT       NOT NULL,
    balance_cents         BIGINT       NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_accounts_type
        CHECK (type IN ('BANK', 'PLAYER')),
    CONSTRAINT chk_accounts_name_not_blank
        CHECK (btrim(name) <> ''),
    CONSTRAINT chk_accounts_initial_balance_non_negative
        CHECK (initial_balance_cents >= 0),
    -- Players can never go below 0; the bank's non-negative rule when limited is enforced at the service/check level
    CONSTRAINT chk_accounts_player_balance_non_negative
        CHECK (type = 'BANK' OR balance_cents >= 0),
    -- Players must have a pawn color; the bank has no pawn color
    CONSTRAINT chk_accounts_color_by_type
        CHECK ((type = 'BANK' AND color IS NULL) OR (type = 'PLAYER' AND color IS NOT NULL))
);

-- Unique account name per game (case-insensitive, trimmed)
CREATE UNIQUE INDEX uq_accounts_game_lower_name
    ON accounts (game_id, lower(btrim(name)));

-- Unique pawn color per game (only applies to rows where color IS NOT NULL)
CREATE UNIQUE INDEX uq_accounts_game_color
    ON accounts (game_id, color)
    WHERE color IS NOT NULL;

-- Partial unique index: exactly one BANK account per game
CREATE UNIQUE INDEX uq_accounts_one_bank_per_game
    ON accounts (game_id)
    WHERE type = 'BANK';

CREATE INDEX idx_accounts_game_id
    ON accounts (game_id);

CREATE TABLE transactions (
    id                         BIGSERIAL    PRIMARY KEY,
    game_id                    BIGINT       NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    from_account_id            BIGINT       NOT NULL REFERENCES accounts(id),
    to_account_id              BIGINT       NOT NULL REFERENCES accounts(id),
    amount_cents               BIGINT       NOT NULL,
    reason                     VARCHAR(120) NOT NULL,
    idempotency_key            UUID         NOT NULL,
    reversal_of_transaction_id BIGINT       REFERENCES transactions(id),
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_transactions_amount_positive
        CHECK (amount_cents > 0),
    CONSTRAINT chk_transactions_distinct_accounts
        CHECK (from_account_id <> to_account_id),
    CONSTRAINT chk_transactions_reason_not_blank
        CHECK (btrim(reason) <> ''),
    CONSTRAINT uq_transactions_idempotency_key
        UNIQUE (idempotency_key),
    CONSTRAINT uq_transactions_reversal_of
        UNIQUE (reversal_of_transaction_id)
);

CREATE INDEX idx_transactions_game_created_at
    ON transactions (game_id, created_at DESC, id DESC);

CREATE INDEX idx_transactions_from_account
    ON transactions (from_account_id);

CREATE INDEX idx_transactions_to_account
    ON transactions (to_account_id);
