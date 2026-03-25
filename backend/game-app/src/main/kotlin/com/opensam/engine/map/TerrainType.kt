package com.opensam.engine.map

enum class TerrainType(val roadSpeed: Float, val offRoadSpeed: Float) {
    PLAIN(1.0f, 0.6f),
    FOREST(0.8f, 0.4f),
    MOUNTAIN(0.5f, 0.2f),
    WATER(0.8f, 0.0f),
    DESERT(0.9f, 0.5f),
}
