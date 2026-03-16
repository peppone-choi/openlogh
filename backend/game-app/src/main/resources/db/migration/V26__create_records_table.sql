-- V26: Create records table and migrate log data from messages

CREATE TABLE IF NOT EXISTS records (
    id BIGSERIAL PRIMARY KEY,
    world_id BIGINT NOT NULL,
    record_type VARCHAR(50) NOT NULL,
    src_id BIGINT,
    dest_id BIGINT,
    year INT NOT NULL,
    month INT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_records_world_type ON records (world_id, record_type);
CREATE INDEX idx_records_dest_type ON records (dest_id, record_type);
CREATE INDEX idx_records_created ON records (created_at);

INSERT INTO records (world_id, record_type, src_id, dest_id, year, month, payload, created_at)
SELECT 
    world_id,
    mailbox_code,
    src_id,
    dest_id,
    COALESCE((payload->>'year')::int, 0),
    COALESCE((payload->>'month')::int, 1),
    payload,
    sent_at
FROM message
WHERE mailbox_code IN (
    'general_action',
    'general_record',
    'world_record',
    'world_history',
    'nation_history',
    'battle_result',
    'battle_detail'
);
