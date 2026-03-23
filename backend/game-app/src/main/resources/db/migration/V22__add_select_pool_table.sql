CREATE TABLE select_pool (
    id            BIGSERIAL PRIMARY KEY,
    world_id      BIGINT       NOT NULL,
    unique_name   VARCHAR(255) NOT NULL,
    owner_id      BIGINT,
    general_id    BIGINT,
    reserved_until TIMESTAMPTZ,
    info          JSONB        NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_select_pool_world_id ON select_pool (world_id);
CREATE UNIQUE INDEX idx_select_pool_world_name ON select_pool (world_id, unique_name);
