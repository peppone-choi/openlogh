CREATE TABLE command_proposal (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT NOT NULL,
    proposer_id     BIGINT NOT NULL,
    approver_id     BIGINT,
    command_code    VARCHAR(64) NOT NULL,
    args            JSONB NOT NULL DEFAULT '{}',
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMPTZ,
    result_log      VARCHAR(2048),

    CONSTRAINT fk_proposal_session FOREIGN KEY (session_id)
        REFERENCES session_state(id) ON DELETE CASCADE
);

CREATE INDEX idx_command_proposal_session_status
    ON command_proposal(session_id, status);

CREATE INDEX idx_command_proposal_approver
    ON command_proposal(approver_id, status)
    WHERE status = 'PENDING';
