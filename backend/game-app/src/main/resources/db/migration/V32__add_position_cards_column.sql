-- Add position_cards JSONB column to officer table
-- Every officer gets at least PERSONAL and CAPTAIN cards by default
ALTER TABLE officer ADD COLUMN position_cards jsonb NOT NULL DEFAULT '["PERSONAL","CAPTAIN"]'::jsonb;
