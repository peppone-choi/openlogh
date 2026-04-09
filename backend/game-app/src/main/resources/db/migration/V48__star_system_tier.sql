-- Dualize star_system.level (1..8) into a binary tier column.
-- Legacy level 7 was the 수도 tier in logh.json levelNames, matching Valhalla
-- and Bharat exactly. Everything else becomes REGULAR.

ALTER TABLE star_system
    ADD COLUMN tier VARCHAR(16) NOT NULL DEFAULT 'REGULAR';

UPDATE star_system
SET tier = 'CAPITAL'
WHERE level = 7;

ALTER TABLE star_system
    DROP COLUMN level;
