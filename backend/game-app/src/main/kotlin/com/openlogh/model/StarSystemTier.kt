package com.openlogh.model

/**
 * Two-tier classification for star systems.
 *
 * - [CAPITAL] — 수도성계. Political/administrative center of a major faction
 *   (e.g. Valhalla for the Galactic Empire, Bharat for the Free Planets
 *   Alliance). Rendered larger on the galaxy map.
 * - [REGULAR] — 일반성계. Every other system. Uniform visual weight.
 *
 * This replaces the legacy numeric `level` column on `star_system`, which
 * carried 1..8 values copied from OpenSamguk city tiers but was never used
 * for gameplay logic beyond map rendering.
 */
enum class StarSystemTier {
    CAPITAL,
    REGULAR;

    val displayNameKo: String
        get() = when (this) {
            CAPITAL -> "수도성계"
            REGULAR -> "일반성계"
        }
}
