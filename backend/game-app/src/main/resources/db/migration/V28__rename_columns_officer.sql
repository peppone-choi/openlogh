-- V28: Rename columns in officer (formerly general) and related tables to LOGH domain

-- ============================================================
-- officer table: stat renames (OpenSamguk 5-stat → LOGH 8-stat)
-- ============================================================
ALTER TABLE officer RENAME COLUMN strength TO command;
ALTER TABLE officer RENAME COLUMN strength_exp TO command_exp;
ALTER TABLE officer RENAME COLUMN intel TO intelligence;
ALTER TABLE officer RENAME COLUMN intel_exp TO intelligence_exp;
ALTER TABLE officer RENAME COLUMN charm TO administration;
ALTER TABLE officer RENAME COLUMN charm_exp TO administration_exp;

-- ============================================================
-- officer table: FK column renames
-- ============================================================
ALTER TABLE officer RENAME COLUMN nation_id TO faction_id;
ALTER TABLE officer RENAME COLUMN city_id TO planet_id;
ALTER TABLE officer RENAME COLUMN troop_id TO fleet_id;

-- ============================================================
-- officer table: lifecycle renames
-- ============================================================
ALTER TABLE officer RENAME COLUMN born_year TO birth_year;
ALTER TABLE officer RENAME COLUMN dead_year TO death_year;

-- ============================================================
-- officer table: rank/position renames
-- ============================================================
ALTER TABLE officer RENAME COLUMN officer_level TO rank;
ALTER TABLE officer RENAME COLUMN officer_city TO stationed_system;

-- ============================================================
-- officer table: resource renames (personal resources)
-- ============================================================
ALTER TABLE officer RENAME COLUMN gold TO funds;
ALTER TABLE officer RENAME COLUMN rice TO supplies;
ALTER TABLE officer RENAME COLUMN crew TO ships;
ALTER TABLE officer RENAME COLUMN crew_type TO ship_class;
ALTER TABLE officer RENAME COLUMN train TO training;
ALTER TABLE officer RENAME COLUMN atmos TO morale;

-- ============================================================
-- officer table: item/equipment renames (LOGH starship/gear)
-- ============================================================
ALTER TABLE officer RENAME COLUMN weapon_code TO flagship_code;
ALTER TABLE officer RENAME COLUMN book_code TO equip_code;
ALTER TABLE officer RENAME COLUMN horse_code TO engine_code;
ALTER TABLE officer RENAME COLUMN item_code TO accessory_code;

-- ============================================================
-- officer_turn table: general_id → officer_id
-- ============================================================
ALTER TABLE officer_turn RENAME COLUMN general_id TO officer_id;

-- ============================================================
-- officer_record table: general_id → officer_id
-- ============================================================
ALTER TABLE officer_record RENAME COLUMN general_id TO officer_id;

-- ============================================================
-- officer_access_log table: general_id → officer_id
-- ============================================================
ALTER TABLE officer_access_log RENAME COLUMN general_id TO officer_id;

-- ============================================================
-- old_officer table: general_no → officer_no
-- ============================================================
ALTER TABLE old_officer RENAME COLUMN general_no TO officer_no;
