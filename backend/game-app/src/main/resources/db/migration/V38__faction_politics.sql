-- V38: Faction politics tables - Empire coups, Alliance council/elections, Fezzan loans

-- Empire: Coup events
CREATE TABLE coup_event (
    id         BIGSERIAL PRIMARY KEY,
    session_id BIGINT      NOT NULL REFERENCES session_state(id),
    faction_id BIGINT      NOT NULL REFERENCES faction(id),
    leader_id  BIGINT      NOT NULL REFERENCES officer(id),
    phase      VARCHAR(20) NOT NULL DEFAULT 'PLANNING',
    supporter_ids       JSONB    NOT NULL DEFAULT '[]',
    target_sovereign_id BIGINT   NOT NULL,
    political_power     INT      NOT NULL DEFAULT 0,
    threshold           INT      NOT NULL DEFAULT 8000,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    result     VARCHAR(20),
    meta       JSONB       NOT NULL DEFAULT '{}'
);
CREATE INDEX idx_coup_event_session_faction_phase
    ON coup_event (session_id, faction_id, phase);

-- Alliance: Supreme Council seats
CREATE TABLE council_seat (
    id         BIGSERIAL PRIMARY KEY,
    session_id BIGINT      NOT NULL REFERENCES session_state(id),
    faction_id BIGINT      NOT NULL REFERENCES faction(id),
    seat_code  VARCHAR(50) NOT NULL,
    officer_id BIGINT      REFERENCES officer(id),
    elected_at  TIMESTAMPTZ,
    term_end_at TIMESTAMPTZ,
    votes_received INT     NOT NULL DEFAULT 0,
    meta       JSONB       NOT NULL DEFAULT '{}',
    UNIQUE (session_id, faction_id, seat_code)
);

-- Alliance: Elections
CREATE TABLE election (
    id         BIGSERIAL PRIMARY KEY,
    session_id BIGINT      NOT NULL REFERENCES session_state(id),
    faction_id BIGINT      NOT NULL REFERENCES faction(id),
    election_type VARCHAR(30) NOT NULL,
    started_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at    TIMESTAMPTZ,
    candidates JSONB       NOT NULL DEFAULT '[]',
    votes      JSONB       NOT NULL DEFAULT '{}',
    winner_officer_id BIGINT REFERENCES officer(id),
    is_completed BOOLEAN   NOT NULL DEFAULT FALSE,
    meta       JSONB       NOT NULL DEFAULT '{}'
);
CREATE INDEX idx_election_session_faction_completed
    ON election (session_id, faction_id, is_completed);

-- Fezzan: Loans
CREATE TABLE fezzan_loan (
    id         BIGSERIAL PRIMARY KEY,
    session_id BIGINT      NOT NULL REFERENCES session_state(id),
    borrower_faction_id BIGINT NOT NULL REFERENCES faction(id),
    principal       INT    NOT NULL,
    interest_rate   REAL   NOT NULL DEFAULT 0.05,
    remaining_debt  INT    NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    due_at      TIMESTAMPTZ NOT NULL,
    repaid_at   TIMESTAMPTZ,
    is_defaulted BOOLEAN   NOT NULL DEFAULT FALSE,
    meta       JSONB       NOT NULL DEFAULT '{}'
);
CREATE INDEX idx_fezzan_loan_session_borrower
    ON fezzan_loan (session_id, borrower_faction_id);
