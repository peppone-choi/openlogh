@file:Suppress("unused")

package com.openlogh.entity

// ========== Extension Properties: Officer (old field names) ==========

var Officer.worldId: Long
    get() = sessionId
    set(value) { sessionId = value }

var Officer.nationId: Long
    get() = factionId
    set(value) { factionId = value }

var Officer.cityId: Long
    get() = planetId
    set(value) { planetId = value }

var Officer.troopId: Long
    get() = fleetId
    set(value) { fleetId = value }

var Officer.gold: Int
    get() = funds
    set(value) { funds = value }

var Officer.rice: Int
    get() = supplies
    set(value) { supplies = value }

var Officer.crew: Int
    get() = ships
    set(value) { ships = value }

var Officer.crewType: Short
    get() = shipClass
    set(value) { shipClass = value }

var Officer.train: Short
    get() = training
    set(value) { training = value }

var Officer.atmos: Short
    get() = morale
    set(value) { morale = value }

var Officer.strength: Short
    get() = command
    set(value) { command = value }

var Officer.intel: Short
    get() = intelligence
    set(value) { intelligence = value }

var Officer.charm: Short
    get() = administration
    set(value) { administration = value }

var Officer.officerLevel: Short
    get() = rank
    set(value) { rank = value }

var Officer.officerCity: Int
    get() = stationedSystem
    set(value) { stationedSystem = value }

var Officer.strengthExp: Short
    get() = commandExp
    set(value) { commandExp = value }

var Officer.intelExp: Short
    get() = intelligenceExp
    set(value) { intelligenceExp = value }

var Officer.charmExp: Short
    get() = administrationExp
    set(value) { administrationExp = value }

// ========== Extension Properties: Planet (old field names) ==========

var Planet.worldId: Long
    get() = sessionId
    set(value) { sessionId = value }

var Planet.nationId: Long
    get() = factionId
    set(value) { factionId = value }

var Planet.agri: Int
    get() = production
    set(value) { production = value }

var Planet.agriMax: Int
    get() = productionMax
    set(value) { productionMax = value }

var Planet.comm: Int
    get() = commerce
    set(value) { commerce = value }

var Planet.commMax: Int
    get() = commerceMax
    set(value) { commerceMax = value }

var Planet.secu: Int
    get() = security
    set(value) { security = value }

var Planet.secuMax: Int
    get() = securityMax
    set(value) { securityMax = value }

var Planet.def: Int
    get() = orbitalDefense
    set(value) { orbitalDefense = value }

var Planet.defMax: Int
    get() = orbitalDefenseMax
    set(value) { orbitalDefenseMax = value }

var Planet.wall: Int
    get() = fortress
    set(value) { fortress = value }

var Planet.wallMax: Int
    get() = fortressMax
    set(value) { fortressMax = value }

var Planet.pop: Int
    get() = population
    set(value) { population = value }

var Planet.popMax: Int
    get() = populationMax
    set(value) { populationMax = value }

var Planet.trust: Float
    get() = approval
    set(value) { approval = value }

var Planet.trade: Int
    get() = tradeRoute
    set(value) { tradeRoute = value }

// ========== Extension Properties: Faction (old field names) ==========

var Faction.worldId: Long
    get() = sessionId
    set(value) { sessionId = value }

var Faction.gold: Int
    get() = funds
    set(value) { funds = value }

var Faction.rice: Int
    get() = supplies
    set(value) { supplies = value }

var Faction.level: Short
    get() = factionRank
    set(value) { factionRank = value }

var Faction.capitalCityId: Long?
    get() = capitalPlanetId
    set(value) { capitalPlanetId = value }

var Faction.gennum: Int
    get() = officerCount
    set(value) { officerCount = value }

var Faction.chiefGeneralId: Long
    get() = supremeCommanderId
    set(value) { supremeCommanderId = value }

var Faction.typeCode: String
    get() = factionType
    set(value) { factionType = value }

// ========== Extension Properties: OfficerTurn (old field names) ==========

var OfficerTurn.generalId: Long
    get() = officerId
    set(value) { officerId = value }

var OfficerTurn.worldId: Long
    get() = sessionId
    set(value) { sessionId = value }

// ========== Extension Properties: FactionTurn (old field names) ==========

var FactionTurn.nationId: Long
    get() = factionId
    set(value) { factionId = value }

var FactionTurn.worldId: Long
    get() = sessionId
    set(value) { sessionId = value }

// ========== Extension Property: Diplomacy ==========

var Diplomacy.destNationId: Long
    get() = destFactionId
    set(value) { destFactionId = value }

var Diplomacy.worldId: Long
    get() = sessionId
    set(value) { sessionId = value }

// ========== Extension Properties: Message (old field names) ==========

var Message.worldId: Long
    get() = sessionId
    set(value) { sessionId = value }

// ========== Extension Properties: Record (old field names) ==========

var Record.worldId: Long
    get() = sessionId
    set(value) { sessionId = value }
