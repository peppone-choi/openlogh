-- V40: Add home_planet_id dedicated column (replaces meta["returnPlanetId"])
-- Requirements: CHAR-15 (home planet auto-return), PERS-06 (flagship destruction return)

ALTER TABLE officer ADD COLUMN IF NOT EXISTS home_planet_id BIGINT DEFAULT NULL;
COMMENT ON COLUMN officer.home_planet_id IS 'Home planet for auto-return on flagship destruction (CHAR-15, PERS-06)';

-- Backfill from meta JSONB where present
UPDATE officer
SET home_planet_id = (meta->>'returnPlanetId')::BIGINT
WHERE meta ? 'returnPlanetId'
  AND meta->>'returnPlanetId' IS NOT NULL;
