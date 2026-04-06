-- V36: Add merit, evaluation, and fame point fields to officer table
-- Phase 7: Rank, Merit & Personnel

ALTER TABLE officer ADD COLUMN merit_points INT NOT NULL DEFAULT 0;
ALTER TABLE officer ADD COLUMN evaluation_points INT NOT NULL DEFAULT 0;
ALTER TABLE officer ADD COLUMN fame_points INT NOT NULL DEFAULT 0;

-- Index for rank ladder queries: session + faction + rank level + merit ordering
CREATE INDEX idx_officer_rank_ladder ON officer (session_id, faction_id, officer_level DESC, merit_points DESC);

COMMENT ON COLUMN officer.merit_points IS 'Merit points (공적) - determines rank order and promotion eligibility';
COMMENT ON COLUMN officer.evaluation_points IS 'Evaluation points (평가) - secondary ranking factor';
COMMENT ON COLUMN officer.fame_points IS 'Fame points (명성) - tertiary ranking factor, peerage/medals';
