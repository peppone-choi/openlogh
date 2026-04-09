package com.openlogh.model

/**
 * Phase 12 D-16: OperationPlan lifecycle states.
 * PENDING → ACTIVE → COMPLETED | CANCELLED.
 * No DRAFT state — OperationPlanCommand issues operations directly.
 */
enum class OperationStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    CANCELLED,
}
