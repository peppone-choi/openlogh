-- V52: Remove Alliance tier 9 (상급대장) officers — gin7 manual p34 says
-- this tier does NOT exist in the Alliance hierarchy (Empire-only).
--
-- Phase 24-11 (user feedback: "제국엔 상급대장이 있고 동맹엔 없어").
-- Gap reference: docs/03-analysis/gin7-manual-complete-gap.analysis.md §D1 complement.
--
-- Before Phase 24-11, RankTitleResolver and RankHeadcount treated tier 9 as
-- "Admiral of the Fleet / 상급대장" for both Empire and Alliance officers. Any
-- Alliance officer that happened to land at tier 9 (via seed data or test
-- fixtures) is structurally invalid under the corrected rules.
--
-- We clamp all such officers down to tier 8 (大将 / Admiral) — the highest
-- valid Alliance tier below 元帥. They can legitimately re-promote to tier 10
-- through the normal auto-promotion loop in RankLadderService.getPromotionCandidates,
-- which now uses RankHeadcount.nextTier to skip the vacant tier 9 slot.
--
-- We intentionally DO NOT batch-promote them straight to tier 10 here; the
-- 元帥 cap is 5 heads and bulk-filling that table behind the scenes would
-- distort merit rankings set by normal gameplay. Tier-8 clamp is the safer
-- default.

UPDATE officer
SET officer_level = 8
WHERE officer_level = 9
  AND faction_id IN (
      SELECT id FROM faction WHERE faction_type = 'alliance'
  );
