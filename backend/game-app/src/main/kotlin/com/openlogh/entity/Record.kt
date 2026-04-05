package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(
    name = "records",
    indexes = [
        Index(name = "idx_records_session_type", columnList = "session_id,record_type"),
        Index(name = "idx_records_dest_type", columnList = "dest_id,record_type"),
        Index(name = "idx_records_created", columnList = "created_at"),
    ]
)
class Record(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    /**
     * Record type classification:
     * - officer_action: 개인 동향 (장교의 행동 로그)
     * - officer_record: 장교 기록 (개인 역사에 남는 중요 기록)
     * - session_record: 장교 동향 전체 (모든 장교에게 공개되는 동향)
     * - session_history: 은하 정세 (은하의 중요 사건)
     * - faction_history: 진영 기록 (진영 소속 장교에게만 공개)
     * - battle_result: 전투 결과
     * - battle_detail: 전투 상세
     */
    @Column(name = "record_type", nullable = false, length = 50)
    var recordType: String = "",

    /**
     * Source officer ID (actor who performed the action)
     */
    @Column(name = "src_id")
    var srcId: Long? = null,

    /**
     * Destination ID (target entity):
     * - officer_action, officer_record: officerId
     * - faction_history: factionId
     * - session_record, session_history: null
     * - battle_result, battle_detail: officerId or null
     */
    @Column(name = "dest_id")
    var destId: Long? = null,

    /**
     * Game year when this record was created
     */
    @Column(name = "year", nullable = false)
    var year: Int = 0,

    /**
     * Game month when this record was created
     */
    @Column(name = "month", nullable = false)
    var month: Int = 0,

    /**
     * Record payload (structured data)
     * Common fields:
     * - message: String (display text with color tags)
     * - Additional fields vary by record_type
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var payload: MutableMap<String, Any> = mutableMapOf(),

    /**
     * Real-world timestamp
     */
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
