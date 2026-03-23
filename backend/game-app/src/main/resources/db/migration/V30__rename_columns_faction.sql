-- V30: Rename columns in faction (formerly nation) and related tables to LOGH domain

-- ============================================================
-- faction table
-- ============================================================

-- Capital reference
ALTER TABLE faction RENAME COLUMN capital_city_id TO capital_planet_id;

-- Resources (gold/rice → funds/supplies)
ALTER TABLE faction RENAME COLUMN gold TO funds;
ALTER TABLE faction RENAME COLUMN rice TO supplies;

-- Rates (tax/conscription)
ALTER TABLE faction RENAME COLUMN bill TO tax_rate;
ALTER TABLE faction RENAME COLUMN rate TO conscription_rate;
ALTER TABLE faction RENAME COLUMN rate_tmp TO conscription_rate_tmp;

-- Leadership
ALTER TABLE faction RENAME COLUMN chief_general_id TO supreme_commander_id;

-- Tech/power stats
ALTER TABLE faction RENAME COLUMN tech TO tech_level;
ALTER TABLE faction RENAME COLUMN power TO military_power;
ALTER TABLE faction RENAME COLUMN level TO faction_rank;

-- Faction type code
ALTER TABLE faction RENAME COLUMN type_code TO faction_type;

-- Officer count (gennum → officer_count)
ALTER TABLE faction RENAME COLUMN gennum TO officer_count;

-- ============================================================
-- faction_turn table: nation_id → faction_id
-- ============================================================
ALTER TABLE faction_turn RENAME COLUMN nation_id TO faction_id;

-- ============================================================
-- faction_flag table: nation_id → faction_id
-- ============================================================
ALTER TABLE faction_flag RENAME COLUMN nation_id TO faction_id;
