CREATE INDEX IF NOT EXISTS idx_diplomacy_world_id ON diplomacy (world_id);
CREATE INDEX IF NOT EXISTS idx_diplomacy_world_nations ON diplomacy (world_id, src_nation_id, dest_nation_id);
