-- V51: Rescale Officer.pcp_max / mcp_max to match real gin7 manual CP costs.
--
-- Phase 24-05 (docs/03-analysis/gin7-manual-complete-gap.analysis.md §B).
-- Source: gin7 manual p69-78 戦略コマンド一覧表.
--
-- Before this migration, pool caps were 5..35 (CpPoolConfig pre-24-05) because
-- the runtime always deducted exactly 1 CP regardless of command. CommandCostTable
-- (Phase 24-05) now delegates BaseCommand.getCommandPointCost to authoritative
-- manual values (40, 80, 160, 320, 640, 800). A rank-0 officer with pool=5 would
-- no longer be able to execute any non-trivial action — including 원거리이동 (10 CP).
--
-- This migration walks officer_level and sets pcp_max/mcp_max to the new
-- CpPoolConfig schedule. It also clamps current pcp/mcp down to the new max
-- (impossible to exceed, but safer) and up to the new max for officers who
-- happened to have a low current pool.

UPDATE officer SET
    pcp_max = CASE officer_level
        WHEN 0  THEN 200
        WHEN 1  THEN 300
        WHEN 2  THEN 400
        WHEN 3  THEN 500
        WHEN 4  THEN 700
        WHEN 5  THEN 900
        WHEN 6  THEN 1200
        WHEN 7  THEN 1500
        WHEN 8  THEN 1800
        WHEN 9  THEN 2200
        WHEN 10 THEN 2600
        ELSE 200
    END,
    mcp_max = CASE officer_level
        WHEN 0  THEN 200
        WHEN 1  THEN 300
        WHEN 2  THEN 400
        WHEN 3  THEN 500
        WHEN 4  THEN 700
        WHEN 5  THEN 900
        WHEN 6  THEN 1200
        WHEN 7  THEN 1500
        WHEN 8  THEN 1800
        WHEN 9  THEN 2200
        WHEN 10 THEN 2600
        ELSE 200
    END;

-- Reset current pool to new max so existing officers do not start the new
-- regime with a stale low pool drained by the old 1-CP-per-command regime.
UPDATE officer SET
    pcp = pcp_max,
    mcp = mcp_max;

COMMENT ON COLUMN officer.pcp_max IS 'Max PCP pool. gin7 manual p69-78 + CpPoolConfig rank table. See Phase 24-05.';
COMMENT ON COLUMN officer.mcp_max IS 'Max MCP pool. gin7 manual p69-78 + CpPoolConfig rank table. See Phase 24-05.';
