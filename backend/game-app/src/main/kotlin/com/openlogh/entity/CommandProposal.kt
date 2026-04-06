package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "command_proposal")
class CommandProposal(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "proposer_id", nullable = false)
    var proposerId: Long = 0,           // 제안한 장교 ID

    @Column(name = "approver_id")
    var approverId: Long? = null,       // 승인/거부한 상급자 ID (null = 미처리)

    @Column(name = "command_code", nullable = false, length = 64)
    var commandCode: String = "",       // 한국어 커맨드 코드 (예: "작전계획")

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var args: MutableMap<String, Any> = mutableMapOf(),  // 커맨드 인자

    @Column(nullable = false, length = 16)
    var status: String = "PENDING",     // PENDING / APPROVED / REJECTED

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "resolved_at")
    var resolvedAt: OffsetDateTime? = null,

    @Column(name = "result_log", length = 2048)
    var resultLog: String? = null,      // 승인 실행 결과 로그 (JSON)
)
