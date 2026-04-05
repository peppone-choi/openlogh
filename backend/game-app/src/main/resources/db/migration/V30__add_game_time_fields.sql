-- V30: Add game clock tracking fields to session_state
-- These columns support the real-time tick engine (Phase 2)

ALTER TABLE session_state ADD COLUMN game_time_sec BIGINT NOT NULL DEFAULT 0;
ALTER TABLE session_state ADD COLUMN tick_count BIGINT NOT NULL DEFAULT 0;
