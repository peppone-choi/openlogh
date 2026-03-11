-- Fix: shiftTurnsDown bulk UPDATE violates unique constraint when PostgreSQL
-- processes rows in non-ascending order. Making constraints DEFERRABLE lets
-- all row updates complete before uniqueness is checked at transaction end.

ALTER TABLE general_turn
    DROP CONSTRAINT general_turn_general_id_turn_idx_key,
    ADD CONSTRAINT general_turn_general_id_turn_idx_key
        UNIQUE (general_id, turn_idx) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE nation_turn
    DROP CONSTRAINT nation_turn_nation_id_officer_level_turn_idx_key,
    ADD CONSTRAINT nation_turn_nation_id_officer_level_turn_idx_key
        UNIQUE (nation_id, officer_level, turn_idx) DEFERRABLE INITIALLY DEFERRED;
