-- V49: Add return_planet_id to officer for tactical injury warp (Phase 24-07)
-- Source: gin7 manual p51 — 전사/부상 처리, 귀환성 워프
-- Gap reference: docs/03-analysis/gin7-manual-complete-gap.analysis.md §A7/C4
--
-- When an officer's flagship is destroyed in tactical combat, the character is
-- injured (not killed by default per gin7 rules) and instantly warped to their
-- configured return planet. If null, fall back chain:
--   officer.return_planet_id → faction.capital_planet_id → officer.planet_id
--
-- TacticalBattleService.processFlagshipDestructions() consumes this column via
-- InjuryEvent.resolveReturnPlanet(). See model/DeathInjurySystem.kt.

ALTER TABLE officer
    ADD COLUMN return_planet_id BIGINT NULL;

-- No index needed — column is read during injury processing which is rare,
-- and always follows an officer primary-key lookup.

COMMENT ON COLUMN officer.return_planet_id IS 'Configured return planet for tactical injury warp (gin7 manual p51). Null = fall back to faction capital.';
