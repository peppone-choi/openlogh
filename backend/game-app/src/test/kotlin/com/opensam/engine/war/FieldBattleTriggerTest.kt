package com.opensam.engine.war

import com.opensam.entity.City
import com.opensam.entity.General
import com.opensam.entity.Message
import com.opensam.entity.Record
import com.opensam.entity.WorldState
import com.opensam.repository.CityRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.MessageRepository
import com.opensam.repository.RecordRepository
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

    private class StubGeneralRepository : GeneralRepository {
        val saved = mutableListOf<General>()
        private val store = mutableMapOf<Long, General>()

        fun put(g: General) { store[g.id] = g }

        override fun <S : General> save(entity: S): S {
            saved += entity
            store[entity.id] = entity
            @Suppress("UNCHECKED_CAST") return entity as S
        }
        override fun <S : General> saveAll(entities: Iterable<S>): List<S> = entities.map { save(it) }
        override fun findById(id: Long): Optional<General> = Optional.ofNullable(store[id])
        override fun findAll(): List<General> = store.values.toList()
        override fun findAll(sort: Sort): List<General> = findAll()
        override fun findAll(pageable: Pageable): Page<General> = Page.empty()
        override fun <S : General> findAll(example: Example<S>): List<S> = emptyList()
        override fun <S : General> findAll(example: Example<S>, sort: Sort): List<S> = emptyList()
        override fun <S : General> findAll(example: Example<S>, pageable: Pageable): Page<S> = Page.empty()
        override fun findAllById(ids: Iterable<Long>): List<General> = ids.mapNotNull { store[it] }
        override fun count(): Long = store.size.toLong()
        override fun <S : General> count(example: Example<S>): Long = 0
        override fun deleteById(id: Long) { store.remove(id) }
        override fun delete(entity: General) { store.remove(entity.id) }
        override fun deleteAllById(ids: Iterable<Long>) { ids.forEach { store.remove(it) } }
        override fun deleteAll(entities: Iterable<General>) { entities.forEach { store.remove(it.id) } }
        override fun deleteAll() { store.clear() }
        override fun existsById(id: Long): Boolean = id in store
        override fun <S : General> exists(example: Example<S>): Boolean = false
        override fun <S : General, R : Any> findBy(example: Example<S>, queryFunction: Function<FluentQuery.FetchableFluentQuery<S>, R>): R = throw UnsupportedOperationException()
        override fun flush() {}
        override fun <S : General> saveAndFlush(entity: S): S = save(entity)
        override fun <S : General> saveAllAndFlush(entities: Iterable<S>): List<S> = saveAll(entities)
        override fun deleteAllInBatch(entities: Iterable<General>) {}
        override fun deleteAllByIdInBatch(ids: Iterable<Long>) {}
        override fun deleteAllInBatch() {}
        override fun getReferenceById(id: Long): General = store[id] ?: throw NoSuchElementException()
        @Deprecated("Use getReferenceById") override fun getById(id: Long): General = getReferenceById(id)
        @Deprecated("Use getReferenceById") override fun getOne(id: Long): General = getReferenceById(id)
        override fun <S : General> findOne(example: Example<S>): Optional<S> = Optional.empty()
        override fun findByWorldId(worldId: Long): List<General> = store.values.filter { it.worldId == worldId }
        override fun findByNationId(nationId: Long): List<General> = store.values.filter { it.nationId == nationId }
        override fun findByCityId(cityId: Long): List<General> = store.values.filter { it.cityId == cityId }
        override fun findByUserId(userId: Long): List<General> = emptyList()
        override fun findByWorldIdAndUserId(worldId: Long, userId: Long): List<General> = emptyList()
        override fun findByWorldIdAndCityIdIn(worldId: Long, cityIds: List<Long>): List<General> = emptyList()
        override fun findByWorldIdAndCommandEndTimeBefore(worldId: Long, time: OffsetDateTime): List<General> = emptyList()
        override fun findByTroopId(troopId: Long): List<General> = emptyList()
        override fun findByWorldIdAndNationId(worldId: Long, nationId: Long): List<General> = emptyList()
        override fun findByNameAndWorldId(name: String, worldId: Long): General? = null
        override fun getAverageStats(worldId: Long, nationId: Long) = com.opensam.repository.GeneralAverageStats()
    }

    private class StubCityRepository(private val cities: Map<Long, City> = emptyMap()) : CityRepository {
        override fun <S : City> save(entity: S): S { @Suppress("UNCHECKED_CAST") return entity as S }
        override fun <S : City> saveAll(entities: Iterable<S>): List<S> = entities.toList()
        override fun findById(id: Long): Optional<City> = Optional.ofNullable(cities[id])
        override fun findAll(): List<City> = cities.values.toList()
        override fun findAll(sort: Sort): List<City> = findAll()
        override fun findAll(pageable: Pageable): Page<City> = Page.empty()
        override fun <S : City> findAll(example: Example<S>): List<S> = emptyList()
        override fun <S : City> findAll(example: Example<S>, sort: Sort): List<S> = emptyList()
        override fun <S : City> findAll(example: Example<S>, pageable: Pageable): Page<S> = Page.empty()
        override fun findAllById(ids: Iterable<Long>): List<City> = ids.mapNotNull { cities[it] }
        override fun count(): Long = cities.size.toLong()
        override fun <S : City> count(example: Example<S>): Long = 0
        override fun deleteById(id: Long) {}
        override fun delete(entity: City) {}
        override fun deleteAllById(ids: Iterable<Long>) {}
        override fun deleteAll(entities: Iterable<City>) {}
        override fun deleteAll() {}
        override fun existsById(id: Long): Boolean = id in cities
        override fun <S : City> exists(example: Example<S>): Boolean = false
        override fun <S : City, R : Any> findBy(example: Example<S>, queryFunction: Function<FluentQuery.FetchableFluentQuery<S>, R>): R = throw UnsupportedOperationException()
        override fun flush() {}
        override fun <S : City> saveAndFlush(entity: S): S = save(entity)
        override fun <S : City> saveAllAndFlush(entities: Iterable<S>): List<S> = saveAll(entities)
        override fun deleteAllInBatch(entities: Iterable<City>) {}
        override fun deleteAllByIdInBatch(ids: Iterable<Long>) {}
        override fun deleteAllInBatch() {}
        override fun getReferenceById(id: Long): City = cities[id] ?: throw NoSuchElementException()
        @Deprecated("Use getReferenceById") override fun getById(id: Long): City = getReferenceById(id)
        @Deprecated("Use getReferenceById") override fun getOne(id: Long): City = getReferenceById(id)
        override fun <S : City> findOne(example: Example<S>): Optional<S> = Optional.empty()
        override fun findByWorldId(worldId: Long): List<City> = emptyList()
        override fun findByNationId(nationId: Long): List<City> = emptyList()
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
        override fun findByWorldIdAndMailboxCodeOrderBySentAtDesc(worldId: Long, mailboxCode: String): List<Message> = emptyList()
        override fun findByWorldIdAndMailboxCodeAndSrcIdOrderBySentAtDesc(worldId: Long, mailboxCode: String, srcId: Long): List<Message> = emptyList()
        override fun findBySrcIdAndMailboxCodeOrderBySentAtDesc(srcId: Long, mailboxCode: String): List<Message> = emptyList()
        override fun findByWorldIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(worldId: Long, mailboxCode: String, id: Long): List<Message> = emptyList()
        override fun findByIdGreaterThanOrderBySentAtDesc(id: Long): List<Message> = emptyList()
        override fun findByDestIdAndMailboxCodeOrderBySentAtDesc(destId: Long, mailboxCode: String): List<Message> = emptyList()
        override fun findByWorldIdAndMailboxCodeAndDestIdOrderBySentAtDesc(worldId: Long, mailboxCode: String, destId: Long): List<Message> = emptyList()
        override fun findByWorldIdAndMailboxTypeOrderBySentAtDesc(worldId: Long, mailboxType: String): List<Message> = emptyList()
        override fun findByWorldIdAndMailboxTypeAndIdLessThanOrderBySentAtDesc(worldId: Long, mailboxType: String, id: Long): List<Message> = emptyList()
        override fun findByDestIdAndMailboxTypeOrderBySentAtDesc(destId: Long, mailboxType: String): List<Message> = emptyList()
        override fun findByDestIdAndMailboxTypeAndIdLessThanOrderBySentAtDesc(destId: Long, mailboxType: String, id: Long): List<Message> = emptyList()
        override fun findByDestIdAndMailboxTypeAndIdGreaterThanOrderBySentAtDesc(destId: Long, mailboxType: String, id: Long): List<Message> = emptyList()
        override fun findByDestIdAndMailboxCodeAndIdLessThanOrderByIdDesc(destId: Long, mailboxCode: String, id: Long): List<Message> = emptyList()
        override fun findByDestIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(destId: Long, mailboxCode: String, id: Long): List<Message> = emptyList()
        override fun findByDestIdAndMailboxTypeAndMessageTypeOrderBySentAtDesc(destId: Long, mailboxType: String, messageType: String): List<Message> = emptyList()
        override fun findByWorldIdAndYearAndMonthOrderBySentAtAsc(worldId: Long, year: Int, month: Int): List<Message> = emptyList()
        override fun findByWorldIdAndYearOrderBySentAtDesc(worldId: Long, year: Int): List<Message> = emptyList()
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
        override fun findByWorldIdAndRecordTypeOrderByCreatedAtDesc(worldId: Long, recordType: String): List<Record> = emptyList()
        override fun findByDestIdAndRecordTypeOrderByCreatedAtDesc(destId: Long, recordType: String): List<Record> = emptyList()
        override fun findByWorldIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(worldId: Long, recordType: String, beforeId: Long): List<Record> = emptyList()
        override fun findByDestIdAndRecordTypeAndIdLessThanOrderByCreatedAtDesc(destId: Long, recordType: String, beforeId: Long): List<Record> = emptyList()
        override fun findByWorldIdAndRecordTypeAndIdGreaterThanOrderByCreatedAtDesc(worldId: Long, recordType: String, sinceId: Long): List<Record> = emptyList()
        override fun findByDestIdAndRecordTypeAndIdGreaterThanOrderByCreatedAtDesc(destId: Long, recordType: String, sinceId: Long): List<Record> = emptyList()
        override fun findByWorldIdAndYearAndMonth(worldId: Long, year: Int, month: Int): List<Record> = emptyList()
        override fun findByWorldIdAndRecordTypeInAndYearAndMonthOrderByCreatedAtDesc(worldId: Long, recordType: List<String>, year: Int, month: Int): List<Record> = emptyList()
        override fun findByWorldIdAndRecordTypesWithPagination(worldId: Long, recordTypes: List<String>, beforeId: Long?): List<Record> = emptyList()
        override fun findByDestIdAndRecordTypesWithPagination(destId: Long, recordTypes: List<String>, beforeId: Long?): List<Record> = emptyList()
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun makeGeneral(
        id: Long,
        nationId: Long,
        cityId: Long = 1L,
        crew: Int = 1000,
        lastTurn: MutableMap<String, Any> = mutableMapOf(),
    ) = General(
        id = id,
        worldId = 1,
        name = "장수$id",
        nationId = nationId,
        cityId = cityId,
        crew = crew,
        rice = 10000,
        leadership = 60.toShort(),
        strength = 60.toShort(),
        intel = 60.toShort(),
        turnTime = OffsetDateTime.now(),
    ).also { it.lastTurn = lastTurn }

    private fun makeCity(id: Long) = City(
        id = id,
        worldId = 1,
        name = "도시$id",
        nationId = 1,
        def = 0,
        defMax = 1000,
        wall = 0,
        wallMax = 1000,
        pop = 10000,
        popMax = 50000,
        level = 0,
    )

    private fun makeWorld() = WorldState(id = 1.toShort()).also {
        it.currentYear = 200.toShort()
        it.currentMonth = 1.toShort()
    }

    private fun makeTrigger(
        interceptorCity: City,
        generalRepo: StubGeneralRepository = StubGeneralRepository(),
        messageRepo: StubMessageRepository = StubMessageRepository(),
        recordRepo: StubRecordRepository = StubRecordRepository(),
    ) = FieldBattleTrigger(
        fieldBattleService = FieldBattleService(),
        generalRepository = generalRepo,
        cityRepository = StubCityRepository(mapOf(interceptorCity.id to interceptorCity)),
        messageRepository = messageRepo,
        recordRepository = recordRepo,
    )

    // ─── tests ────────────────────────────────────────────────────────────────

    @Test
    fun `요격 intercepts mover on matching road segment`() {
        val city = makeCity(10L)
        val interceptor = makeGeneral(
            id = 2L, nationId = 2L, cityId = 10L,
            lastTurn = mutableMapOf(
                "action" to "요격",
                "originCityId" to 10L,
                "interceptionTargetCityId" to 20L,
            ),
        )
        val mover = makeGeneral(id = 1L, nationId = 1L, cityId = 20L)
        val allGenerals = listOf(mover, interceptor)

        val generalRepo = StubGeneralRepository()
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
            id = 2L, nationId = 1L, cityId = 10L,
            lastTurn = mutableMapOf(
                "action" to "요격",
                "originCityId" to 10L,
                "interceptionTargetCityId" to 20L,
            ),
        )
        val mover = makeGeneral(id = 1L, nationId = 1L, cityId = 20L)
        val allGenerals = listOf(mover, interceptor)

        val trigger = makeTrigger(city)
        val triggered = trigger.checkAndTrigger(mover, "이동", fromCityId = 10L, allGenerals = allGenerals, world = makeWorld())

        assertFalse(triggered, "Should not trigger between same-nation generals")
    }

    @Test
    fun `요격 does not trigger for non-move action`() {
        val city = makeCity(10L)
        val interceptor = makeGeneral(
            id = 2L, nationId = 2L, cityId = 10L,
            lastTurn = mutableMapOf(
                "action" to "요격",
                "originCityId" to 10L,
                "interceptionTargetCityId" to 20L,
            ),
        )
        val mover = makeGeneral(id = 1L, nationId = 1L, cityId = 20L)
        val allGenerals = listOf(mover, interceptor)

        val trigger = makeTrigger(city)
        val triggered = trigger.checkAndTrigger(mover, "훈련", fromCityId = 10L, allGenerals = allGenerals, world = makeWorld())

        assertFalse(triggered, "훈련 is not a move action — should not trigger")
    }

    @Test
    fun `요격 does not trigger when road segment does not match`() {
        val city = makeCity(10L)
        val interceptor = makeGeneral(
            id = 2L, nationId = 2L, cityId = 10L,
            lastTurn = mutableMapOf(
                "action" to "요격",
                "originCityId" to 30L,
                "interceptionTargetCityId" to 40L,
            ),
        )
        val mover = makeGeneral(id = 1L, nationId = 1L, cityId = 20L)
        val allGenerals = listOf(mover, interceptor)

        val trigger = makeTrigger(city)
        val triggered = trigger.checkAndTrigger(mover, "이동", fromCityId = 10L, allGenerals = allGenerals, world = makeWorld())

        assertFalse(triggered, "Intercept road segment mismatch — should not trigger")
    }

    @Test
    fun `순찰 intercepts mover passing through patrol city`() {
        val city = makeCity(10L)
        val patrol = makeGeneral(
            id = 2L, nationId = 2L, cityId = 10L,
            lastTurn = mutableMapOf(
                "action" to "순찰",
                "patrolCityId" to 10L,
            ),
        )
        val mover = makeGeneral(id = 1L, nationId = 1L, cityId = 20L)
        val allGenerals = listOf(mover, patrol)

        val generalRepo = StubGeneralRepository()
        val trigger = makeTrigger(city, generalRepo)
        val triggered = trigger.checkAndTrigger(mover, "이동", fromCityId = 10L, allGenerals = allGenerals, world = makeWorld())

        assertTrue(triggered, "순찰 should intercept mover passing through patrol city")
    }

    @Test
    fun `interceptor with zero crew does not trigger`() {
        val city = makeCity(10L)
        val interceptor = makeGeneral(
            id = 2L, nationId = 2L, cityId = 10L, crew = 0,
            lastTurn = mutableMapOf(
                "action" to "요격",
                "originCityId" to 10L,
                "interceptionTargetCityId" to 20L,
            ),
        )
        val mover = makeGeneral(id = 1L, nationId = 1L, cityId = 20L)
        val allGenerals = listOf(mover, interceptor)

        val trigger = makeTrigger(city)
        val triggered = trigger.checkAndTrigger(mover, "이동", fromCityId = 10L, allGenerals = allGenerals, world = makeWorld())

        assertFalse(triggered, "Interceptor with zero crew should not trigger")
    }

    @Test
    fun `logs are persisted when field battle triggers`() {
        val city = makeCity(10L)
        val interceptor = makeGeneral(
            id = 2L, nationId = 2L, cityId = 10L,
            lastTurn = mutableMapOf(
                "action" to "요격",
                "originCityId" to 10L,
                "interceptionTargetCityId" to 20L,
            ),
        )
        val mover = makeGeneral(id = 1L, nationId = 1L, cityId = 20L)
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
            id = 2L, nationId = 2L, cityId = 20L,
            lastTurn = mutableMapOf(
                "action" to "요격",
                "originCityId" to 20L,
                "interceptionTargetCityId" to 10L,
            ),
        )
        val mover = makeGeneral(id = 1L, nationId = 1L, cityId = 20L)
        val allGenerals = listOf(mover, interceptor)

        val generalRepo = StubGeneralRepository()
        val trigger = makeTrigger(city, generalRepo)
        val triggered = trigger.checkAndTrigger(mover, "이동", fromCityId = 10L, allGenerals = allGenerals, world = makeWorld())

        assertTrue(triggered, "Reverse road direction should also match")
    }
}
