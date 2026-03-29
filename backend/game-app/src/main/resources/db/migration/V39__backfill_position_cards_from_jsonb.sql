-- V39: Backfill position_card rows from officer.meta JSONB
-- Must run BEFORE code switches to relational reads (HARD-03).
--
-- Extracts positionCards array from officer.meta and inserts
-- corresponding rows into the position_card table.
-- ON CONFLICT DO NOTHING ensures idempotency if some cards
-- already exist (e.g. partial migration or re-run).

INSERT INTO position_card (officer_id, session_id, position_type, position_name_ko, granted_at, meta)
SELECT
    o.id,
    o.world_id,
    card_code.value,
    card_code.value,
    now(),
    '{}'::jsonb
FROM officer o,
     jsonb_array_elements_text(o.meta -> 'positionCards') AS card_code
WHERE o.meta ? 'positionCards'
  AND jsonb_typeof(o.meta -> 'positionCards') = 'array'
ON CONFLICT DO NOTHING;
