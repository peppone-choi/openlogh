package com.openlogh.engine

/**
 * Game time constants for the real-time tick engine.
 *
 * Time model (24x speed):
 *   1 tick  = 1 real second  = 24 game-seconds
 *   300 ticks = 5 real minutes = 7,200 game-seconds  (CP regen interval)
 *   3,600 ticks = 1 real hour   = 86,400 game-seconds (1 game day)
 *   108,000 ticks = 30 real hours = 2,592,000 game-seconds (1 game month)
 */
object GameTimeConstants {

    /** Tick interval in milliseconds (1 tick per second). */
    const val TICK_INTERVAL_MS = 1_000L

    /** Game-seconds that elapse per tick (24x real-time). */
    const val GAME_SECONDS_PER_TICK = 24

    /** Ticks in one game month: 30 real hours = 108,000 ticks. */
    const val TICKS_PER_MONTH = 108_000L

    /** Game-seconds in one game month: 108,000 * 24 = 2,592,000. */
    const val GAME_SECONDS_PER_MONTH = 2_592_000L

    /** CP regeneration interval in ticks: 5 real minutes = 300 ticks. */
    const val CP_REGEN_INTERVAL_TICKS = 300L

    /** Broadcast game-time updates every N ticks (10 seconds). */
    const val TICK_BROADCAST_INTERVAL = 10L

    /** Game-seconds in one game day: 24 * 3,600 = 86,400. */
    const val GAME_SECONDS_PER_DAY = 86_400L

    /** Shipyard auto-production interval: 1 game day = 3,600 ticks. */
    const val SHIPYARD_INTERVAL_TICKS = 3_600L
}
