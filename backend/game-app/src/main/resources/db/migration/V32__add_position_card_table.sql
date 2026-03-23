-- V32: Create position_card table (직무카드)
-- Represents special roles/positions held by officers within a session
-- position_type: military, political, academy, military_police, fief
-- Fief cards (봉토카드) persist through rank changes (Empire only)

CREATE TABLE IF NOT EXISTS position_card (
    id                  BIGSERIAL PRIMARY KEY,
    officer_id          BIGINT NOT NULL,
    session_id          BIGINT NOT NULL REFERENCES session_state(id) ON DELETE CASCADE,
    -- Category: military, political, academy, military_police, fief
    position_type       TEXT NOT NULL,
    -- Human-readable Korean name (e.g. '함대 사령관', '봉토 영주')
    position_name_ko    TEXT NOT NULL,
    granted_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    meta                JSONB NOT NULL DEFAULT '{}'::jsonb,
    CHECK (jsonb_typeof(meta) = 'object')
);

CREATE INDEX IF NOT EXISTS idx_position_card_session_id ON position_card(session_id);
CREATE INDEX IF NOT EXISTS idx_position_card_officer_id ON position_card(officer_id);
CREATE INDEX IF NOT EXISTS idx_position_card_type ON position_card(position_type);
