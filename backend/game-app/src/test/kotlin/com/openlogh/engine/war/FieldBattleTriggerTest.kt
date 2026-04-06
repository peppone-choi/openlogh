package com.openlogh.engine.war

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Message
import com.openlogh.entity.Record
import com.openlogh.entity.SessionState
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.RecordRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Example
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.query.FluentQuery
import java.time.OffsetDateTime
import java.util.Optional
import java.util.function.Function

class FieldBattleTriggerTest {

    // ─── stub repositories ───────────────────────────────────────────────────

    private class StubOfficerRepository : OfficerRepository {
        val saved = mutableListOf<Officer>()
        private val store = mutableMapOf<Long, Officer>()

        fun put(g: Officer) { store[g.id] = g }

        override fun <S : Officer> save(entity: S): S {
            saved += entity
            store[entity.id] = entity
            @Suppress("UNCHECKED_CAST") return entity as S
        }
        override fun <S : Officer> saveAll(entities: Iterable<S>): List<S> = entities.map { save(it) }
        override fun findById(id: Long): Optional<Officer> = Optional.ofNullable(store[id])
        override fun findAll(): List<Officer> = store.values.toList()
        override fun findAll(sort: Sort): List<Officer> = findAll()
        override fun findAll(pageable: Pageable): Page<Officer> = Page.empty()
        override fun <S : Officer> findAll(example: Example<S>): List<S> = emptyList()
        override fun <S : Officer> findAll(example: Example<S>, sort: Sort): List<S> = emptyList()
        override fun <S : Officer> findAll(example: Example<S>, pageable: Pageable): Page<S> = Page.empty()
        override fun findAllById(ids: Iterable<Long>): List<Officer> = ids.mapNotNull { store[it] }
        override fun count(): Long = store.size.toLong()
        override fun <S : Officer> count(example: Example<S>): Long = 0
        override fun deleteById(id: Long) { store.remove(id) }
        override fun delete(entity: Officer) { store.remove(entity.id) }
        override fun deleteAllById(ids: Iterable<Long>) { ids.forEach { store.remove(it) } }
        override fun deleteAll(entities: Iterable<Officer>) { entities.forEach { store.remove(it.id) } }
        override fun deleteAll() { store.clear() }
        override fun existsById(id: Long): Boolean = id in store
        override fun <S : Officer> exists(example: Example<S>): Boolean = false
        override fun <S : Officer, R : Any> findBy(example: Example<S>, queryFunction: Function<FluentQuery.FetchableFluentQuery<S>, R>): R = throw UnsupportedOperationException()
        override fun flush() {}
        override fun <S : Officer> saveAndFlush(entity: S): S = save(entity)
        override fun <S : Officer> saveAllAndFlush(entities: Iterable<S>): List<S> = saveAll(entities)
        override fun deleteAllInBatch(entities: Iterable<Officer>) {}
        override fun deleteAllByIdInBatch(ids: Iterable<Long>) {}
        override fun deleteAllInBatch() {}
        override fun getReferenceById(id: Long): Officer = store[id] ?: throw NoSuchElementException()
        @Deprecated("Use getReferenceById") override fun getById(id: Long): Officer = getReferenceById(id)
        @Deprecated("Use getReferenceById") override fun getOne(id: Long): Officer = getReferenceById(id)
        override fun <S : Officer> findOne(example: Example<S>): Optional<S> = Optional.empty()
        override fun findBySessionId(sessionId: Long): List<Officer> = store.values.filter { it.sessionId == sessionId }
        override fun findByFactionId(factionId: Long): List<Officer> = store.values.filter { it.factionId == factionId }
        override fun findByPlanetId(planetId: Long): List<Officer> = store.values.filter { it.planetId == planetId }
        override fun findByUserId(userId: Long): List<Officer> = emptyList()
        override fun findBySessionIdAndUserId(sessionId: Long, userId: Long): List<Officer> = emptyList()
        override fun findBySessionIdAndPlanetIdIn(sessionId: Long, planetIds: List<Long>): List<Officer> = emptyList()
        override fun findBySessionIdAndCommandEndTimeBefore(sessionId: Long, time: OffsetDateTime): List<Officer> = emptyList()
        override fun findByFleetId(fleetId: Long): List<Officer> = emptyList()
        override fun findBySessionIdAndFactionId(sessionId: Long, factionId: Long): List<Officer> = emptyList()
        override fun findByNameAndSessionId(name: String, sessionId: Long): Officer? = null
        override fun getAverageStats(sessionId: Long, factionId: Long) = com.openlogh.repository.OfficerAverageStats()
    }

    private class StubPlanetRepository(private val cities: Map<Long, Planet> = emptyMap()) : PlanetRepository {
        override fun <S : Planet> save(entity: S): S { @Suppress("UNCHECKED_CAST") return entity as S }
        override fun <S : Planet> saveAll(entities: Iterable<S>): List<S> = entities.toList()
        override fun findById(id: Long): Optional<Planet> = Optional.ofNullable(cities[id])
        override fun findAll(): List<Planet> = cities.values.toList()
        override fun findAll(sort: Sort): List<Planet> = findAll()
        override fun findAll(pageable: Pageable): Page<Planet> = Page.empty()
        override fun <S : Planet> findAll(example: Example<S>): List<S> = emptyList()
        override fun <S : Planet> findAll(example: Example<S>, sort: Sort): List<S> = emptyList()
        override fun <S : Planet> findAll(example: Example<S>, pageable: Pageable): Page<S> = Page.empty()
        override fun findAllById(ids: Iterable<Long>): List<Planet> = ids.mapNotNull { cities[it] }
        override fun count(): Long = cities.size.toLong()
        override fun <S : Planet> count(example: Example<S>): Long = 0
        override fun deleteById(id: Long) {}
        override fun delete(entity: Planet) {}
        override fun deleteAllById(ids: Iterable<Long>) {}
        override fun deleteAll(entities: Iterable<Planet>) {}
        override fun deleteAll() {}
        override fun existsById(id: Long): Boolean = id in cities
        override fun <S : Planet> exists(example: Example<S>): Boolean = false
        override fun <S : Planet, R : Any> findBy(example: Example<S>, queryFunction: Function<FluentQuery.FetchableFluentQuery<S>, R>): R = throw UnsupportedOperationException()
        override fun flush() {}
        override fun <S : Planet> saveAndFlush(entity: S): S = save(entity)
        override fun <S : Planet> saveAllAndFlush(entities: Iterable<S>): List<S> = saveAll(entities)
        override fun deleteAllInBatch(entities: Iterable<Planet>) {}
        override fun deleteAllByIdInBatch(ids: Iterable<Long>) {}
        override fun deleteAllInBatch() {}
        override fun getReferenceById(id: Long): Planet = cities[id] ?: throw NoSuchElementException()
        @Deprecated("Use getReferenceById") override fun getById(id: Long): Planet = getReferenceById(id)
        @Deprecated("Use getReferenceById") override fun getOne(id: Long): Planet = getReferenceById(id)
        override fun <S : Planet> findOne(example: Example<S>): Optional<S> = Optional.empty()
        override fun findBySessionId(sessionId: Long): List<Planet> = emptyList()
        override fun findByFactionId(factionId: Long): List<Planet> = emptyList()
    }

    private class StubMessageRepository : MessageRepository {
        val saved = mutableListOf<Message>()
        override fun <S : Message> save(entity: S): S { saved += entity; @Suppress("UNCHECKED_CAST") return entity as S }
        override fun <S : Message> saveAll(entities: Iterable<S>): List<S> {
            val list = entities.toList()
            list.forEach { save(it) }
            @Suppress("UNCHECKED_CAST") return list as List<S>
        }
        override fun findById(id: Long): Optional<Message> = Optional.empty()
        override fun findAll(): List<Message> = saved
        override fun findAll(sort: Sort): List<Message> = saved
        override fun findAll(pageable: Pageable): Page<Message> = Page.empty()
        override fun <S : Message> findAll(example: Example<S>): List<S> = emptyList()
        override fun <S : Message> findAll(example: Example<S>, sort: Sort): List<S> = emptyList()
        override fun <S : Message> findAll(example: Example<S>, pageable: Pageable): Page<S> = Page.empty()
        override fun findAllById(ids: Iterable<Long>): List<Message> = emptyList()
        override fun count(): Long = saved.size.toLong()
        override fun <S : Message> count(example: Example<S>): Long = 0
        override fun deleteById(id: Long) {}
        override fun delete(entity: Message) {}
        override fun deleteAllById(ids: Iterable<Long>) {}
        override fun deleteAll(entities: Iterable<Message>) {}
        override fun deleteAll() {}
        override fun existsById(id: Long): Boolean = false
        override fun <S : Message> exists(example: Example<S>): Boolean = false
        override fun <S : Message, R : Any> findBy(example: Example<S>, queryFunction: Function<FluentQuery.FetchableFluentQuery<S>, R>): R = throw UnsupportedOperationException()
        override fun flush() {}
        override fun <S : Message> saveAndFlush(entity: S): S = save(entity)
        override fun <S : Message> saveAllAndFlush(entities: Iterable<S>): List<S> = saveAll(entities)
        override fun deleteAllInBatch(entities: Iterable<Message>) {}
        override fun deleteAllByIdInBatch(ids: Iterable<Long>) {}
        override fun deleteAllInBatch() {}
        override fun getReferenceById(id: Long): Message = throw UnsupportedOperationException()
        @Deprecated("Use getReferenceById") override fun getById(id: Long): Message = throw UnsupportedOperationException()
        @Deprecated("Use getReferenceById") override fun getOne(id: Long): Message = throw UnsupportedOperationException()
        override fun <S : Message> findOne(example: Example<S>): Optional<S> = Optional.empty()
        override fun findByDestIdOrderBySentAtDesc(destId: Long): List<Message> = emptyList()
        override fun findByDestIdAndIdGreaterThanOrderBySentAtDesc(destId: Long, id: Long): List<Message> = emptyList()
        override fun findBySessionIdAndMailboxCodeOrderBySentAtDesc(sessionId: Long, mailboxCode: String): List<Message> = emptyList()
        override fun findBySessionIdAndMailboxCodeAndSrcIdOrderBySentAtDesc(sessionId: Long, mailboxCode: String, srcId: Long): List<Message> = emptyList()
        override fun findBySrcIdAndMailboxCodeOrderBySentAtDesc(srcId: Long, mailboxCode: String): List<Message> = emptyList()
        override fun findBySessionIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(sessionId: Long, mailboxCode: String, id: Long): List<Message> = emptyList()
        override fun findByIdGreaterThanOrderBySentAtDesc(id: Long): List<Message> = emptyList()
        override fun findByDestIdAndMailboxCodeOrderBySentAtDesc(destId: Long, mailboxCode: String): List<Message> = emptyList()
        override fun findBySessionIdAndMailboxCodeAndDestIdOrderBySentAtDesc(sessionId: Long, mailboxCode: String, destId: Long): List<Message> = emptyList()
        override fun findBySessionIdAndMailboxTypeOrderBySentAtDesc(sessionId: Long, mailboxType: String): List<Message> = emptyList()
        override fun findBySessionIdAndMailboxTypeAndIdLessThanOrderBySentAtDesc(sessionId: Long, mailboxType: String, id: Long): List<Message> = emptyList()
        override fun findByDestIdAndMailboxTypeOrderBySentAtDesc(destId: Long, mailboxType: String): List<Message> = emptyList()
        override fun findByDestIdAndMailboxTypeAndIdLessThanOrderBySentAtDesc(destId: Long, mailboxType: String, id: Long): List<Message> = emptyList()
        override fun findByDestIdAndMailboxTypeAndIdGreaterThanOrderBySentAtDesc(destId: Long, mailboxType: String, id: Long): List<Message> = emptyList()
        override fun findByDestIdAndMailboxCodeAndIdLessThanOrderByIdDesc(destId: Long, mailboxCode: String, id: Long): List<Message> = emptyList()
        override fun findByDestIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(destId: Long, mailboxCode: String, id: Long): List<Message> = emptyList()
        override fun findByDestIdAndMailboxTypeAndMessageTypeOrderBySentAtDesc(destId: Long, mailboxType: String, messageType: String): List<Message> = emptyList()
        override fun findBySessionIdAndYearAndMonthOrderBySentAtAsc(sessionId: Long, year: Int, month: Int): List<Message> = emptyList()
        override fun findBySessionIdAndYearOrderBySentAtDesc(sessionId: Long, year: Int): List<Message> = emptyList()
        override fun findConversationByMailboxTypeAndOwnerId(mailboxType: String, ownerId: Long): List<Message> = emptyList()
        override fun findConversationByMailboxTypeAndOwnerIdAndIdLessThan(mailboxType: String, ownerId: Long, beforeId: Long): List<Message> = emptyList()
        override fun findConversationByMailboxTypeAndOwnerIdAndIdGreaterThan(mailboxType: String, ownerId: Long, sinceId: Long): List<Message> = emptyList()
    }

    private class StubRecordRepository : RecordRepository {
        val saved = mutableListOf<Record>()
        override fun <S : Record> save(entity: S): S { saved += entity; @Suppress("UNCHECKED_CAST") return entity as S }
        override fun <S : Record> saveAll(entities: Iterable<S>): List<S> {
            val list = entities.toList()
            list.forEach { save(it) }
            @Suppress("UNCHECKED_CAST") return list as List<S>
        }
        override fun findById(id: Long): Optional<Record> = Optional.empty()
        override fun findAll(): List<Record> = saved
        override fun findAll(sort: Sort): List<Record> = saved
        override fun findAll(pageable: Pageable): Page<Record> = Page.empty()
        override fun <S : Record> findAll(example: Example<S>): List<S> = emptyList()
        override fun <S : Record> findAll(example: Example<S>, sort: Sort): List<S> = emptyList()
        override fun <S : Record> findAll(example: Example<S>, pageable: Pageable): Page<S> = Page.empty()
        override fun findAllById(ids: Iterable<Long>): List<Record> = emptyList()
        override fun count(): Long = saved.size.toLong()
        override fun <S : Record> count(example: Example<S>): Long = 0
        override fun deleteById(id: Long) {}
        override fun delete(entity: Record) {}
        override fun deleteAllById(ids: Iterable<Long>) {}
        override fun deleteAll(entities: Iterable<Record>) {}
        override fun deleteAll() {}
        override fun existsById(id: Long): Boolean = false
        override fun <S : Record> exists(example: Example<S>): Boolean = false
        override fun <S : Record, R : Any> findBy(example: Example<S>, queryFunction: Function<FluentQuery.FetchableFluentQuery<S>, R>): R = throw UnsupportedOperationException()
        override fun flush() {}
        override fun <S : Record> saveAndFlush(entity: S): S = save(entity)
        override fun <S : Record> saveAllAndFlush(entities: Iterable<S>): List<S> = saveAll(entities)
        override fun deleteAllInBatch(entities: Iterable<Record>) {}
        override fun deleteAllByIdInBatch(ids: Iterable<Long>) {}
        override fun deleteAllInBatch() {}
        override fun getReferenceById(id: Long): Record = throw UnsupportedOperationException()
        @Deprecated("Use getReferenceById") override fun getById(id: Long): Record = throw UnsupportedOperationException()
        @Deprecated("Use getReferenceById") override fun getOne(id: Long): Record = throw UnsupportedOperationException()
        override fun <S : Record> findOne(example: Example<S>): Optional<S> = Optional.empty()
        override fun findBySessionIdAndRecordTypeOrderByCreatedAtDesc(sessionId: Long, recordType: String): List<Record> = emptyList()
        override fun findByDestIdAndRecordTypeOrderByCreatedAtDesc(destId: Long, recordType: String): List<Record> = emptyList()
        override fun findBySessionIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(sessionId: Long, recordType: String, beforeId: Long): List<Record> = emptyList()
        override fun findByDestIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(destId: Long, recordType: String, beforeId: Long): List<Record> = emptyList()
        override fun findBySessionIdAndRecordTypeAndIdGreaterThanOrderByCreatedAtDesc(sessionId: Long, recordType: String, sinceId: Long): List<Record> = emptyList()
        override fun findByDestIdAndRecordTypeAndIdGreaterThanOrderByCreatedAtDesc(destId: Long, recordType: String, sinceId: Long): List<Record> = emptyList()
        override fun findBySessionIdAndYearAndMonth(sessionId: Long, year: Int, month: Int): List<Record> = emptyList()
        override fun findBySessionIdAndRecordTypeInAndYearAndMonthOrderByCreatedAtDesc(sessionId: Long, recordType: List<String>, year: Int, month: Int): List<Record> = emptyList()
        override fun findBySessionIdAndRecordTypesWithPagination(sessionId: Long, recordTypes: List<String>, beforeId: Long?): List<Record> = emptyList()
        override fun findByDestIdAndRecordTypesWithPagination(destId: Long, recordTypes: List<String>, beforeId: Long?): List<Record> = emptyList()
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun makeGeneral(
        id: Long,
        factionId: Long,
        planetId: Long = 1L,
        ships: Int = 1000,
        lastTurn: MutableMap<String, Any> = mutableMapOf(),
    ) = Officer(
        id = id,
        sessionId = 1,
        name = "장수$id",
        factionId = factionId,
        planetId = planetId,
        ships = ships,
        supplies = 10000,
        leadership = 60.toShort(),
        command = 60.toShort(),
        intelligence = 60.toShort(),
        turnTime = OffsetDateTime.now(),
    ).also { it.lastTurn = lastTurn }

    private fun makeCity(id: Long) = Planet(
        id = id,
        sessionId = 1,
        name = "도시$id",
        factionId = 1,
        orbitalDefense = 0,
        orbitalDefenseMax = 1000,
        fortress = 0,
        fortressMax = 1000,
        population = 10000,
        populationMax = 50000,
        level = 0,
    )

    private fun makeWorld() = SessionState(id = 1.toShort()).also {
        it.currentYear = 200.toShort()
        it.currentMonth = 1.toShort()
    }

    private fun makeTrigger(
        interceptorCity: Planet,
        generalRepo: StubOfficerRepository = StubOfficerRepository(),
        messageRepo: StubMessageRepository = StubMessageRepository(),
        recordRepo: StubRecordRepository = StubRecordRepository(),
    ) = FieldBattleTrigger(
        fieldBattleService = FieldBattleService(),
        officerRepository = generalRepo,
        planetRepository = StubPlanetRepository(mapOf(interceptorCity.id to interceptorCity)),
        messageRepository = messageRepo,
        recordRepository = recordRepo,
    )

    // ─── tests ────────────────────────────────────────────────────────────────

    @Test
    fun `요격 intercepts mover on matching road segment`() {
        val city = makeCity(10L)
        val interceptor = makeGeneral(
            id = 2L, factionId = 2L, planetId = 10L,
            lastTurn = mutableMapOf(
                "action" to "요격",
                "originCityId" to 10L,
                "interceptionTargetCityId" to 20L,
            ),
        )
        val mover = makeGeneral(id = 1L, factionId = 1L, planetId = 20L)
        val allGenerals = listOf(mover, interceptor)

        val generalRepo = StubOfficerRepository()
        val trigger = makeTrigger(city, generalRepo)

        val triggered = trigger.checkAndTrigger(mover, "이동", fromCityId = 10L, allGenerals = allGenerals, world = makeWorld())

        assertTrue(triggered, "Field battle should have been triggered")
        assertTrue(generalRepo.saved.any { it.id == mover.id })
        assertTrue(generalRepo.saved.any { it.id == interceptor.id })
    }

    @Test
    fun `요격 does not trigger for same-nation general`() {
        val city = makeCity(10L)
        val interceptor = makeGeneral(
            id = 2L, factionId = 1L, planetId = 10L,
            lastTurn = mutableMapOf(
                "action" to "요격",
                "originCityId" to 10L,
                "interceptionTargetCityId" to 20L,
            ),
        )
        val mover = makeGeneral(id = 1L, factionId = 1L, planetId = 20L)
        val allGenerals = listOf(mover, interceptor)

        val trigger = makeTrigger(city)
        val triggered = trigger.checkAndTrigger(mover, "이동", fromCityId = 10L, allGenerals = allGenerals, world = makeWorld())

        assertFalse(triggered, "Should not trigger between same-nation generals")
    }

    @Test
    fun `요격 does not trigger for non-move action`() {
        val city = makeCity(10L)
        val interceptor = makeGeneral(
            id = 2L, factionId = 2L, planetId = 10L,
            lastTurn = mutableMapOf(
                "action" to "요격",
                "originCityId" to 10L,
                "interceptionTargetCityId" to 20L,
            ),
        )
        val mover = makeGeneral(id = 1L, factionId = 1L, planetId = 20L)
        val allGenerals = listOf(mover, interceptor)

        val trigger = makeTrigger(city)
        val triggered = trigger.checkAndTrigger(mover, "훈련", fromCityId = 10L, allGenerals = allGenerals, world = makeWorld())

        assertFalse(triggered, "훈련 is not a move action — should not trigger")
    }

    @Test
    fun `요격 does not trigger when road segment does not match`() {
        val city = makeCity(10L)
        val interceptor = makeGeneral(
            id = 2L, factionId = 2L, planetId = 10L,
            lastTurn = mutableMapOf(
                "action" to "요격",
                "originCityId" to 30L,
                "interceptionTargetCityId" to 40L,
            ),
        )
        val mover = makeGeneral(id = 1L, factionId = 1L, planetId = 20L)
        val allGenerals = listOf(mover, interceptor)

        val trigger = makeTrigger(city)
        val triggered = trigger.checkAndTrigger(mover, "이동", fromCityId = 10L, allGenerals = allGenerals, world = makeWorld())

        assertFalse(triggered, "Intercept road segment mismatch — should not trigger")
    }

    @Test
    fun `순찰 intercepts mover passing through patrol city`() {
        val city = makeCity(10L)
        val patrol = makeGeneral(
            id = 2L, factionId = 2L, planetId = 10L,
            lastTurn = mutableMapOf(
                "action" to "순찰",
                "patrolCityId" to 10L,
            ),
        )
        val mover = makeGeneral(id = 1L, factionId = 1L, planetId = 20L)
        val allGenerals = listOf(mover, patrol)

        val generalRepo = StubOfficerRepository()
        val trigger = makeTrigger(city, generalRepo)
        val triggered = trigger.checkAndTrigger(mover, "이동", fromCityId = 10L, allGenerals = allGenerals, world = makeWorld())

        assertTrue(triggered, "순찰 should intercept mover passing through patrol city")
    }

    @Test
    fun `interceptor with zero ships does not trigger`() {
        val city = makeCity(10L)
        val interceptor = makeGeneral(
            id = 2L, factionId = 2L, planetId = 10L, ships = 0,
            lastTurn = mutableMapOf(
                "action" to "요격",
                "originCityId" to 10L,
                "interceptionTargetCityId" to 20L,
            ),
        )
        val mover = makeGeneral(id = 1L, factionId = 1L, planetId = 20L)
        val allGenerals = listOf(mover, interceptor)

        val trigger = makeTrigger(city)
        val triggered = trigger.checkAndTrigger(mover, "이동", fromCityId = 10L, allGenerals = allGenerals, world = makeWorld())

        assertFalse(triggered, "Interceptor with zero ships should not trigger")
    }

    @Test
    fun `logs are persisted when field battle triggers`() {
        val city = makeCity(10L)
        val interceptor = makeGeneral(
            id = 2L, factionId = 2L, planetId = 10L,
            lastTurn = mutableMapOf(
                "action" to "요격",
                "originCityId" to 10L,
                "interceptionTargetCityId" to 20L,
            ),
        )
        val mover = makeGeneral(id = 1L, factionId = 1L, planetId = 20L)
        val allGenerals = listOf(mover, interceptor)

        val messageRepo = StubMessageRepository()
        val recordRepo = StubRecordRepository()
        val trigger = makeTrigger(city, messageRepo = messageRepo, recordRepo = recordRepo)

        trigger.checkAndTrigger(mover, "이동", fromCityId = 10L, allGenerals = allGenerals, world = makeWorld())

        assertTrue(messageRepo.saved.isNotEmpty(), "Battle messages should be persisted")
        assertTrue(recordRepo.saved.isNotEmpty(), "Battle records should be persisted")
    }

    @Test
    fun `요격 reverse road direction also triggers`() {
        val city = makeCity(20L)
        val interceptor = makeGeneral(
            id = 2L, factionId = 2L, planetId = 20L,
            lastTurn = mutableMapOf(
                "action" to "요격",
                "originCityId" to 20L,
                "interceptionTargetCityId" to 10L,
            ),
        )
        val mover = makeGeneral(id = 1L, factionId = 1L, planetId = 20L)
        val allGenerals = listOf(mover, interceptor)

        val generalRepo = StubOfficerRepository()
        val trigger = makeTrigger(city, generalRepo)
        val triggered = trigger.checkAndTrigger(mover, "이동", fromCityId = 10L, allGenerals = allGenerals, world = makeWorld())

        assertTrue(triggered, "Reverse road direction should also match")
    }
}
