-- Proposal table for the suggestion/proposal system (gin7 제안 시스템)
-- Lower-rank officers can propose commands to superiors who hold the required position cards.

CREATE TABLE proposal (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL REFERENCES officer(id),
    approver_id BIGINT NOT NULL REFERENCES officer(id),
    action_code VARCHAR(100) NOT NULL,
    args JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    CONSTRAINT chk_proposal_status CHECK (status IN ('pending', 'approved', 'rejected', 'expired'))
);

CREATE INDEX idx_proposal_session ON proposal(session_id);
CREATE INDEX idx_proposal_approver_status ON proposal(approver_id, status);
CREATE INDEX idx_proposal_requester ON proposal(requester_id);
