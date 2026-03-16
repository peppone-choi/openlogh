# Database Migration Guide

## world_history Migration (Post-Deployment Cleanup)

**Status**: Pending - Run AFTER deploying current code

### Background

World history messages were incorrectly saved to the `message` table with `mailbox_code='world_history'` and `mailbox_type='PUBLIC'` due to architectural violation. The root cause has been fixed - services now use `HistoryService.logWorldHistory()` which correctly saves to the `record` table.

### Pre-Deployment State

**Temporary defensive filter** in `MessageService.getPublicMessages()` (line 83):

```kotlin
val filtered = messages.filter { it.mailboxCode != "world_history" }
```

This prevents existing bad data from displaying while we clean up.

### Migration Steps

**1. Verify Deployment**
Confirm new code is running:

- Services use `HistoryService` for world history events
- No new `world_history` records appear in `message` table

**2. Run Migration Script**

```bash
psql -h <host> -U <user> -d <database> -f migrate-world-history.sql
```

The script will:

- Insert existing `world_history` messages into `record` table
- Show counts for verification
- WAIT for manual confirmation before deletion

**3. Verify Migration**
Check counts match:

```sql
SELECT COUNT(*) FROM message WHERE mailbox_code = 'world_history';
SELECT COUNT(*) FROM record WHERE record_type = 'world_history';
```

**4. Complete Migration**
Uncomment and run the DELETE statement in the script:

```sql
DELETE FROM message WHERE mailbox_code = 'world_history';
```

**5. Deploy Filter Removal**
After successful migration and verification:

- Remove lines 82-83 from `MessageService.kt`
- Deploy updated code

### Rollback Plan

If migration fails:

1. Keep temporary filter in place
2. Investigate data discrepancies
3. Do NOT run DELETE statement
4. Fix issues and retry from Step 2

### Verification Queries

**Check for new violations** (should return 0 after deployment):

```sql
SELECT COUNT(*)
FROM message
WHERE mailbox_code = 'world_history'
  AND sent_at > '<deployment_timestamp>';
```

**Verify record table has events**:

```sql
SELECT record_type, COUNT(*)
FROM record
GROUP BY record_type
ORDER BY count DESC;
```
