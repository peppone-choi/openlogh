-- HARD-01: Add optimistic locking version column to officer table
ALTER TABLE officer ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
