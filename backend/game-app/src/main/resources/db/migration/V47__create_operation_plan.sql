-- V47: OperationPlan 엔티티 테이블 (Phase 12 작전 연동)
-- gin7 작전계획: CONQUEST/DEFENSE/SWEEP × 단일 성계 목표 × 참가 함대 집합
-- 진영당 동시 다중 작전 허용 (D-02); 부대당 1작전 enforced at application layer (D-04)

BEGIN;

CREATE TABLE operation_plan (
    id                        BIGSERIAL    PRIMARY KEY,
    session_id                BIGINT       NOT NULL,
    faction_id                BIGINT       NOT NULL,
    name                      VARCHAR(128) NOT NULL,

    -- MissionObjective enum: CONQUEST / DEFENSE / SWEEP
    objective                 VARCHAR(16)  NOT NULL,

    target_star_system_id     BIGINT       NOT NULL,

    -- OperationStatus enum: PENDING / ACTIVE / COMPLETED / CANCELLED
    status                    VARCHAR(16)  NOT NULL DEFAULT 'PENDING',

    -- JSONB array of Fleet IDs (List<Long>)
    participant_fleet_ids     JSONB        NOT NULL DEFAULT '[]',

    -- gin7 scale 1..7 (MCP cost / merit leverage)
    scale                     SMALLINT     NOT NULL DEFAULT 1,

    -- Audit / history / succession narrative
    issued_by_officer_id      BIGINT       NOT NULL,
    issued_at_tick            BIGINT       NOT NULL,

    -- Column added but consumption deferred (D-05)
    expected_completion_tick  BIGINT,

    -- DEFENSE stability counter (D-18)
    stability_tick_counter    INT          NOT NULL DEFAULT 0,

    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_operation_plan_session FOREIGN KEY (session_id)
        REFERENCES session_state(id) ON DELETE CASCADE
);

-- Activation query: find PENDING/ACTIVE operations per session
CREATE INDEX idx_operation_plan_session_status
    ON operation_plan(session_id, status);

-- BattleTrigger lookup: given a fleet, find its active operation via JSONB membership
CREATE INDEX idx_operation_plan_participants
    ON operation_plan USING GIN (participant_fleet_ids jsonb_path_ops);

-- Faction ownership (for Phase 13 strategic AI enumeration)
CREATE INDEX idx_operation_plan_faction
    ON operation_plan(session_id, faction_id, status);

COMMIT;
