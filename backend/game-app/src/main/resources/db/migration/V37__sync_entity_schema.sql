-- V37: Comprehensive entity-schema sync
-- Adds all columns present in entities but missing from prior migrations.
-- Uses ADD COLUMN IF NOT EXISTS throughout for idempotency.

-- ============================================================
-- fleet: missing ship-subtype, fuel, crew, and ground columns
-- (fleet table created in V31 — these fields were added to the
--  entity after V31 was written)
-- ============================================================

-- Ship subtypes (V31 only has battleships/cruisers/destroyers/carriers)
ALTER TABLE fleet ADD COLUMN IF NOT EXISTS fast_battleships   INT NOT NULL DEFAULT 0;
ALTER TABLE fleet ADD COLUMN IF NOT EXISTS strike_cruisers    INT NOT NULL DEFAULT 0;
ALTER TABLE fleet ADD COLUMN IF NOT EXISTS torpedo_carriers   INT NOT NULL DEFAULT 0;
ALTER TABLE fleet ADD COLUMN IF NOT EXISTS engineering_ships  INT NOT NULL DEFAULT 0;

-- Warp fuel system
ALTER TABLE fleet ADD COLUMN IF NOT EXISTS fuel               INT NOT NULL DEFAULT 1000;
ALTER TABLE fleet ADD COLUMN IF NOT EXISTS fuel_max           INT NOT NULL DEFAULT 1000;

-- Crew quality and ship generation
ALTER TABLE fleet ADD COLUMN IF NOT EXISTS crew_grade         TEXT NOT NULL DEFAULT 'normal';
ALTER TABLE fleet ADD COLUMN IF NOT EXISTS ship_generation    SMALLINT NOT NULL DEFAULT 1;

-- Ground unit doctrine (set at strategic phase, cannot change mid-battle)
ALTER TABLE fleet ADD COLUMN IF NOT EXISTS ground_unit_type   TEXT NOT NULL DEFAULT 'marines';

-- ============================================================
-- officer: missing LOGH-specific columns
-- (officer table = general in V1, renamed in V27, columns added
--  in V28/V31 — these were added to entity after those migrations)
-- ============================================================

-- Career classification
ALTER TABLE officer ADD COLUMN IF NOT EXISTS career_type      TEXT NOT NULL DEFAULT 'military';
ALTER TABLE officer ADD COLUMN IF NOT EXISTS origin_type      TEXT NOT NULL DEFAULT 'commoner';

-- Ops abilities (정치/정보/군사 공작)
ALTER TABLE officer ADD COLUMN IF NOT EXISTS political_ops    INT NOT NULL DEFAULT 0;
ALTER TABLE officer ADD COLUMN IF NOT EXISTS intel_ops        INT NOT NULL DEFAULT 0;
ALTER TABLE officer ADD COLUMN IF NOT EXISTS military_ops     INT NOT NULL DEFAULT 0;

-- Cross-session fame points
ALTER TABLE officer ADD COLUMN IF NOT EXISTS fame_points      INT NOT NULL DEFAULT 0;

-- Fighter (공전) skill — carrier-based combat
ALTER TABLE officer ADD COLUMN IF NOT EXISTS fighter_skill     SMALLINT NOT NULL DEFAULT 30;
ALTER TABLE officer ADD COLUMN IF NOT EXISTS fighter_skill_exp SMALLINT NOT NULL DEFAULT 0;

-- Ground combat (육전) skill — assault landings
ALTER TABLE officer ADD COLUMN IF NOT EXISTS ground_combat     SMALLINT NOT NULL DEFAULT 30;
ALTER TABLE officer ADD COLUMN IF NOT EXISTS ground_combat_exp SMALLINT NOT NULL DEFAULT 0;

-- Command point system (PCP = political, MCP = military)
-- command_points already exists from V1; pcp/mcp are separate new fields
ALTER TABLE officer ADD COLUMN IF NOT EXISTS pcp              INT NOT NULL DEFAULT 10;
ALTER TABLE officer ADD COLUMN IF NOT EXISTS mcp              INT NOT NULL DEFAULT 10;
ALTER TABLE officer ADD COLUMN IF NOT EXISTS pcp_used_total   INT NOT NULL DEFAULT 0;
ALTER TABLE officer ADD COLUMN IF NOT EXISTS mcp_used_total   INT NOT NULL DEFAULT 0;

-- ============================================================
-- session_state: version tracking columns
-- (world_state created in V1, renamed in V27 — these fields
--  were added to entity after V27)
-- ============================================================
ALTER TABLE session_state ADD COLUMN IF NOT EXISTS commit_sha   TEXT NOT NULL DEFAULT 'local';
ALTER TABLE session_state ADD COLUMN IF NOT EXISTS game_version TEXT NOT NULL DEFAULT 'dev';
