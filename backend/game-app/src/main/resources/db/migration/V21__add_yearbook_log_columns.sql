ALTER TABLE yearbook_history
    ADD COLUMN global_history JSONB NOT NULL DEFAULT '[]',
    ADD COLUMN global_action JSONB NOT NULL DEFAULT '[]';
