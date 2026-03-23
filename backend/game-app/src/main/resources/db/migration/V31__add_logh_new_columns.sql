-- V31: Add new LOGH domain columns and create fleet table

-- ============================================================
-- officer: LOGH 8-stat system — new mobility/attack/defense columns
-- ============================================================
ALTER TABLE officer ADD COLUMN IF NOT EXISTS mobility SMALLINT NOT NULL DEFAULT 50;
ALTER TABLE officer ADD COLUMN IF NOT EXISTS mobility_exp SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE officer ADD COLUMN IF NOT EXISTS attack SMALLINT NOT NULL DEFAULT 50;
ALTER TABLE officer ADD COLUMN IF NOT EXISTS attack_exp SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE officer ADD COLUMN IF NOT EXISTS defense SMALLINT NOT NULL DEFAULT 50;
ALTER TABLE officer ADD COLUMN IF NOT EXISTS defense_exp SMALLINT NOT NULL DEFAULT 0;

-- officer: location state (planet/fleet/space)
ALTER TABLE officer ADD COLUMN IF NOT EXISTS location_state TEXT NOT NULL DEFAULT 'planet';

-- officer: peerage (Empire nobility: none/ritter/freiherr/viscount/graf/marquis/herzog)
ALTER TABLE officer ADD COLUMN IF NOT EXISTS peerage TEXT NOT NULL DEFAULT 'none';

-- officer: influence (rank ladder & political commands)
ALTER TABLE officer ADD COLUMN IF NOT EXISTS influence INTEGER NOT NULL DEFAULT 0;

-- NOTE: officer_turn.world_id and faction_turn.world_id already exist (added in V6)
-- FK constraints auto-update to reference session_state after V27 table rename

-- ============================================================
-- planet: fief officer (Empire 봉토 system — Empire only)
-- ============================================================
ALTER TABLE planet ADD COLUMN IF NOT EXISTS fief_officer_id BIGINT;

-- ============================================================
-- faction: secession and independence system
-- ============================================================
ALTER TABLE faction ADD COLUMN IF NOT EXISTS parent_faction_id BIGINT;
ALTER TABLE faction ADD COLUMN IF NOT EXISTS secession_type TEXT NOT NULL DEFAULT 'none';
ALTER TABLE faction ADD COLUMN IF NOT EXISTS secession_state TEXT NOT NULL DEFAULT 'none';
ALTER TABLE faction ADD COLUMN IF NOT EXISTS secession_leader_id BIGINT;
ALTER TABLE faction ADD COLUMN IF NOT EXISTS diplomacy_enabled BOOLEAN NOT NULL DEFAULT true;

-- ============================================================
-- officer_access_log: refresh tracking (anti-abuse)
-- ============================================================
ALTER TABLE officer_access_log ADD COLUMN IF NOT EXISTS refresh INTEGER NOT NULL DEFAULT 0;
ALTER TABLE officer_access_log ADD COLUMN IF NOT EXISTS refresh_score_total INTEGER NOT NULL DEFAULT 0;

-- ============================================================
-- fleet table (new LOGH fleet structure)
-- Replaces troop (archived in V27); completely different schema
-- fleet types: fleet/division/patrol/transport/ground/garrison
-- ============================================================
CREATE TABLE IF NOT EXISTS fleet (
    id                  BIGSERIAL PRIMARY KEY,
    session_id          BIGINT NOT NULL REFERENCES session_state(id) ON DELETE CASCADE,
    leader_officer_id   BIGINT NOT NULL,
    faction_id          BIGINT NOT NULL DEFAULT 0,
    parent_fleet_id     BIGINT,
    sort_order          SMALLINT NOT NULL DEFAULT 0,
    name                TEXT NOT NULL,
    -- fleet, division, patrol, transport, ground, garrison
    fleet_type          TEXT NOT NULL DEFAULT 'fleet',
    planet_id           BIGINT,
    grid_x              INT,
    grid_y              INT,
    -- Flagship (기함)
    flagship_code       TEXT NOT NULL DEFAULT 'standard_battleship',
    -- Combat ship units (전투 함선)
    battleships         INT NOT NULL DEFAULT 0,
    cruisers            INT NOT NULL DEFAULT 0,
    destroyers          INT NOT NULL DEFAULT 0,
    carriers            INT NOT NULL DEFAULT 0,
    -- Ground forces (지상 부대)
    ground_troops       INT NOT NULL DEFAULT 0,
    assault_ships       INT NOT NULL DEFAULT 0,
    -- Support units (지원 부대)
    transports          INT NOT NULL DEFAULT 0,
    hospital_ships      INT NOT NULL DEFAULT 0,
    -- Fleet state
    morale              SMALLINT NOT NULL DEFAULT 100,
    training            SMALLINT NOT NULL DEFAULT 50,
    supplies            INT NOT NULL DEFAULT 0,
    formation           TEXT NOT NULL DEFAULT 'spindle',
    fleet_state         SMALLINT NOT NULL DEFAULT 0,
    -- Energy allocation (6 channels, sum = 100; used in tactical combat)
    energy_beam         SMALLINT NOT NULL DEFAULT 20,
    energy_gun          SMALLINT NOT NULL DEFAULT 20,
    energy_shield       SMALLINT NOT NULL DEFAULT 20,
    energy_engine       SMALLINT NOT NULL DEFAULT 20,
    energy_sensor       SMALLINT NOT NULL DEFAULT 10,
    energy_warp         SMALLINT NOT NULL DEFAULT 10,
    meta                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (jsonb_typeof(meta) = 'object')
);

CREATE INDEX IF NOT EXISTS idx_fleet_session_id ON fleet(session_id);
CREATE INDEX IF NOT EXISTS idx_fleet_faction_id ON fleet(faction_id);
CREATE INDEX IF NOT EXISTS idx_fleet_leader_officer_id ON fleet(leader_officer_id);
CREATE INDEX IF NOT EXISTS idx_fleet_parent_fleet_id ON fleet(parent_fleet_id);
