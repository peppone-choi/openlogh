-- V39: Add victory result to session_state and create session_ranking table

-- Victory result stored as JSONB on session_state
ALTER TABLE session_state ADD COLUMN IF NOT EXISTS victory_result JSONB;
ALTER TABLE session_state ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- Session ranking table for end-of-session rankings
CREATE TABLE IF NOT EXISTS session_ranking (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    officer_id BIGINT NOT NULL,
    officer_name VARCHAR(255) NOT NULL,
    faction_id BIGINT NOT NULL,
    final_rank INT NOT NULL DEFAULT 0,
    score INT NOT NULL DEFAULT 0,
    merit_points INT NOT NULL DEFAULT 0,
    stats JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_session_ranking_session_id ON session_ranking(session_id);
CREATE INDEX IF NOT EXISTS idx_session_ranking_score ON session_ranking(session_id, score DESC);
