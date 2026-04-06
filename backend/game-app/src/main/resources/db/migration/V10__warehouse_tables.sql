-- V10: Warehouse tables for logistics system (행성창고 / 부대창고)

-- Planet Warehouse: stores produced units and supplies at planet level
CREATE TABLE planet_warehouse (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT  NOT NULL,
    planet_id       BIGINT  NOT NULL,

    -- Ship counts by class (each unit = 300 ships)
    battleship      INT     NOT NULL DEFAULT 0,
    cruiser         INT     NOT NULL DEFAULT 0,
    destroyer       INT     NOT NULL DEFAULT 0,
    carrier         INT     NOT NULL DEFAULT 0,
    transport       INT     NOT NULL DEFAULT 0,
    hospital        INT     NOT NULL DEFAULT 0,

    -- Crew (승조원) counts by proficiency
    crew_green      INT     NOT NULL DEFAULT 0,
    crew_normal     INT     NOT NULL DEFAULT 0,
    crew_veteran    INT     NOT NULL DEFAULT 0,
    crew_elite      INT     NOT NULL DEFAULT 0,

    -- Resources
    supplies        INT     NOT NULL DEFAULT 0,
    missiles        INT     NOT NULL DEFAULT 0,

    -- Shipyard production flag
    has_shipyard    BOOLEAN NOT NULL DEFAULT FALSE,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_planet_warehouse_session_planet UNIQUE (session_id, planet_id)
);

CREATE INDEX idx_planet_warehouse_session ON planet_warehouse(session_id);
CREATE INDEX idx_planet_warehouse_planet ON planet_warehouse(planet_id);

-- Fleet Warehouse: stores assigned units and supplies per fleet
CREATE TABLE fleet_warehouse (
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT  NOT NULL,
    fleet_id        BIGINT  NOT NULL,

    -- Ship counts by class
    battleship      INT     NOT NULL DEFAULT 0,
    cruiser         INT     NOT NULL DEFAULT 0,
    destroyer       INT     NOT NULL DEFAULT 0,
    carrier         INT     NOT NULL DEFAULT 0,
    transport       INT     NOT NULL DEFAULT 0,
    hospital        INT     NOT NULL DEFAULT 0,

    -- Crew counts by proficiency
    crew_green      INT     NOT NULL DEFAULT 0,
    crew_normal     INT     NOT NULL DEFAULT 0,
    crew_veteran    INT     NOT NULL DEFAULT 0,
    crew_elite      INT     NOT NULL DEFAULT 0,

    -- Resources
    supplies        INT     NOT NULL DEFAULT 0,
    missiles        INT     NOT NULL DEFAULT 0,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_fleet_warehouse_session_fleet UNIQUE (session_id, fleet_id)
);

CREATE INDEX idx_fleet_warehouse_session ON fleet_warehouse(session_id);
CREATE INDEX idx_fleet_warehouse_fleet ON fleet_warehouse(fleet_id);
