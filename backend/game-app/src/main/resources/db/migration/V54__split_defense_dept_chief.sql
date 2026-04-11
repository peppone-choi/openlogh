-- Phase 24-17 (gap D1, gin7 manual p61):
-- Split legacy `DEFENSE_DEPT_CHIEF` position card into the 11 concrete
-- 国防委員会 department chief cards that the gin7 manual enumerates
-- separately (査問/戦略/人事/防諜/情報/通信/装備/施設/経理/教育/衛生).
--
-- position_cards is stored as a JSONB array of string codes on officer.
-- Any officer who previously held DEFENSE_DEPT_CHIEF gets the HR chair
-- (DEFENSE_HR_DEPT) as a safe default because that is the slot that
-- carried the legacy promotion authority in PersonnelService.HR_CARDS.
-- Admins can re-assign the other 10 chairs via AppointCommand.
--
-- No officers in the current default seed hold DEFENSE_DEPT_CHIEF, but
-- any active world that already ran Phase 04's AppointCommand flow may
-- have rows referencing it — hence the safety rewrite below.

UPDATE officer
SET position_cards = (
    SELECT jsonb_agg(
        CASE WHEN value = '"DEFENSE_DEPT_CHIEF"'::jsonb
             THEN '"DEFENSE_HR_DEPT"'::jsonb
             ELSE value
        END
    )
    FROM jsonb_array_elements(position_cards) AS value
)
WHERE position_cards @> '["DEFENSE_DEPT_CHIEF"]'::jsonb;
