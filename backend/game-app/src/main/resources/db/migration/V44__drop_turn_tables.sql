-- Legacy turn queue tables are no longer used in real-time mode.
-- Commands execute immediately with cooldowns instead of being queued in per-turn slots.
-- Truncate data but keep tables for Hibernate schema validation compatibility.
TRUNCATE TABLE officer_turn;
TRUNCATE TABLE faction_turn;

-- Add comment to mark as deprecated
COMMENT ON TABLE officer_turn IS 'DEPRECATED: Legacy turn-based queue. Not used in real-time mode.';
COMMENT ON TABLE faction_turn IS 'DEPRECATED: Legacy turn-based queue. Not used in real-time mode.';
