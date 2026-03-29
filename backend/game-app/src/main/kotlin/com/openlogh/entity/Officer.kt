package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

private const val COMPAT_UNSET_LONG: Long = Long.MIN_VALUE
private const val COMPAT_UNSET_INT: Int = Int.MIN_VALUE
private const val COMPAT_UNSET_SHORT: Short = Short.MIN_VALUE

@Entity
@Table(name = "officer")
class Officer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,

    @Column(name = "world_id", nullable = false)
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

    @Column(name = "location_state", nullable = false)
    var locationState: String = "planet",

    @Column(nullable = false)
    var peerage: String = "none",

    /** 분류: military(군인) / politician(정치가) */
    @Column(name = "career_type", nullable = false)
    var careerType: String = "military",

    /** 출자: noble(귀족) / knight(제국기사) / commoner(평민) / exile(망명자) / citizen(시민) */
    @Column(name = "origin_type", nullable = false)
    var originType: String = "commoner",

    /** 정치공작 능력치 (최대 8,000) */
    @Column(name = "political_ops", nullable = false)
    var politicalOps: Int = 0,

    /** 정보공작 능력치 (최대 8,000) */
    @Column(name = "intel_ops", nullable = false)
    var intelOps: Int = 0,

    /** 군사공작 능력치 (최대 8,000) */
    @Column(name = "military_ops", nullable = false)
    var militaryOps: Int = 0,

    /** 명성 포인트 (세션간 누적) */
    @Column(name = "fame_points", nullable = false)
    var famePoints: Int = 0,

    @Column(name = "npc_state", nullable = false)
    var npcState: Short = 0,

    @Column(name = "npc_org")
    var npcOrg: Long? = null,

    @Column(nullable = false)
    var affinity: Short = 0,

    @Column(name = "birth_year", nullable = false)
    var birthYear: Short = 180,

    @Column(name = "death_year", nullable = false)
    var deathYear: Short = 300,

    @Column(nullable = false)
    var picture: String = "",

    @Column(name = "image_server", nullable = false)
    var imageServer: Short = 0,

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

    /** 공전: 함재기 전투력 */
    @Column(name = "fighter_skill", nullable = false)
    var fighterSkill: Short = 30,

    @Column(name = "fighter_skill_exp", nullable = false)
    var fighterSkillExp: Short = 0,

    /** 육전: 지상전/강습양륙 */
    @Column(name = "ground_combat", nullable = false)
    var groundCombat: Short = 30,

    @Column(name = "ground_combat_exp", nullable = false)
    var groundCombatExp: Short = 0,

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

    @Column(nullable = false)
    var injury: Short = 0,

    @Column(nullable = false)
    var experience: Int = 0,

    @Column(nullable = false)
    var dedication: Int = 0,

    @Column(nullable = false)
    var rank: Short = 0,

    @Column(name = "stationed_system", nullable = false)
    var stationedSystem: Int = 0,

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

    /** 정략 커맨드 포인트 (PCP) */
    @Column(name = "pcp", nullable = false)
    var pcp: Int = 10,

    /** 군사 커맨드 포인트 (MCP) */
    @Column(name = "mcp", nullable = false)
    var mcp: Int = 10,

    /** PCP 누적 사용량 (경험치 연동용) */
    @Column(name = "pcp_used_total", nullable = false)
    var pcpUsedTotal: Int = 0,

    /** MCP 누적 사용량 (경험치 연동용) */
    @Column(name = "mcp_used_total", nullable = false)
    var mcpUsedTotal: Int = 0,

    /** 레거시 호환 */
    @Column(name = "command_points", nullable = false)
    var commandPoints: Int = 10,

    @Column(nullable = false)
    var influence: Int = 0,

    @Column(name = "command_end_time")
    var commandEndTime: OffsetDateTime? = null,

    /** 본거지 행성 ID — 기함 파괴 시 자동 귀환 (CHAR-15, PERS-06) */
    @Column(name = "home_planet_id")
    var homePlanetId: Long? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_turn", columnDefinition = "jsonb", nullable = false)
    var lastTurn: MutableMap<String, Any> = mutableMapOf(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var meta: MutableMap<String, Any> = mutableMapOf(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var penalty: MutableMap<String, Any> = mutableMapOf(),

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),

    // === Old field name aliases (non-persisted constructor params) ===
    worldId: Long = COMPAT_UNSET_LONG,
    nationId: Long = COMPAT_UNSET_LONG,
    cityId: Long = COMPAT_UNSET_LONG,
    troopId: Long = COMPAT_UNSET_LONG,
    gold: Int = COMPAT_UNSET_INT,
    rice: Int = COMPAT_UNSET_INT,
    crew: Int = COMPAT_UNSET_INT,
    crewType: Short = COMPAT_UNSET_SHORT,
    train: Short = COMPAT_UNSET_SHORT,
    atmos: Short = COMPAT_UNSET_SHORT,
    strength: Short = COMPAT_UNSET_SHORT,
    strengthExp: Short = COMPAT_UNSET_SHORT,
    intel: Short = COMPAT_UNSET_SHORT,
    intelExp: Short = COMPAT_UNSET_SHORT,
    charm: Short = COMPAT_UNSET_SHORT,
    charmExp: Short = COMPAT_UNSET_SHORT,
    officerLevel: Short = COMPAT_UNSET_SHORT,
) {
    init {
        if (worldId != COMPAT_UNSET_LONG) sessionId = worldId
        if (nationId != COMPAT_UNSET_LONG) factionId = nationId
        if (cityId != COMPAT_UNSET_LONG) planetId = cityId
        if (troopId != COMPAT_UNSET_LONG) fleetId = troopId
        if (gold != COMPAT_UNSET_INT) funds = gold
        if (rice != COMPAT_UNSET_INT) supplies = rice
        if (crew != COMPAT_UNSET_INT) ships = crew
        if (crewType != COMPAT_UNSET_SHORT) shipClass = crewType
        if (train != COMPAT_UNSET_SHORT) training = train
        if (atmos != COMPAT_UNSET_SHORT) morale = atmos
        if (strength != COMPAT_UNSET_SHORT) command = strength
        if (strengthExp != COMPAT_UNSET_SHORT) commandExp = strengthExp
        if (intel != COMPAT_UNSET_SHORT) intelligence = intel
        if (intelExp != COMPAT_UNSET_SHORT) intelligenceExp = intelExp
        if (charm != COMPAT_UNSET_SHORT) administration = charm
        if (charmExp != COMPAT_UNSET_SHORT) administrationExp = charmExp
        if (officerLevel != COMPAT_UNSET_SHORT) rank = officerLevel
    }
}
