-- Star system: grid-level navigable point on galaxy map
CREATE TABLE star_system (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES session_state(id),
    map_star_id INT NOT NULL,
    name_ko VARCHAR(50) NOT NULL,
    name_en VARCHAR(50) NOT NULL,
    faction_id BIGINT NOT NULL DEFAULT 0,
    x INT NOT NULL,
    y INT NOT NULL,
    spectral_type VARCHAR(2) NOT NULL DEFAULT 'A',
    star_rgb JSONB NOT NULL DEFAULT '[255,255,255]',
    level SMALLINT NOT NULL DEFAULT 5,
    region SMALLINT NOT NULL DEFAULT 1,
    fortress_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
    fortress_gun_power INT NOT NULL DEFAULT 0,
    fortress_gun_range INT NOT NULL DEFAULT 0,
    fortress_gun_cooldown INT NOT NULL DEFAULT 0,
    garrison_capacity INT NOT NULL DEFAULT 0,
    UNIQUE(session_id, map_star_id)
);
CREATE INDEX idx_star_system_session ON star_system(session_id);
CREATE INDEX idx_star_system_faction ON star_system(session_id, faction_id);

-- Routes between star systems
CREATE TABLE star_route (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES session_state(id),
    from_star_id INT NOT NULL,
    to_star_id INT NOT NULL,
    distance INT NOT NULL DEFAULT 1,
    UNIQUE(session_id, from_star_id, to_star_id)
);
CREATE INDEX idx_star_route_session ON star_route(session_id);
CREATE INDEX idx_star_route_from ON star_route(session_id, from_star_id);

-- Add star_system_id FK to planet table
ALTER TABLE planet ADD COLUMN star_system_id BIGINT DEFAULT NULL;
-- Add fortress-specific columns to planet
ALTER TABLE planet ADD COLUMN fortress_type VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE planet ADD COLUMN fortress_gun_power INT NOT NULL DEFAULT 0;
ALTER TABLE planet ADD COLUMN fortress_gun_range INT NOT NULL DEFAULT 0;
ALTER TABLE planet ADD COLUMN fortress_gun_cooldown INT NOT NULL DEFAULT 0;
ALTER TABLE planet ADD COLUMN garrison_capacity INT NOT NULL DEFAULT 0;
