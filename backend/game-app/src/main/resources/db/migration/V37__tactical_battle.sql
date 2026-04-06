-- V37: Tactical battle table for real-time fleet combat (Phase 10)
CREATE TABLE tactical_battle (
    id                  BIGSERIAL PRIMARY KEY,
    session_id          BIGINT       NOT NULL,
    star_system_id      BIGINT       NOT NULL,
    attacker_faction_id BIGINT       NOT NULL,
    defender_faction_id BIGINT       NOT NULL,
    phase               VARCHAR(20)  NOT NULL DEFAULT 'PREPARING',
    started_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ended_at            TIMESTAMPTZ,
    participants        JSONB        NOT NULL DEFAULT '{}',
    battle_state        JSONB        NOT NULL DEFAULT '{}',
    result              VARCHAR(20),
    tick_count          INT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_tactical_battle_session ON tactical_battle(session_id);
CREATE INDEX idx_tactical_battle_session_phase ON tactical_battle(session_id, phase);
CREATE INDEX idx_tactical_battle_star_system ON tactical_battle(session_id, star_system_id);
