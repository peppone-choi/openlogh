package com.openlogh.engine.turn.cqrs.persist

import com.openlogh.engine.turn.cqrs.port.IdAllocator
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class SequenceIdAllocator(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${opensam.cqrs.sequence.general:general_id_seq}")
    private val generalSequence: String,
    @Value("\${opensam.cqrs.sequence.city:city_id_seq}")
    private val citySequence: String,
    @Value("\${opensam.cqrs.sequence.nation:nation_id_seq}")
    private val nationSequence: String,
    @Value("\${opensam.cqrs.sequence.troop:troop_id_seq}")
    private val troopSequence: String,
    @Value("\${opensam.cqrs.sequence.diplomacy:diplomacy_id_seq}")
    private val diplomacySequence: String,
    @Value("\${opensam.cqrs.sequence.batch-size:50}")
    private val batchSize: Int,
) : IdAllocator {

    private val generalPool = ArrayDeque<Long>()
    private val cityPool = ArrayDeque<Long>()
    private val nationPool = ArrayDeque<Long>()
    private val troopPool = ArrayDeque<Long>()
    private val diplomacyPool = ArrayDeque<Long>()
    private val validSequenceName = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    override fun nextGeneralId(): Long = nextFromPool(generalPool, generalSequence)

    override fun nextCityId(): Long = nextFromPool(cityPool, citySequence)

    override fun nextNationId(): Long = nextFromPool(nationPool, nationSequence)

    override fun nextTroopId(): Long = nextFromPool(troopPool, troopSequence)

    override fun nextDiplomacyId(): Long = nextFromPool(diplomacyPool, diplomacySequence)

    @Synchronized
    private fun nextFromPool(pool: ArrayDeque<Long>, sequenceName: String): Long {
        if (pool.isEmpty()) {
            refillPool(sequenceName, pool)
        }
        return pool.removeFirst()
    }

    private fun refillPool(sequenceName: String, pool: ArrayDeque<Long>) {
        require(validSequenceName.matches(sequenceName)) {
            "Invalid sequence name: $sequenceName"
        }

        val ids = jdbcTemplate.queryForList(
            "SELECT nextval('$sequenceName') FROM generate_series(1, $batchSize)",
            Long::class.java,
        )
        ids.forEach(pool::addLast)
    }
}
