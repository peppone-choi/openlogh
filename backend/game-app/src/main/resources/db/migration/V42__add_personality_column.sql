-- V38: Add personality trait column to officer table for NPC AI personality system
ALTER TABLE officer ADD COLUMN IF NOT EXISTS personality VARCHAR(20) NOT NULL DEFAULT 'BALANCED';

-- Add last_access_at for offline player detection
ALTER TABLE officer ADD COLUMN IF NOT EXISTS last_access_at TIMESTAMPTZ;
