-- V34: Add unit_type column to fleet, create unit_crew join table
-- Phase 05 Plan 01: Organization & Fleet Structure foundation

-- Fleet table: add unit type and composition columns
ALTER TABLE fleet ADD COLUMN unit_type VARCHAR(20) NOT NULL DEFAULT 'FLEET';
ALTER TABLE fleet ADD COLUMN max_units INT NOT NULL DEFAULT 60;
ALTER TABLE fleet ADD COLUMN current_units INT NOT NULL DEFAULT 0;
ALTER TABLE fleet ADD COLUMN max_crew INT NOT NULL DEFAULT 10;
ALTER TABLE fleet ADD COLUMN planet_id BIGINT NULL;

CREATE INDEX idx_fleet_unit_type ON fleet(unit_type);
CREATE INDEX idx_fleet_planet_id ON fleet(planet_id);

-- Unit crew: tracks officer assignments to fleet crew slots
CREATE TABLE unit_crew (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    fleet_id BIGINT NOT NULL REFERENCES fleet(id) ON DELETE CASCADE,
    officer_id BIGINT NOT NULL REFERENCES officer(id) ON DELETE CASCADE,
    slot_role VARCHAR(30) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(fleet_id, officer_id)
);

CREATE INDEX idx_unit_crew_fleet ON unit_crew(fleet_id);
CREATE INDEX idx_unit_crew_officer ON unit_crew(officer_id);
