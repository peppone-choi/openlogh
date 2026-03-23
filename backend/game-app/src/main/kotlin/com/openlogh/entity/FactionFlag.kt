package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.io.Serializable
import java.time.OffsetDateTime

enum class FactionAuxKey {
    can_국기변경, can_국호변경, did_특성초토화, can_무작위수도이전,
    can_대검병사용, can_극병사용, can_화시병사용, can_원융노병사용,
    can_산저병사용, can_상병사용, can_음귀병사용, can_무희사용, can_화륜차사용
}

data class FactionFlagId(
    var factionId: Long = 0,
    var key: FactionAuxKey = FactionAuxKey.can_국기변경,
) : Serializable

@Entity
@Table(name = "faction_flag")
@IdClass(FactionFlagId::class)
class FactionFlag(
    @Column(name = "world_id", nullable = false)
    var sessionId: Long = 0,

    @Id
    @Column(name = "faction_id", nullable = false)
    var factionId: Long = 0,

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "key", nullable = false, columnDefinition = "faction_aux_key")
    var key: FactionAuxKey = FactionAuxKey.can_국기변경,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var value: Any = true,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
