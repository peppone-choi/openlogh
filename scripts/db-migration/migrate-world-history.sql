-- Migration script: Move world_history messages from Message table to Record table
-- Run this after deploying the new code with HistoryService changes

-- Step 1: Insert world_history messages into Record table
INSERT INTO record (
    world_id,
    record_type,
    content,
    year,
    month,
    created_at
)
SELECT 
    m.world_id,
    'world_history' as record_type,
    m.payload->>'message' as content,
    COALESCE((m.payload->>'year')::smallint, 0) as year,
    COALESCE((m.payload->>'month')::smallint, 0) as month,
    m.sent_at as created_at
FROM message m
WHERE m.mailbox_code = 'world_history'
  AND NOT EXISTS (
      -- Avoid duplicates if script runs multiple times
      SELECT 1 FROM record r 
      WHERE r.world_id = m.world_id 
        AND r.record_type = 'world_history'
        AND r.content = m.payload->>'message'
        AND r.created_at = m.sent_at
  );

-- Step 2: Verify migration
-- Expected: Count should match before deletion
SELECT 
    'Messages to migrate' as description,
    COUNT(*) as count 
FROM message 
WHERE mailbox_code = 'world_history';

SELECT 
    'Records migrated' as description,
    COUNT(*) as count 
FROM record 
WHERE record_type = 'world_history';

-- Step 3: Delete migrated messages (only run after verification)
-- UNCOMMENT AFTER VERIFYING COUNTS MATCH:
-- DELETE FROM message WHERE mailbox_code = 'world_history';

-- Step 4: Final verification
-- Expected: 0 messages with world_history mailbox_code
-- SELECT COUNT(*) as remaining_world_history_messages 
-- FROM message 
-- WHERE mailbox_code = 'world_history';
