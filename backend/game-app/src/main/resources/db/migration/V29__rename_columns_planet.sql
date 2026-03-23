-- V29: Rename columns in planet (formerly city) to LOGH domain
-- Maps OpenSamguk city fields to LOGH planet fields

-- FK rename
ALTER TABLE planet RENAME COLUMN nation_id TO faction_id;

-- Population
ALTER TABLE planet RENAME COLUMN pop TO population;
ALTER TABLE planet RENAME COLUMN pop_max TO population_max;

-- Production (agriculture → starship/supply production)
ALTER TABLE planet RENAME COLUMN agri TO production;
ALTER TABLE planet RENAME COLUMN agri_max TO production_max;

-- Commerce
ALTER TABLE planet RENAME COLUMN comm TO commerce;
ALTER TABLE planet RENAME COLUMN comm_max TO commerce_max;

-- Security
ALTER TABLE planet RENAME COLUMN secu TO security;
ALTER TABLE planet RENAME COLUMN secu_max TO security_max;

-- Approval (民心 trust → citizen approval rating)
ALTER TABLE planet RENAME COLUMN trust TO approval;

-- Trade route (무역로 trade → 항로 trade_route)
ALTER TABLE planet RENAME COLUMN trade TO trade_route;

-- Orbital defense (성벽 def → orbital_defense)
ALTER TABLE planet RENAME COLUMN def TO orbital_defense;
ALTER TABLE planet RENAME COLUMN def_max TO orbital_defense_max;

-- Fortress (성벽 wall → fortress)
ALTER TABLE planet RENAME COLUMN wall TO fortress;
ALTER TABLE planet RENAME COLUMN wall_max TO fortress_max;

-- Garrison set (officer deployment capacity)
ALTER TABLE planet RENAME COLUMN officer_set TO garrison_set;

-- Map reference rename
ALTER TABLE planet RENAME COLUMN map_city_id TO map_planet_id;
