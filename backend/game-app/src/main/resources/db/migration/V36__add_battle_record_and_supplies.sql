-- V36: Add battle_record table + planet.supplies column

CREATE TABLE IF NOT EXISTS battle_record (
    id                      BIGSERIAL PRIMARY KEY,
    session_id              BIGINT NOT NULL REFERENCES session_state(id) ON DELETE CASCADE,
    session_code            TEXT NOT NULL DEFAULT '',
    planet_id               BIGINT NOT NULL DEFAULT 0,
    planet_name             TEXT NOT NULL DEFAULT '',
    attacker_faction_id     BIGINT NOT NULL DEFAULT 0,
    attacker_faction_name   TEXT NOT NULL DEFAULT '',
    attacker_officers       JSONB NOT NULL DEFAULT '[]'::jsonb,
    defender_faction_id     BIGINT NOT NULL DEFAULT 0,
    defender_faction_name   TEXT NOT NULL DEFAULT '',
    defender_officers       JSONB NOT NULL DEFAULT '[]'::jsonb,
    winner_faction_id       BIGINT NOT NULL DEFAULT 0,
    victory_type            TEXT NOT NULL DEFAULT '',
    total_turns             INT NOT NULL DEFAULT 0,
    attacker_won            BOOLEAN NOT NULL DEFAULT false,
    planet_captured         BOOLEAN NOT NULL DEFAULT false,
    attacker_ships_lost     INT NOT NULL DEFAULT 0,
    defender_ships_lost     INT NOT NULL DEFAULT 0,
    attacker_ships_initial  INT NOT NULL DEFAULT 0,
    defender_ships_initial  INT NOT NULL DEFAULT 0,
    battle_log              JSONB NOT NULL DEFAULT '[]'::jsonb,
    initial_state           JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_battle_record_session_id ON battle_record(session_id);

-- Add supplies column to planet (used by TransportExecution/PlanetProduction)
ALTER TABLE planet ADD COLUMN IF NOT EXISTS supplies INT NOT NULL DEFAULT 0;
