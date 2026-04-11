-- V53: Add planet_type column for ground combat unit filtering.
--
-- Phase 24-12 (gap A6/C3, docs/03-analysis/gin7-manual-complete-gap.analysis.md §8.2).
-- Source: gin7 manual p50 — "지상전에 참가할 수 있는유닛 종류는 행성의 타입에 따라 
-- 달라진다(행성타입는현재미구현이다)".
--
-- Values:
--   'normal'   : default, all three ground unit types allowed
--   'gas'      : gas giant, no heavy 장갑병 (armored infantry)
--   'fortress' : fortress assault, no heavy 장갑병
--
-- Existing planets default to 'normal' — fortress/gas flags must be set
-- via seed data import or admin command. We do NOT auto-promote existing
-- rows where `fortress_type != 'NONE'` because fortress_type is a separate
-- concept (fortress gun tier) not a ground-combat classifier.

ALTER TABLE planet
    ADD COLUMN planet_type VARCHAR(16) NOT NULL DEFAULT 'normal';

-- Migrate planets whose fortress_type indicates they are fortresses
-- (Iserlohn / Gaiesburg) to planet_type = 'fortress' so ground assault
-- filtering picks them up automatically.
UPDATE planet
SET planet_type = 'fortress'
WHERE fortress_type != 'NONE';

COMMENT ON COLUMN planet.planet_type IS 'Terrain classification (normal/gas/fortress) gating ground combat unit types. gin7 manual p50.';
