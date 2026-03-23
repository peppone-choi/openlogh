-- V27: Rename OpenSamguk tables to LOGH domain names
-- Maps: general→officer, city→planet, nation→faction, troop→fleet(archive),
--       world_state→session_state, emperor→sovereign, etc.

-- 1. world_state → session_state (SessionState entity)
ALTER TABLE world_state RENAME TO session_state;
-- session_state.name is required by entity but not in original world_state
ALTER TABLE session_state ADD COLUMN IF NOT EXISTS name TEXT NOT NULL DEFAULT '';

-- 2. nation → faction (Faction entity)
ALTER TABLE nation RENAME TO faction;

-- 3. city → planet (Planet entity)
ALTER TABLE city RENAME TO planet;

-- 4. general → officer (Officer entity)
ALTER TABLE general RENAME TO officer;

-- 5. general_turn → officer_turn (OfficerTurn entity)
ALTER TABLE general_turn RENAME TO officer_turn;

-- 6. nation_turn → faction_turn (FactionTurn entity)
ALTER TABLE nation_turn RENAME TO faction_turn;

-- 7. general_record → officer_record (OfficerRecord entity)
ALTER TABLE general_record RENAME TO officer_record;

-- 8. general_access_log → officer_access_log (OfficerAccessLog entity)
ALTER TABLE general_access_log RENAME TO officer_access_log;

-- 9. nation_flag → faction_flag (FactionFlag entity)
ALTER TABLE nation_flag RENAME TO faction_flag;

-- 10. emperor → sovereign (Sovereign entity)
ALTER TABLE emperor RENAME TO sovereign;

-- 11. old_general → old_officer (OldOfficer entity)
ALTER TABLE old_general RENAME TO old_officer;

-- 12. old_nation → old_faction (OldFaction entity)
ALTER TABLE old_nation RENAME TO old_faction;

-- 13. troop is replaced by fleet (completely different structure)
--     Archive existing data; new fleet table created in V31
ALTER TABLE IF EXISTS troop RENAME TO troop_archive;

-- 14. Rename ENUM type: nation_aux_key → faction_aux_key
--     FactionFlag entity uses columnDefinition = "faction_aux_key"
ALTER TYPE nation_aux_key RENAME TO faction_aux_key;
