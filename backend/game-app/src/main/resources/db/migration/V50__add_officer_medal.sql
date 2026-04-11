-- V50: Add medal_rank and medal_count to officer for 叙勲 system (Phase 24-09)
-- Source: gin7 manual p34-35 — 階級ラダー 第三法則 (叙勲している最高の勲章の順위)
-- Gap reference: docs/03-analysis/gin7-manual-complete-gap.analysis.md §A3
--
-- Phase 24-09 Medal System:
--   - medal_rank: highest rank of medals held (0 = none, 1..N = rising importance)
--   - medal_count: total number of medals awarded (secondary tiebreaker)
--
-- RankLadderService consumes medal_rank as the 第三法則 tie-breaker in its
-- sort chain. AwardDecorationCommand increments both columns on success.

ALTER TABLE officer
    ADD COLUMN medal_rank SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN medal_count SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN officer.medal_rank IS 'Highest medal rank (0=none). gin7 p34 rank ladder 第三法則.';
COMMENT ON COLUMN officer.medal_count IS 'Total medals awarded. Secondary tiebreaker under medal_rank.';
