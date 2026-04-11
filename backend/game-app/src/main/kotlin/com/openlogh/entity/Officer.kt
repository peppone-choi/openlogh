package com.openlogh.entity

import com.openlogh.model.PositionCard
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "officer")
class Officer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "user_id")
    var userId: Long? = null,

    @Column(nullable = false)
    var name: String = "",

    @Column(name = "faction_id", nullable = false)
    var factionId: Long = 0,

    @Column(name = "planet_id", nullable = false)
    var planetId: Long = 0,

    @Column(name = "fleet_id", nullable = false)
    var fleetId: Long = 0,

    @Column(name = "npc_state", nullable = false)
    var npcState: Short = 0,

    @Column(name = "npc_org")
    var npcOrg: Long? = null,

    @Column(nullable = false)
    var affinity: Short = 0,

    @Column(name = "born_year", nullable = false)
    var bornYear: Short = 180,

    @Column(name = "dead_year", nullable = false)
    var deadYear: Short = 300,

    @Column(nullable = false)
    var picture: String = "",

    @Column(name = "image_server", nullable = false)
    var imageServer: Short = 0,

    // 8-stat system
    @Column(nullable = false)
    var leadership: Short = 50,

    @Column(name = "leadership_exp", nullable = false)
    var leadershipExp: Short = 0,

    @Column(nullable = false)
    var command: Short = 50,

    @Column(name = "command_exp", nullable = false)
    var commandExp: Short = 0,

    @Column(nullable = false)
    var intelligence: Short = 50,

    @Column(name = "intelligence_exp", nullable = false)
    var intelligenceExp: Short = 0,

    @Column(nullable = false)
    var politics: Short = 50,

    @Column(name = "politics_exp", nullable = false)
    var politicsExp: Short = 0,

    @Column(nullable = false)
    var administration: Short = 50,

    @Column(name = "administration_exp", nullable = false)
    var administrationExp: Short = 0,

    @Column(nullable = false)
    var mobility: Short = 50,

    @Column(name = "mobility_exp", nullable = false)
    var mobilityExp: Short = 0,

    @Column(nullable = false)
    var attack: Short = 50,

    @Column(name = "attack_exp", nullable = false)
    var attackExp: Short = 0,

    @Column(nullable = false)
    var defense: Short = 50,

    @Column(name = "defense_exp", nullable = false)
    var defenseExp: Short = 0,

    @Column(name = "dex_1", nullable = false)
    var dex1: Int = 0,

    @Column(name = "dex_2", nullable = false)
    var dex2: Int = 0,

    @Column(name = "dex_3", nullable = false)
    var dex3: Int = 0,

    @Column(name = "dex_4", nullable = false)
    var dex4: Int = 0,

    @Column(name = "dex_5", nullable = false)
    var dex5: Int = 0,

    @Column(name = "dex_6", nullable = false)
    var dex6: Int = 0,

    @Column(name = "dex_7", nullable = false)
    var dex7: Int = 0,

    @Column(name = "dex_8", nullable = false)
    var dex8: Int = 0,

    @Column(nullable = false)
    var injury: Short = 0,

    @Column(nullable = false)
    var experience: Int = 0,

    @Column(nullable = false)
    var dedication: Int = 0,

    @Column(name = "officer_level", nullable = false)
    var officerLevel: Short = 0,

    @Column(name = "officer_planet", nullable = false)
    var officerPlanet: Int = 0,

    @Column(nullable = false)
    var permission: String = "normal",

    @Column(nullable = false)
    var funds: Int = 1000,

    @Column(nullable = false)
    var supplies: Int = 1000,

    @Column(nullable = false)
    var ships: Int = 0,

    @Column(name = "ship_class", nullable = false)
    var shipClass: Short = 0,

    @Column(nullable = false)
    var training: Short = 0,

    @Column(nullable = false)
    var morale: Short = 0,

    @Column(name = "flagship_code", nullable = false)
    var flagshipCode: String = "None",

    @Column(name = "equip_code", nullable = false)
    var equipCode: String = "None",

    @Column(name = "engine_code", nullable = false)
    var engineCode: String = "None",

    @Column(name = "accessory_code", nullable = false)
    var accessoryCode: String = "None",

    @Column(name = "owner_name", nullable = false)
    var ownerName: String = "",

    @Column(nullable = false)
    var newmsg: Short = 0,

    @Column(name = "turn_time", nullable = false)
    var turnTime: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "recent_war_time")
    var recentWarTime: OffsetDateTime? = null,

    @Column(name = "make_limit", nullable = false)
    var makeLimit: Short = 0,

    @Column(name = "kill_turn")
    var killTurn: Short? = null,

    @Column(name = "block_state", nullable = false)
    var blockState: Short = 0,

    @Column(name = "ded_level", nullable = false)
    var dedLevel: Short = 0,

    @Column(name = "exp_level", nullable = false)
    var expLevel: Short = 0,

    @Column(nullable = false)
    var age: Short = 20,

    @Column(name = "start_age", nullable = false)
    var startAge: Short = 20,

    @Column(nullable = false)
    var belong: Short = 1,

    @Column(nullable = false)
    var betray: Short = 0,

    @Column(name = "personal_code", nullable = false)
    var personalCode: String = "None",

    @Column(name = "special_code", nullable = false)
    var specialCode: String = "None",

    @Column(name = "spec_age", nullable = false)
    var specAge: Short = 0,

    @Column(name = "special2_code", nullable = false)
    var special2Code: String = "None",

    @Column(name = "spec2_age", nullable = false)
    var spec2Age: Short = 0,

    @Column(name = "defence_train", nullable = false)
    var defenceTrain: Short = 80,

    @Column(name = "tournament_state", nullable = false)
    var tournamentState: Short = 0,

    /** @deprecated Use pcp/mcp instead. Kept for backward compatibility. */
    @Column(name = "command_points", nullable = false)
    var commandPoints: Int = 10,

    @Column(nullable = false)
    var pcp: Int = 5,

    @Column(nullable = false)
    var mcp: Int = 5,

    @Column(name = "pcp_max", nullable = false)
    var pcpMax: Int = 5,

    @Column(name = "mcp_max", nullable = false)
    var mcpMax: Int = 5,

    @Column(name = "command_end_time")
    var commandEndTime: OffsetDateTime? = null,

    @Column(name = "pos_x", nullable = false)
    var posX: Float = 0f,

    @Column(name = "pos_y", nullable = false)
    var posY: Float = 0f,

    @Column(name = "dest_x")
    var destX: Float? = null,

    @Column(name = "dest_y")
    var destY: Float? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "position_cards", columnDefinition = "jsonb", nullable = false)
    var positionCards: MutableList<String> = mutableListOf("PERSONAL", "CAPTAIN"),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_turn", columnDefinition = "jsonb", nullable = false)
    var lastTurn: MutableMap<String, Any> = mutableMapOf(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var meta: MutableMap<String, Any> = mutableMapOf(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var penalty: MutableMap<String, Any> = mutableMapOf(),

    @Column(name = "merit_points", nullable = false)
    var meritPoints: Int = 0,

    @Column(name = "evaluation_points", nullable = false)
    var evaluationPoints: Int = 0,

    @Column(name = "fame_points", nullable = false)
    var famePoints: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(nullable = false)
    var personality: String = "BALANCED",

    @Column(name = "last_access_at")
    var lastAccessAt: OffsetDateTime? = null,

    /**
     * Configured return planet (帰還惑星) for tactical injury warp.
     * When this officer's flagship is destroyed, they are warped here instead
     * of the faction capital. Null = fall back to faction capital → current planet.
     * Source: gin7 manual p51 — 戦死/負傷 처리 귀환성.
     */
    @Column(name = "return_planet_id")
    var returnPlanetId: Long? = null,

    /**
     * Highest rank of medals held by this officer (叙勲 勲章 랭크).
     * 0 = no medals, 1..N = increasing medal importance.
     * gin7 manual p34 rank ladder 第三法則: tie-breaker after 功績 (merit).
     * Source: gin7 manual p35 — 叙勲 커맨드 결과.
     */
    @Column(name = "medal_rank", nullable = false)
    var medalRank: Short = 0,

    /**
     * Total count of medals awarded to this officer.
     * Used as a secondary tie-breaker under medalRank.
     */
    @Column(name = "medal_count", nullable = false)
    var medalCount: Short = 0,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    /** Returns position cards as typed enum values, skipping any unrecognized names. */
    fun getPositionCardEnums(): List<PositionCard> =
        positionCards.mapNotNull { runCatching { PositionCard.valueOf(it) }.getOrNull() }

    /** Checks if this officer holds a specific position card. */
    fun hasPositionCard(card: PositionCard): Boolean = card.name in positionCards

    /**
     * Checks whether this officer can receive another position card.
     * gin7 manual p26: 최대 보유 카드 수 16매.
     */
    fun canAcceptAdditionalPositionCard(): Boolean =
        positionCards.size < MAX_POSITION_CARDS

    companion object {
        /**
         * Maximum number of position (duty authority) cards an officer may hold.
         * Source: gin7 manual p26 (Chapter 3 — 직무권한카드 상한).
         * See gap analysis D2/E54.
         */
        const val MAX_POSITION_CARDS: Int = 16
    }
}
