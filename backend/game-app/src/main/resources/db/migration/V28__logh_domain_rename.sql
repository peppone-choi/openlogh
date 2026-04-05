-- =============================================================================
-- V28: OpenSamguk -> OpenLOGH Domain Rename Migration
-- =============================================================================
-- Purpose: Atomically rename all tables, columns, indexes, and enum types from
-- OpenSamguk (Three Kingdoms) terminology to LOGH (Legend of the Galactic Heroes)
-- domain terminology. Also adds 3 new stat columns (mobility, attack, defense).
--
-- All ALTER TABLE RENAME and ALTER INDEX RENAME operations are metadata-only in
-- PostgreSQL (no data rewrite), making this migration fast and safe regardless
-- of table size.
--
-- IMPORTANT: This migration MUST be applied together with the corresponding
-- Kotlin entity class renames in the application code.
-- =============================================================================

-- =============================================
-- 1. TABLE RENAMES (11 tables)
-- =============================================
ALTER TABLE general       RENAME TO officer;
ALTER TABLE general_turn  RENAME TO officer_turn;
ALTER TABLE city          RENAME TO planet;
ALTER TABLE nation        RENAME TO faction;
ALTER TABLE nation_turn   RENAME TO faction_turn;
ALTER TABLE nation_flag   RENAME TO faction_flag;
ALTER TABLE troop         RENAME TO fleet;
ALTER TABLE emperor       RENAME TO sovereign;
ALTER TABLE world_state   RENAME TO session_state;
ALTER TABLE old_general   RENAME TO old_officer;
ALTER TABLE old_nation    RENAME TO old_faction;

-- =============================================
-- 2. FK / REFERENCE COLUMN RENAMES
-- =============================================

-- officer (formerly general)
ALTER TABLE officer RENAME COLUMN nation_id  TO faction_id;
ALTER TABLE officer RENAME COLUMN city_id    TO planet_id;
ALTER TABLE officer RENAME COLUMN troop_id   TO fleet_id;
ALTER TABLE officer RENAME COLUMN world_id   TO session_id;

-- planet (formerly city)
ALTER TABLE planet RENAME COLUMN nation_id TO faction_id;
ALTER TABLE planet RENAME COLUMN world_id  TO session_id;

-- fleet (formerly troop)
ALTER TABLE fleet RENAME COLUMN leader_general_id TO leader_officer_id;
ALTER TABLE fleet RENAME COLUMN nation_id         TO faction_id;
ALTER TABLE fleet RENAME COLUMN world_id          TO session_id;

-- faction (formerly nation)
ALTER TABLE faction RENAME COLUMN chief_general_id TO chief_officer_id;
ALTER TABLE faction RENAME COLUMN capital_city_id  TO capital_planet_id;
ALTER TABLE faction RENAME COLUMN world_id         TO session_id;

-- faction_turn (formerly nation_turn)
ALTER TABLE faction_turn RENAME COLUMN nation_id TO faction_id;
ALTER TABLE faction_turn RENAME COLUMN world_id  TO session_id;

-- faction_flag (formerly nation_flag)
ALTER TABLE faction_flag RENAME COLUMN nation_id TO faction_id;
ALTER TABLE faction_flag RENAME COLUMN world_id  TO session_id;

-- officer_turn (formerly general_turn)
ALTER TABLE officer_turn RENAME COLUMN general_id TO officer_id;
ALTER TABLE officer_turn RENAME COLUMN world_id   TO session_id;

-- sovereign (formerly emperor): no world_id column (uses server_id)

-- session_state (formerly world_state): no FK renames needed

-- diplomacy
ALTER TABLE diplomacy RENAME COLUMN src_nation_id  TO src_faction_id;
ALTER TABLE diplomacy RENAME COLUMN dest_nation_id TO dest_faction_id;
ALTER TABLE diplomacy RENAME COLUMN world_id       TO session_id;

-- event
ALTER TABLE event RENAME COLUMN world_id TO session_id;

-- message
ALTER TABLE message RENAME COLUMN world_id TO session_id;

-- rank_data
ALTER TABLE rank_data RENAME COLUMN nation_id TO faction_id;
ALTER TABLE rank_data RENAME COLUMN world_id  TO session_id;

-- board
ALTER TABLE board RENAME COLUMN world_id TO session_id;

-- board_comment
ALTER TABLE board_comment RENAME COLUMN world_id TO session_id;

-- auction
ALTER TABLE auction RENAME COLUMN world_id TO session_id;

-- auction_bid
ALTER TABLE auction_bid RENAME COLUMN world_id TO session_id;

-- betting
ALTER TABLE betting RENAME COLUMN world_id TO session_id;

-- bet_entry
ALTER TABLE bet_entry RENAME COLUMN world_id TO session_id;

-- vote
ALTER TABLE vote RENAME COLUMN nation_id TO faction_id;
ALTER TABLE vote RENAME COLUMN world_id  TO session_id;

-- vote_cast
ALTER TABLE vote_cast RENAME COLUMN world_id TO session_id;

-- tournament
ALTER TABLE tournament RENAME COLUMN world_id TO session_id;

-- general_access_log
ALTER TABLE general_access_log RENAME COLUMN world_id TO session_id;

-- world_history
ALTER TABLE world_history RENAME COLUMN world_id TO session_id;

-- general_record
ALTER TABLE general_record RENAME COLUMN world_id TO session_id;

-- yearbook_history
ALTER TABLE yearbook_history RENAME COLUMN world_id TO session_id;

-- traffic_snapshot
ALTER TABLE traffic_snapshot RENAME COLUMN world_id TO session_id;

-- select_pool
ALTER TABLE select_pool RENAME COLUMN world_id TO session_id;

-- records
ALTER TABLE records RENAME COLUMN world_id TO session_id;

-- game_history: uses server_id (VARCHAR), not world_id -- no rename needed
-- hall_of_fame: uses server_id (VARCHAR), not world_id -- no rename needed
-- old_officer (formerly old_general): uses server_id, not world_id -- no rename needed
-- old_faction (formerly old_nation): uses server_id, not world_id -- no rename needed

-- =============================================
-- 3. STAT COLUMN RENAMES (officer table)
-- =============================================
ALTER TABLE officer RENAME COLUMN strength     TO command;
ALTER TABLE officer RENAME COLUMN strength_exp TO command_exp;
ALTER TABLE officer RENAME COLUMN intel        TO intelligence;
ALTER TABLE officer RENAME COLUMN intel_exp    TO intelligence_exp;
ALTER TABLE officer RENAME COLUMN charm        TO administration;
ALTER TABLE officer RENAME COLUMN charm_exp    TO administration_exp;

-- =============================================
-- 4. NEW STAT COLUMNS (officer table)
-- =============================================
ALTER TABLE officer ADD COLUMN mobility     SMALLINT NOT NULL DEFAULT 50;
ALTER TABLE officer ADD COLUMN mobility_exp SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE officer ADD COLUMN attack       SMALLINT NOT NULL DEFAULT 50;
ALTER TABLE officer ADD COLUMN attack_exp   SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE officer ADD COLUMN defense      SMALLINT NOT NULL DEFAULT 50;
ALTER TABLE officer ADD COLUMN defense_exp  SMALLINT NOT NULL DEFAULT 0;

-- dex columns for the 3 new stats
ALTER TABLE officer ADD COLUMN dex_6 INTEGER NOT NULL DEFAULT 0;
ALTER TABLE officer ADD COLUMN dex_7 INTEGER NOT NULL DEFAULT 0;
ALTER TABLE officer ADD COLUMN dex_8 INTEGER NOT NULL DEFAULT 0;

-- =============================================
-- 5. RESOURCE / ITEM COLUMN RENAMES (officer)
-- =============================================
ALTER TABLE officer RENAME COLUMN gold        TO funds;
ALTER TABLE officer RENAME COLUMN rice        TO supplies;
ALTER TABLE officer RENAME COLUMN crew        TO ships;
ALTER TABLE officer RENAME COLUMN crew_type   TO ship_class;
ALTER TABLE officer RENAME COLUMN train       TO training;
ALTER TABLE officer RENAME COLUMN atmos       TO morale;
ALTER TABLE officer RENAME COLUMN weapon_code TO flagship_code;
ALTER TABLE officer RENAME COLUMN book_code   TO equip_code;
ALTER TABLE officer RENAME COLUMN horse_code  TO engine_code;
ALTER TABLE officer RENAME COLUMN item_code   TO accessory_code;

-- =============================================
-- 6. PLANET RESOURCE COLUMN RENAMES
-- =============================================
ALTER TABLE planet RENAME COLUMN pop      TO population;
ALTER TABLE planet RENAME COLUMN pop_max  TO population_max;
ALTER TABLE planet RENAME COLUMN agri     TO production;
ALTER TABLE planet RENAME COLUMN agri_max TO production_max;
ALTER TABLE planet RENAME COLUMN comm     TO commerce;
ALTER TABLE planet RENAME COLUMN comm_max TO commerce_max;
ALTER TABLE planet RENAME COLUMN secu     TO security;
ALTER TABLE planet RENAME COLUMN secu_max TO security_max;
ALTER TABLE planet RENAME COLUMN trust    TO approval;
ALTER TABLE planet RENAME COLUMN def      TO orbital_defense;
ALTER TABLE planet RENAME COLUMN def_max  TO orbital_defense_max;
ALTER TABLE planet RENAME COLUMN wall     TO fortress;
ALTER TABLE planet RENAME COLUMN wall_max TO fortress_max;
ALTER TABLE planet RENAME COLUMN trade    TO trade_route;

-- =============================================
-- 7. FACTION COLUMN RENAMES
-- =============================================
ALTER TABLE faction RENAME COLUMN gold      TO funds;
ALTER TABLE faction RENAME COLUMN rice      TO supplies;
ALTER TABLE faction RENAME COLUMN bill      TO tax_rate;
ALTER TABLE faction RENAME COLUMN rate      TO conscription_rate;
ALTER TABLE faction RENAME COLUMN rate_tmp  TO conscription_rate_tmp;
ALTER TABLE faction RENAME COLUMN tech      TO tech_level;
ALTER TABLE faction RENAME COLUMN power     TO military_power;
ALTER TABLE faction RENAME COLUMN gennum    TO officer_count;
ALTER TABLE faction RENAME COLUMN level     TO faction_rank;
ALTER TABLE faction RENAME COLUMN type_code TO faction_type;

-- =============================================
-- 8. INDEX RENAMES
-- =============================================

-- Core indexes from V1 (now reference renamed tables/columns)
ALTER INDEX idx_general_world_id  RENAME TO idx_officer_session_id;
ALTER INDEX idx_general_nation_id RENAME TO idx_officer_faction_id;
ALTER INDEX idx_general_city_id   RENAME TO idx_officer_planet_id;
ALTER INDEX idx_general_user_id   RENAME TO idx_officer_user_id;
ALTER INDEX idx_city_world_id     RENAME TO idx_planet_session_id;
ALTER INDEX idx_city_nation_id    RENAME TO idx_planet_faction_id;
ALTER INDEX idx_nation_world_id   RENAME TO idx_faction_session_id;
ALTER INDEX idx_diplomacy_world_id RENAME TO idx_diplomacy_session_id;
ALTER INDEX idx_message_world_id  RENAME TO idx_message_session_id;
ALTER INDEX idx_event_world_id    RENAME TO idx_event_session_id;

-- =============================================
-- 9. ENUM TYPE RENAME
-- =============================================
ALTER TYPE nation_aux_key RENAME TO faction_aux_key;
