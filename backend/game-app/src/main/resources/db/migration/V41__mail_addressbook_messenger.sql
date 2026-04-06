-- =============================================================
-- V10: Position Card Mail, Address Book, Messenger tables
-- =============================================================

-- Address book: stores exchanged name cards (명함교환)
-- Max 100 entries per officer, wiped on faction defection
CREATE TABLE IF NOT EXISTS address_book (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT  NOT NULL,
    owner_id    BIGINT  NOT NULL,  -- officer who owns this address book entry
    target_id   BIGINT  NOT NULL,  -- officer whose address was added
    address_type TEXT   NOT NULL DEFAULT 'PERSONAL',  -- PERSONAL or POSITION_CARD
    position_card TEXT,            -- position card code if address_type = POSITION_CARD
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(session_id, owner_id, target_id, address_type, position_card)
);

CREATE INDEX IF NOT EXISTS idx_address_book_owner ON address_book(session_id, owner_id);
CREATE INDEX IF NOT EXISTS idx_address_book_target ON address_book(session_id, target_id);

-- Messenger connection state (1:1 real-time calls)
CREATE TABLE IF NOT EXISTS messenger_connection (
    id            BIGSERIAL PRIMARY KEY,
    session_id    BIGINT  NOT NULL,
    caller_id     BIGINT  NOT NULL,  -- officer requesting connection
    callee_id     BIGINT  NOT NULL,  -- officer being called
    status        TEXT    NOT NULL DEFAULT 'PENDING',  -- PENDING, ACTIVE, CANCELLED, DECLINED
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_messenger_conn_callee ON messenger_connection(session_id, callee_id, status);
CREATE INDEX IF NOT EXISTS idx_messenger_conn_caller ON messenger_connection(session_id, caller_id, status);
