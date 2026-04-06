package com.openlogh.dto

data class StarSystemDto(
    val id: Long,
    val mapStarId: Int,
    val nameKo: String,
    val nameEn: String,
    val factionId: Long,
    val x: Int,
    val y: Int,
    val spectralType: String,
    val starRgb: List<Int>,
    val level: Int,
    val region: Int,
    val fortressType: String,
    val fortressGunPower: Int,
    val fortressGunRange: Int,
    val garrisonCapacity: Int,
    val connections: List<Int>,
    val planetCount: Int,
)

data class StarRouteDto(
    val fromStarId: Int,
    val toStarId: Int,
    val distance: Int,
)

data class GalaxyMapDto(
    val systems: List<StarSystemDto>,
    val routes: List<StarRouteDto>,
    val factionTerritories: Map<Long, List<Int>>,
)
