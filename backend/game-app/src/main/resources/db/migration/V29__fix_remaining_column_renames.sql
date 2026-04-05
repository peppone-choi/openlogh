-- V29: Fix remaining column renames missed in V28

-- auction table
ALTER TABLE auction RENAME COLUMN buyer_general_id TO buyer_officer_id;
ALTER TABLE auction RENAME COLUMN host_general_id TO host_officer_id;
ALTER TABLE auction RENAME COLUMN seller_general_id TO seller_officer_id;
ALTER INDEX IF EXISTS idx_auction_seller_general_id RENAME TO idx_auction_seller_officer_id;

-- auction_bid table
ALTER TABLE auction_bid RENAME COLUMN bidder_general_id TO bidder_officer_id;

-- bet_entry table
ALTER TABLE bet_entry RENAME COLUMN general_id TO officer_id;

-- board table
ALTER TABLE board RENAME COLUMN author_general_id TO author_officer_id;
ALTER TABLE board RENAME COLUMN nation_id TO faction_id;

-- board_comment table
ALTER TABLE board_comment RENAME COLUMN author_general_id TO author_officer_id;

-- game_history table
ALTER TABLE game_history RENAME COLUMN winner_nation TO winner_faction;

-- general_access_log → officer_access_log (table rename missed in V28)
ALTER TABLE general_access_log RENAME TO officer_access_log;
ALTER TABLE officer_access_log RENAME COLUMN general_id TO officer_id;

-- general_record → officer_record (table rename missed in V28)
ALTER TABLE general_record RENAME TO officer_record;
ALTER TABLE officer_record RENAME COLUMN general_id TO officer_id;

-- hall_of_fame table
ALTER TABLE hall_of_fame RENAME COLUMN general_no TO officer_no;

-- officer table (officer_city is legacy)
ALTER TABLE officer RENAME COLUMN officer_city TO officer_planet;

-- old_faction table
ALTER TABLE old_faction RENAME COLUMN nation TO faction;

-- old_officer table
ALTER TABLE old_officer RENAME COLUMN general_no TO officer_no;

-- planet table
ALTER TABLE planet RENAME COLUMN map_city_id TO map_planet_id;

-- sovereign table
ALTER TABLE sovereign RENAME COLUMN citynum TO planet_count;
ALTER TABLE sovereign RENAME COLUMN nation_count TO faction_count;
ALTER TABLE sovereign RENAME COLUMN nation_hist TO faction_hist;
ALTER TABLE sovereign RENAME COLUMN nation_name TO faction_name;

-- select_pool table
ALTER TABLE select_pool RENAME COLUMN general_id TO officer_id;

-- tournament table
ALTER TABLE tournament RENAME COLUMN general_id TO officer_id;

-- vote_cast table
ALTER TABLE vote_cast RENAME COLUMN general_id TO officer_id;

-- yearbook_history table (JSONB column, keep as-is — rename would break JSON parsing)
-- ALTER TABLE yearbook_history RENAME COLUMN nations TO factions;
-- NOTE: 'nations' is a JSONB field storing historical data snapshots. Renaming requires
-- updating all JSON serialization/deserialization code. Deferred to avoid breakage.
