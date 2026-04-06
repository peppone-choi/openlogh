-- V31: Add dual PCP/MCP command point columns to officer table
-- Splits the single command_points field into separate Political and Military pools

ALTER TABLE officer ADD COLUMN pcp INT NOT NULL DEFAULT 5;
ALTER TABLE officer ADD COLUMN mcp INT NOT NULL DEFAULT 5;
ALTER TABLE officer ADD COLUMN pcp_max INT NOT NULL DEFAULT 5;
ALTER TABLE officer ADD COLUMN mcp_max INT NOT NULL DEFAULT 5;

-- Migrate existing command_points data: split evenly, odd point goes to MCP
UPDATE officer SET
    pcp = command_points / 2,
    mcp = command_points - command_points / 2;

-- Set pcp_max and mcp_max based on officer_level (rank)
UPDATE officer SET
    pcp_max = CASE officer_level
        WHEN 0 THEN 5
        WHEN 1 THEN 6
        WHEN 2 THEN 7
        WHEN 3 THEN 8
        WHEN 4 THEN 10
        WHEN 5 THEN 12
        WHEN 6 THEN 15
        WHEN 7 THEN 18
        WHEN 8 THEN 22
        WHEN 9 THEN 27
        WHEN 10 THEN 35
        ELSE 5
    END,
    mcp_max = CASE officer_level
        WHEN 0 THEN 5
        WHEN 1 THEN 6
        WHEN 2 THEN 7
        WHEN 3 THEN 8
        WHEN 4 THEN 10
        WHEN 5 THEN 12
        WHEN 6 THEN 15
        WHEN 7 THEN 18
        WHEN 8 THEN 22
        WHEN 9 THEN 27
        WHEN 10 THEN 35
        ELSE 5
    END;

-- NOTE: command_points column is NOT dropped yet for backward compatibility
