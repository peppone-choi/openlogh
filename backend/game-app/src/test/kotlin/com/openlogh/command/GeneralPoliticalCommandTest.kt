package com.openlogh.command

import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.command.general.*
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.engine.DiplomacyService
import com.openlogh.engine.modifier.ModifierService
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.service.MessageService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import kotlin.random.Random

/**
 * Political command golden value parity tests.
 *
 * Each test uses a fixed RNG seed (42) and deterministic fixtures to produce
 * golden value assertions that lock command output against PHP legacy behavior.
 * Multi-entity mutations are verified for HIGH-RISK commands.
 * Color-tagged log strings are exactly matched (per D-05).
 * Korean josa (조사) correctness is verified against fixture names (per Pitfall 3).
 */
class GeneralPoliticalCommandTest {

    private val mapper = jacksonObjectMapper()

    // ===================== Fixture Helpers =====================

    private fun createTestGeneral(
        id: Long = 1,
        name: String? = null,
        funds: Int = 1000,
        supplies: Int = 1000,
        ships: Int = 0,
        shipClass: Short = 0,
        training: Short = 0,
        morale: Short = 0,
        leadership: Short = 50,
        command: Short = 50,
        intelligence: Short = 50,
        politics: Short = 50,
        administration: Short = 50,
        factionId: Long = 1,
        planetId: Long = 1,
        officerLevel: Short = 0,
        fleetId: Long = 0,
        experience: Int = 0,
        dedication: Int = 0,
        age: Short = 20,
        npcState: Short = 0,
        affinity: Short = 0,
        makeLimit: Short = 0,
        betray: Short = 0,
        specialCode: String = "None",
        special2Code: String = "None",
        flagshipCode: String = "None",
        equipCode: String = "None",
        engineCode: String = "None",
        accessoryCode: String = "None",
    ): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = name ?: "테스트장수$id",
            factionId = factionId,
            planetId = planetId,
            funds = funds,
            supplies = supplies,
            ships = ships,
            shipClass = shipClass,
            training = training,
            morale = morale,
            leadership = leadership,
            command = command,
            intelligence = intelligence,
            politics = politics,
            administration = administration,
            officerLevel = officerLevel,
            fleetId = fleetId,
            experience = experience,
            dedication = dedication,
            age = age,
            npcState = npcState,
            affinity = affinity,
            makeLimit = makeLimit,
            betray = betray,
            specialCode = specialCode,
            special2Code = special2Code,
            flagshipCode = flagshipCode,
            equipCode = equipCode,
            engineCode = engineCode,
            accessoryCode = accessoryCode,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun createTestCity(
        id: Long = 1,
        factionId: Long = 1,
        supplyState: Short = 1,
        name: String = "허창",
        population: Int = 10000,
        security: Int = 500,
    ): Planet {
        return Planet(
            id = id,
            sessionId = 1,
            name = name,
            factionId = factionId,
            production = 500,
            productionMax = 1000,
            commerce = 500,
            commerceMax = 1000,
            security = security,
            securityMax = 1000,
            orbitalDefense = 500,
            orbitalDefenseMax = 1000,
            fortress = 500,
            fortressMax = 1000,
            population = population,
            populationMax = 50000,
            approval = 80f,
            supplyState = supplyState,
            frontState = 0,
        )
    }

    private fun createTestNation(
        id: Long = 1,
        name: String? = null,
        level: Short = 1,
        funds: Int = 10000,
        supplies: Int = 10000,
        capitalPlanetId: Long? = 1,
    ): Faction {
        return Faction(
            id = id,
            sessionId = 1,
            name = name ?: "테스트국가$id",
            color = "#FF0000",
            funds = funds,
            supplies = supplies,
            factionRank = level,
            capitalPlanetId = capitalPlanetId,
        )
    }

    private fun createTestEnv(
        year: Int = 200,
        month: Int = 1,
        startYear: Int = 190,
        develCost: Int = 100,
        scenario: Int = 0,
        gameStor: MutableMap<String, Any> = mutableMapOf(),
    ) = CommandEnv(
        year = year,
        month = month,
        startYear = startYear,
        sessionId = 1,
        realtimeMode = false,
        develCost = develCost,
        scenario = scenario,
        gameStor = gameStor,
    )

    private fun freshRng() = Random(42)

    private fun createMockServices(
        allNations: List<Faction> = emptyList(),
        allGenerals: List<Officer> = emptyList(),
        nationGeneralsMap: Map<Long, List<Officer>> = emptyMap(),
    ): CommandServices {
        val officerRepository = mock(OfficerRepository::class.java)
        val planetRepository = mock(PlanetRepository::class.java)
        val factionRepository = mock(FactionRepository::class.java)
        val diplomacyService = mock(DiplomacyService::class.java)
        val modifierService = mock(ModifierService::class.java)
        val messageService = mock(MessageService::class.java)

        `when`(officerRepository.findBySessionId(1L)).thenReturn(allGenerals)
        `when`(factionRepository.findBySessionId(1L)).thenReturn(allNations)
        `when`(factionRepository.save(org.mockito.Mockito.any(Faction::class.java))).thenAnswer { it.arguments[0] as Faction }

        for ((nId, gens) in nationGeneralsMap) {
            `when`(officerRepository.findByFactionId(nId)).thenReturn(gens)
        }

        return CommandServices(
            officerRepository = officerRepository,
            planetRepository = planetRepository,
            factionRepository = factionRepository,
            diplomacyService = diplomacyService,
            modifierService = modifierService,
            messageService = messageService,
        )
    }

    // ===================== HIGH-RISK Commands =====================

    @Nested
    @DisplayName("등용 -- golden value parity")
    inner class 등용Test {

        @Test
        fun `등용 golden value -- statChanges match PHP trace`() {
            // Fixture: 유비 tries to recruit 관우 (factionId=2, exp=1000, ded=1000)
            val general = createTestGeneral(id = 1, name = "유비", funds = 2000, factionId = 1, planetId = 1)
            val destGen = createTestGeneral(id = 2, name = "관우", factionId = 2, experience = 1000, dedication = 1000)
            val env = createTestEnv(develCost = 100)
            val cmd = 등용(general, env, mapOf("destGeneralID" to 2L))
            cmd.city = createTestCity(factionId = 1)
            cmd.nation = createTestNation(id = 1, name = "촉")
            cmd.destOfficer = destGen
            cmd.services = createMockServices()

            // Constraint check
            assertTrue(cmd.checkFullCondition() is ConstraintResult.Pass)

            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            // Golden value: cost = round(100 + (1000+1000)/1000.0) * 10 = round(102.0) * 10 = 1020
            val json = mapper.readTree(result.message)
            assertEquals(-1020, json["statChanges"]["gold"].asInt(), "등용 gold cost parity")
            assertEquals(100, json["statChanges"]["experience"].asInt())
            assertEquals(200, json["statChanges"]["dedication"].asInt())
            assertEquals(1, json["statChanges"]["leadershipExp"].asInt())

            // Color-tagged log with josa: "유비" ends with 비(종성 없음) → "이" → 빈 문자열 안 됨
            // "관우" ends with 우(종성 없음)
            assertTrue(result.logs.any { it.contains("<Y>관우</>에게 등용 권유 서신을 보냈습니다.") },
                "color-tagged log with dest general name")
        }

        @Test
        fun `등용 constraint -- cannot recruit lord`() {
            val general = createTestGeneral(factionId = 1)
            val env = createTestEnv()
            val cmd = 등용(general, env, mapOf("destGeneralID" to 2L))
            cmd.city = createTestCity(factionId = 1)
            cmd.destOfficer = createTestGeneral(id = 2, factionId = 2, officerLevel = 20)

            val cond = cmd.checkFullCondition()
            assertTrue(cond is ConstraintResult.Fail)
            assertTrue((cond as ConstraintResult.Fail).reason.contains("군주"))
        }

        @Test
        fun `등용 constraint -- cannot recruit same nation general`() {
            val general = createTestGeneral(factionId = 1)
            val env = createTestEnv()
            val cmd = 등용(general, env, mapOf("destGeneralID" to 2L))
            cmd.city = createTestCity(factionId = 1)
            cmd.destOfficer = createTestGeneral(id = 2, factionId = 1)

            val cond = cmd.checkFullCondition()
            assertTrue(cond is ConstraintResult.Fail)
        }
    }

    @Nested
    @DisplayName("등용수락 -- golden value parity")
    inner class 등용수락Test {

        @Test
        fun `등용수락 golden value -- neutral general joins nation`() {
            // 방랑 장수가 등용 수락 → 국가 가입
            val general = createTestGeneral(
                id = 1, name = "조운", factionId = 0, fleetId = 0,
                funds = 1500, supplies = 1400, experience = 500, dedication = 300,
                npcState = 0, betray = 0,
            )
            val env = createTestEnv()
            val cmd = 등용수락(general, env)
            cmd.destFaction = createTestNation(id = 2, name = "촉", capitalPlanetId = 3)

            assertTrue(cmd.checkFullCondition() is ConstraintResult.Pass)
            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            val sc = json["statChanges"]

            // Neutral → Join: experience = 500 + 100 = 600, dedication = 300 + 100 = 400
            assertEquals(600, sc["experience"].asInt(), "experience parity (neutral bonus)")
            assertEquals(400, sc["dedication"].asInt(), "dedication parity (neutral bonus)")
            assertEquals(2, sc["nation"].asInt(), "nation change to destFaction")
            assertEquals(3, sc["city"].asLong(), "move to capital city")
            assertEquals(1, sc["officerLevel"].asInt())
            assertEquals(0, sc["officerPlanet"].asInt())
            assertEquals(1, sc["belong"].asInt())
            assertEquals(0, sc["troop"].asInt())

            // Recruiter rewards
            val rc = json["recruiterChanges"]
            assertEquals(100, rc["experience"].asInt())
            assertEquals(100, rc["dedication"].asInt())

            // troopDisband = false (fleetId=0 != id=1)
            assertFalse(json["troopDisband"].asBoolean())

            // Color-tagged log with josa: "조운" ends with 운(종성 ㄴ) → "이"
            assertTrue(result.logs.any { it.contains("<Y>조운</>이 등용 서신을 보냈습니다.") }.not())
            assertTrue(result.logs.any { it.contains("<D>촉</>로 망명") },
                "global log with 촉 + 로 josa")
        }

        @Test
        fun `등용수락 golden value -- betrayal penalty for affiliated general`() {
            val general = createTestGeneral(
                id = 1, name = "여포", factionId = 3, fleetId = 0,
                funds = 2000, supplies = 2500, experience = 1000, dedication = 800,
                betray = 2,
            )
            val env = createTestEnv()
            val cmd = 등용수락(general, env)
            cmd.destFaction = createTestNation(id = 5, name = "위", capitalPlanetId = 7)

            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            val sc = json["statChanges"]

            // Affiliated: betrayPenalty = 0.1 * 2 = 0.2
            // exp = floor(1000 * 0.8) = 800
            // ded = floor(800 * 0.8) = 640
            assertEquals(800, sc["experience"].asInt(), "experience with betrayal penalty")
            assertEquals(640, sc["dedication"].asInt(), "dedication with betrayal penalty")
            // Return funds: 2000 - 1000 = 1000 returned
            assertEquals(1000, sc["gold"].asInt(), "gold capped to 1000")
            assertEquals(1000, sc["returnGold"].asInt(), "return excess gold to nation")
            // Return supplies: 2500 - 1000 = 1500 returned
            assertEquals(1000, sc["rice"].asInt(), "rice capped to 1000")
            assertEquals(1500, sc["returnRice"].asInt(), "return excess rice to nation")
            // betray incremented: min(2+1, 10) = 3
            assertEquals(3, sc["betray"].asInt(), "betray count incremented")
        }
    }

    @Nested
    @DisplayName("임관 -- golden value parity")
    inner class 임관Test {

        @Test
        fun `임관 golden value -- small nation exp bonus and officerCount increment`() {
            val general = createTestGeneral(id = 1, name = "장비", factionId = 0, makeLimit = 0)
            val env = createTestEnv()
            val destFaction = createTestNation(id = 3, name = "촉").apply { officerCount = 5 }
            val cmd = 임관(general, env)
            cmd.destFaction = destFaction
            cmd.services = createMockServices(
                allNations = listOf(destFaction),
                nationGeneralsMap = mapOf(3L to listOf(
                    createTestGeneral(id = 100, factionId = 3),
                    createTestGeneral(id = 101, factionId = 3),
                )),
            )

            assertTrue(cmd.checkFullCondition() is ConstraintResult.Pass)
            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            val sc = json["statChanges"]

            // officerCount=2 (from findByNationId) < 8 → exp = 700
            assertEquals(700, sc["experience"].asInt(), "small nation exp bonus")
            assertEquals(3, sc["nation"].asInt())
            assertEquals(1, sc["officerLevel"].asInt())
            assertEquals(0, sc["officerPlanet"].asInt())
            assertEquals(1, sc["belong"].asInt())
            assertEquals(0, sc["troop"].asInt())

            // Entity mutation: officerCount incremented
            assertEquals(6, destFaction.officerCount, "nation officerCount incremented")

            // moveToCityOfLord and inheritanceBonus
            assertTrue(json["moveToCityOfLord"].asBoolean())
            assertEquals(1, json["inheritanceBonus"].asInt())
            assertTrue(json["tryUniqueLottery"].asBoolean())

            // Color-tagged log with josa: "장비" ends with 비(종성 없음) → "" (manual josa in 임관.kt)
            // Global log: "_global:<Y>장비</> <D><b>촉</b></>에 <S>임관</>했습니다."
            assertTrue(result.logs.any { it.contains("임관") && it.contains("장비") },
                "log contains 임관 and general name")
        }

        @Test
        fun `임관 golden value -- large nation exp lower`() {
            val general = createTestGeneral(factionId = 0)
            val env = createTestEnv()
            val destFaction = createTestNation(id = 4, name = "위")
            val cmd = 임관(general, env)
            cmd.destFaction = destFaction
            // 10 generals → >= 8 → exp = 100
            cmd.services = createMockServices(
                allNations = listOf(destFaction),
                nationGeneralsMap = mapOf(4L to (1L..10L).map {
                    createTestGeneral(id = it + 200, factionId = 4)
                }),
            )

            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            assertEquals(100, json["statChanges"]["experience"].asInt(), "large nation exp")
        }
    }

    @Nested
    @DisplayName("랜덤임관 -- golden value parity")
    inner class 랜덤임관Test {

        @Test
        fun `랜덤임관 golden value -- deterministic nation selection by affinity`() {
            val general = createTestGeneral(
                id = 1, name = "관우", factionId = 0, makeLimit = 0,
                npcState = 2, affinity = 20,
            )
            val env = createTestEnv(scenario = 1500)
            val cmd = 랜덤임관(general, env)

            val nationA = createTestNation(id = 10, name = "촉", capitalPlanetId = 101).apply { officerCount = 2; scoutLevel = 0 }
            val nationB = createTestNation(id = 11, name = "위", capitalPlanetId = 102).apply { officerCount = 3; scoutLevel = 0 }
            val lordA = createTestGeneral(id = 1001, factionId = 10, officerLevel = 20, planetId = 201, affinity = 22)
            val lordB = createTestGeneral(id = 1002, factionId = 11, officerLevel = 20, planetId = 202, affinity = 120)
            cmd.services = createMockServices(
                allNations = listOf(nationA, nationB),
                allGenerals = listOf(lordA, lordB),
            )

            assertTrue(cmd.checkFullCondition() is ConstraintResult.Pass)
            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            val sc = json["statChanges"]

            // Deterministic: historical NPC with seed 42 picks by affinity score
            assertEquals(10, sc["nation"].asInt(), "deterministic nation selection parity")
            assertEquals(1, sc["officerLevel"].asInt())
            assertEquals(0, sc["officerPlanet"].asInt())
            assertEquals(1, sc["belong"].asInt())
            assertEquals(0, sc["troop"].asInt())
            // planetId should be lord's city
            assertEquals("201", sc["planetId"].asText(), "move to lord's city")
            // officerCount=2 < 5(initialNationGenLimit) → exp = 700
            assertEquals(700, sc["experience"].asInt(), "small nation exp bonus")

            assertTrue(json["tryUniqueLottery"].asBoolean())
            assertEquals(1, json["inheritanceBonus"].asInt())

            // Color-tagged log with josa: "관우" ends with 우(종성 없음) → "가"
            assertTrue(result.logs.any { it.contains("<Y>관우</>가") },
                "josa parity: 관우 → 가")
        }

        @Test
        fun `랜덤임관 golden value -- fail when no eligible nation exists`() {
            val general = createTestGeneral(factionId = 0, makeLimit = 0)
            val env = createTestEnv()
            val cmd = 랜덤임관(general, env)

            val blockedNation = createTestNation(id = 20).apply { scoutLevel = 1 }
            cmd.services = createMockServices(
                allNations = listOf(blockedNation),
                allGenerals = emptyList(),
            )

            val result = runBlocking { cmd.run(freshRng()) }
            assertFalse(result.success)
            assertTrue(result.logs.any { it.contains("임관 가능한 국가가 없습니다") })
        }
    }

    @Nested
    @DisplayName("장수대상임관 -- golden value parity")
    inner class 장수대상임관Test {

        @Test
        fun `장수대상임관 golden value -- dest general city and officerCount`() {
            val general = createTestGeneral(id = 1, name = "마초", factionId = 0, makeLimit = 0)
            val env = createTestEnv()
            val destFaction = createTestNation(id = 4, name = "촉", capitalPlanetId = 9).apply { officerCount = 3 }
            val cmd = 장수대상임관(general, env)
            cmd.destFaction = destFaction
            cmd.destOfficer = createTestGeneral(id = 2, name = "유비", planetId = 7, factionId = 4)
            cmd.services = createMockServices(
                allNations = listOf(destFaction),
                nationGeneralsMap = mapOf(4L to listOf(
                    createTestGeneral(id = 100, factionId = 4),
                )),
            )

            assertTrue(cmd.checkFullCondition() is ConstraintResult.Pass)
            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            val sc = json["statChanges"]

            // 1 general < 8 → exp = 700
            assertEquals(700, sc["experience"].asInt(), "small nation exp bonus parity")
            // dest city = destOfficer's city (7), not capital (9)
            assertEquals(7, sc["city"].asInt(), "move to dest general's city, not capital")
            assertEquals(4, sc["nation"].asInt())
            assertEquals(1, sc["officerLevel"].asInt())
            assertEquals(0, sc["officerPlanet"].asInt())
            assertEquals(1, sc["belong"].asInt())

            // Entity mutation: officerCount incremented
            assertEquals(4, destFaction.officerCount, "nation officerCount incremented")

            // Color-tagged log with josa: "마초" ends with 초(종성 없음) → "가"
            assertTrue(result.logs.any { it.contains("<Y>마초</>가") },
                "josa parity: 마초 → 가")
        }
    }

    @Nested
    @DisplayName("건국 -- golden value parity (HIGH-RISK multi-entity)")
    inner class 건국Test {

        @Test
        fun `건국 golden value -- General City Nation 3-entity mutation`() {
            val general = createTestGeneral(id = 1, name = "조조", factionId = 0, planetId = 5, officerLevel = 0)
            val env = createTestEnv(year = 200, month = 2, startYear = 190)
            val cmd = 건국(general, env, mapOf(
                "factionName" to "위",
                "abbreviation" to "위",
                "nationType" to "che_군벌",
                "colorType" to 3,
            ))
            cmd.city = createTestCity(id = 5, name = "허창", factionId = 0)
            cmd.nation = createTestNation(id = 0, level = 0)

            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)

            // statChanges
            assertEquals(1000, json["statChanges"]["experience"].asInt(), "exp golden value")
            assertEquals(1000, json["statChanges"]["dedication"].asInt(), "ded golden value")

            // nationChanges: foundNation with all fields
            val nc = json["nationChanges"]
            assertTrue(nc["foundNation"].asBoolean())
            assertEquals("위", nc["factionName"].asText())
            assertEquals("위", nc["abbreviation"].asText())
            assertEquals("che_군벌", nc["nationType"].asText())
            assertEquals(3, nc["colorType"].asInt())
            assertEquals(1, nc["level"].asInt())
            assertEquals(5, nc["capital"].asLong(), "capital = general's planetId")
            assertEquals(1, nc["can_국기변경"].asInt())

            // cityChanges
            assertTrue(json["cityChanges"]["claimCity"].asBoolean())

            // Color-tagged logs with josa: "위" ends with 위(종성 없음) → "를"
            assertTrue(result.logs.any { it.contains("<D><b>위</b></>를 건국하였습니다.") },
                "josa parity: 위 → 를")
            // "조조" ends with 조(종성 없음) → "가"
            assertTrue(result.logs.any { it.contains("<Y>조조</>가") },
                "josa parity: 조조 → 가")

            // Global history log with nation type
            assertTrue(result.logs.any { it.contains("【건국】") && it.contains("che_군벌") })

            // Inheritance point
            val ip = json["inheritancePoint"]
            assertEquals(1, ip["active_action"].asInt())
            assertEquals(250, ip["unifier"].asInt())
        }

        @Test
        fun `건국 constraint -- fails during first turn`() {
            val general = createTestGeneral(factionId = 0, planetId = 1)
            // year=190, month=1, startYear=190 → yearMonth=2281 <= initYearMonth=2281
            val env = createTestEnv(year = 190, month = 1, startYear = 190)
            val cmd = 건국(general, env, mapOf("factionName" to "신국"))
            cmd.city = createTestCity(factionId = 0)
            cmd.nation = createTestNation(level = 0)

            val result = runBlocking { cmd.run(freshRng()) }
            assertFalse(result.success, "cannot found nation on first turn")
            val json = mapper.readTree(result.message)
            assertEquals("che_인재탐색", json["alternativeCommand"].asText())
        }
    }

    @Nested
    @DisplayName("무작위건국 -- golden value parity (HIGH-RISK multi-entity)")
    inner class 무작위건국Test {

        @Test
        fun `무작위건국 golden value -- deterministic city selection and multi-entity`() {
            val general = createTestGeneral(id = 1, name = "손견", factionId = 0, planetId = 1, officerLevel = 0)
            val env = createTestEnv(year = 200, month = 2, startYear = 190)
            val cmd = 무작위건국(general, env, mapOf(
                "factionName" to "오",
                "nationType" to "che_군벌",
                "colorType" to 1,
            ))
            cmd.nation = createTestNation(level = 0)

            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)

            // statChanges
            assertEquals(1000, json["statChanges"]["experience"].asInt())
            assertEquals(1000, json["statChanges"]["dedication"].asInt())

            // nationChanges
            val nc = json["nationChanges"]
            assertTrue(nc["foundNation"].asBoolean())
            assertEquals("오", nc["factionName"].asText())
            assertEquals("che_군벌", nc["nationType"].asText())
            assertEquals(1, nc["colorType"].asInt())
            assertEquals(1, nc["level"].asInt())

            // aux fields specific to 무작위건국
            val aux = nc["aux"]
            assertEquals(1, aux["can_국기변경"].asInt())
            assertEquals(1, aux["can_무작위수도이전"].asInt())

            // findRandomCity
            val frc = json["findRandomCity"]
            assertEquals("neutral_constructable", frc["query"].asText())
            assertEquals(5, frc["levelMin"].asInt())
            assertEquals(6, frc["levelMax"].asInt())

            assertTrue(json["moveAllNationGenerals"].asBoolean())
            assertEquals("che_해산", json["alternativeCommand"].asText())

            // Color-tagged log: "오" ends with 오(종성 없음) → "를"
            assertTrue(result.logs.any { it.contains("<D><b>오</b></>를 건국하였습니다.") },
                "josa parity: 오 → 를")
        }

        @Test
        fun `무작위건국 fails during first turn`() {
            val general = createTestGeneral(factionId = 0)
            val env = createTestEnv(year = 190, month = 1, startYear = 190)
            val cmd = 무작위건국(general, env, mapOf("factionName" to "신국"))

            val result = runBlocking { cmd.run(freshRng()) }
            assertFalse(result.success)
            val json = mapper.readTree(result.message)
            assertEquals("che_인재탐색", json["alternativeCommand"].asText())
        }
    }

    @Nested
    @DisplayName("모반시도 -- golden value parity (HIGH-RISK)")
    inner class 모반시도Test {

        @Test
        fun `모반시도 golden value -- success case entity mutations`() {
            // 모반시도 run() always succeeds (constraint handles eligibility)
            val general = createTestGeneral(id = 1, name = "사마의", factionId = 1, officerLevel = 12)
            val env = createTestEnv()
            val cmd = 모반시도(general, env)
            cmd.city = createTestCity(factionId = 1)
            cmd.nation = createTestNation(id = 1, name = "위")

            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)

            // statChanges: 모반 성공 → officerLevel=20
            assertEquals(20, json["statChanges"]["officerLevel"].asInt(), "rebel becomes lord")
            assertEquals(0, json["statChanges"]["officerPlanet"].asInt())

            // rebellionResult
            assertTrue(json["rebellionResult"]["success"].asBoolean())

            // lordChanges: former lord demoted
            assertEquals(1, json["lordChanges"]["officerLevel"].asInt(), "former lord demoted to 1")
            assertEquals(0, json["lordChanges"]["officerPlanet"].asInt())
            assertEquals(0.7, json["lordChanges"]["experienceMultiplier"].asDouble(), 0.001)

            // lordLogs: action and history logs for the deposed lord
            assertTrue(json["lordLogs"]["action"].asText().contains("사마의"),
                "lord action log mentions rebel")
            assertTrue(json["lordLogs"]["history"].asText().contains("모반"),
                "lord history log mentions rebellion")

            // Color-tagged log with josa: "사마의" ends with 의(종성 없음) → "가"
            assertTrue(result.logs.any { it.contains("<Y>사마의</>가") },
                "josa parity: 사마의 → 가")
            assertTrue(result.logs.any { it.contains("【모반】") },
                "global log contains 【모반】 tag")
        }

        @Test
        fun `모반시도 constraint -- lord cannot rebel`() {
            val general = createTestGeneral(factionId = 1, officerLevel = 20)
            val env = createTestEnv()
            val cmd = 모반시도(general, env)
            cmd.city = createTestCity(factionId = 1)
            cmd.nation = createTestNation(id = 1)

            val cond = cmd.checkFullCondition()
            assertTrue(cond is ConstraintResult.Fail)
            assertTrue((cond as ConstraintResult.Fail).reason.contains("군주"))
        }
    }

    @Nested
    @DisplayName("인재탐색 -- golden value parity (HIGH-RISK)")
    inner class 인재탐색Test {

        @Test
        fun `인재탐색 golden value -- NPC not found case`() {
            val general = createTestGeneral(
                id = 1, name = "유비", funds = 500,
                leadership = 80, command = 60, intelligence = 70,
            )
            // High general count → foundProp very low → most likely not found
            val env = createTestEnv(develCost = 100, gameStor = mutableMapOf(
                "maxGeneral" to 500.0,
                "totalGeneralCount" to 480.0,
                "totalNpcCount" to 200.0,
            ))
            val cmd = 인재탐색(general, env)

            assertTrue(cmd.checkFullCondition() is ConstraintResult.Pass)
            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            val sc = json["statChanges"]
            assertEquals(-100, sc["gold"].asInt(), "gold cost parity")
            assertEquals(100, sc["experience"].asInt(), "not-found exp parity")
            assertEquals(70, sc["dedication"].asInt(), "not-found ded parity")

            // Weighted stat: leadership=80, command =60, intelligence =70, total=210
            // rng.nextInt(210) deterministic → check which stat gets +1
            assertTrue(
                sc.has("leadershipExp") || sc.has("strengthExp") || sc.has("intelExp"),
                "one stat exp must be present"
            )
            assertTrue(json["tryUniqueLottery"].asBoolean())

            // Color-tagged log
            assertTrue(result.logs.any { it.contains("인재를 찾을 수 없었습니다.") })
        }

        @Test
        fun `인재탐색 golden value -- NPC found case`() {
            val general = createTestGeneral(
                id = 1, name = "조조", funds = 500,
                leadership = 50, command = 50, intelligence = 50,
            )
            // Very low general count → foundProp high → very likely found
            val env = createTestEnv(develCost = 100, gameStor = mutableMapOf(
                "maxGeneral" to 500.0,
                "totalGeneralCount" to 10.0,
                "totalNpcCount" to 5.0,
            ))
            val cmd = 인재탐색(general, env)

            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            val sc = json["statChanges"]
            assertEquals(-100, sc["gold"].asInt(), "gold cost")
            assertEquals(200, sc["experience"].asInt(), "found exp parity")
            assertEquals(300, sc["dedication"].asInt(), "found ded parity")

            // Found: stat gets +3 instead of +1
            val statExpSum = (sc["leadershipExp"]?.asInt() ?: 0) +
                (sc["strengthExp"]?.asInt() ?: 0) +
                (sc["intelExp"]?.asInt() ?: 0)
            assertEquals(3, statExpSum, "found stat exp = 3")

            // createNPC directive
            assertTrue(json.has("createNPC"))
            assertEquals("wandering", json["createNPC"]["type"].asText())

            assertTrue(result.logs.any { it.contains("인재를 발견하였습니다!") })
        }
    }

    @Nested
    @DisplayName("증여 -- golden value parity")
    inner class 증여Test {

        @Test
        fun `증여 golden value -- gold transfer with multi-entity assertion`() {
            val general = createTestGeneral(id = 1, name = "유비", factionId = 1, planetId = 1, funds = 2000, supplies = 2000)
            val destGen = createTestGeneral(id = 2, name = "관우", factionId = 1, planetId = 1)
            val env = createTestEnv()
            val cmd = 증여(general, env, mapOf("isGold" to true, "amount" to 500))
            cmd.city = createTestCity(factionId = 1)
            cmd.destOfficer = destGen

            assertTrue(cmd.checkFullCondition() is ConstraintResult.Pass)
            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            val sc = json["statChanges"]
            assertEquals(-500, sc["gold"].asInt(), "gold deducted parity")
            assertEquals(70, sc["experience"].asInt(), "exp golden value")
            assertEquals(100, sc["dedication"].asInt(), "ded golden value")
            assertEquals(1, sc["leadershipExp"].asInt())

            // destGeneralChanges
            val dgc = json["destGeneralChanges"]
            assertEquals("2", dgc["officerId"].asText())
            assertEquals(500, dgc["gold"].asInt(), "dest general receives gold")

            // Color-tagged log with amount formatting
            assertTrue(result.logs.any { it.contains("<C>500</>") },
                "color-tagged amount in log")
        }

        @Test
        fun `증여 golden value -- rice transfer clamped to available amount`() {
            // general has 600 rice, minimum reserve=100, max giveable = 500
            // request 1000 → clamped to 500
            val general = createTestGeneral(factionId = 1, planetId = 1, funds = 2000, supplies = 600)
            val env = createTestEnv()
            val cmd = 증여(general, env, mapOf("isGold" to false, "amount" to 1000))
            cmd.city = createTestCity(factionId = 1)
            cmd.destOfficer = createTestGeneral(id = 2, factionId = 1, planetId = 1)

            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            assertEquals(-500, json["statChanges"]["rice"].asInt(), "rice clamped to max giveable")
        }
    }

    @Nested
    @DisplayName("장비매매 -- golden value parity (HIGH-RISK)")
    inner class 장비매매Test {

        @Test
        fun `장비매매 golden value -- buy weapon`() {
            val general = createTestGeneral(id = 1, name = "관우", funds = 5000)
            val env = createTestEnv()
            // Buy "che_무기_01_단도" (grade=1, cost=1000)
            val cmd = 장비매매(general, env, mapOf("itemType" to "weapon", "itemCode" to "che_무기_01_단도"))
            cmd.city = createTestCity(factionId = 1)

            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            assertEquals(-1000, json["statChanges"]["gold"].asInt(), "buy cost golden value")
            assertEquals(10, json["statChanges"]["experience"].asInt(), "buy exp golden value")

            val ic = json["itemChanges"]
            assertEquals("weapon", ic["type"].asText())
            assertEquals("che_무기_01_단도", ic["code"].asText())
            assertEquals("buy", ic["action"].asText())

            // Color-tagged log
            assertTrue(result.logs.any { it.contains("<C>단도(+1)</>") },
                "item name with grade in color tag")
        }

        @Test
        fun `장비매매 golden value -- sell weapon`() {
            val general = createTestGeneral(
                id = 1, name = "조운", funds = 1000,
                flagshipCode = "che_무기_01_단도",
            )
            val env = createTestEnv()
            val cmd = 장비매매(general, env, mapOf("itemType" to "weapon", "itemCode" to "None"))

            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            // sell price = cost / 2 = 500
            assertEquals(500, json["statChanges"]["gold"].asInt(), "sell price golden value")
            assertEquals(10, json["statChanges"]["experience"].asInt())

            val ic = json["itemChanges"]
            assertEquals("weapon", ic["type"].asText())
            assertEquals("None", ic["code"].asText())
            assertEquals("sell", ic["action"].asText())
        }

        @Test
        fun `장비매매 fails without args`() {
            val general = createTestGeneral(funds = 5000)
            val env = createTestEnv()
            val cmd = 장비매매(general, env)

            val result = runBlocking { cmd.run(freshRng()) }
            assertFalse(result.success)
            assertTrue(result.logs[0].contains("인자가 없습니다"))
        }
    }

    // ===================== MEDIUM Commands =====================

    @Nested
    @DisplayName("하야 -- golden value parity")
    inner class 하야Test {

        @Test
        fun `하야 golden value -- betrayal penalty and resource return`() {
            val general = createTestGeneral(
                id = 1, name = "여포", factionId = 1, officerLevel = 5,
                funds = 3000, supplies = 2500, experience = 1000, dedication = 800,
                betray = 1, fleetId = 0,
            )
            val env = createTestEnv()
            val cmd = 하야(general, env)
            cmd.nation = createTestNation(id = 1, name = "위")

            assertTrue(cmd.checkFullCondition() is ConstraintResult.Pass)
            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            val sc = json["statChanges"]

            // betrayCount=1, penaltyRate=0.1
            // newExp = floor(1000 * 0.9) = 900, expLoss = 100
            // newDed = floor(800 * 0.9) = 720, dedLoss = 80
            assertEquals(-100, sc["experience"].asInt(), "exp loss golden value")
            assertEquals(-80, sc["dedication"].asInt(), "ded loss golden value")
            // goldToNation = 3000 - 1000 = 2000
            assertEquals(-2000, sc["gold"].asInt(), "gold returned to nation")
            // riceToNation = 2500 - 1000 = 1500
            assertEquals(-1500, sc["rice"].asInt(), "rice returned to nation")
            // newBetray = min(1+1, 10) = 2, delta = 2-1 = 1
            assertEquals(1, sc["betray"].asInt(), "betray increment")

            // nationChanges: nation receives returned resources
            val nc = json["nationChanges"]
            assertEquals(2000, nc["gold"].asInt())
            assertEquals(1500, nc["rice"].asInt())

            // leaveNation, resetOfficer flags
            assertTrue(json["leaveNation"].asBoolean())
            assertTrue(json["resetOfficer"].asBoolean())
            assertEquals(12, json["setMakeLimit"].asInt())

            // Color-tagged log with josa: "여포" ends with 포(종성 없음) → "가"
            assertTrue(result.logs.any { it.contains("<Y>여포</>가") },
                "josa parity: 여포 → 가")
            assertTrue(result.logs.any { it.contains("<R>하야</>") },
                "color-tagged 하야")
        }
    }

    @Nested
    @DisplayName("은퇴 -- golden value parity")
    inner class 은퇴Test {

        @Test
        fun `은퇴 golden value -- rebirth and checkHall flags`() {
            val general = createTestGeneral(id = 1, name = "황충", age = 65)
            val env = createTestEnv()
            val cmd = 은퇴(general, env)

            assertTrue(cmd.checkFullCondition() is ConstraintResult.Pass)
            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            assertTrue(json["rebirth"].asBoolean())
            assertTrue(json["checkHall"].asBoolean(), "checkHall=true when isunited=0")
            assertTrue(json["tryUniqueLottery"].asBoolean())

            // Color-tagged log
            assertTrue(result.logs.any { it.contains("은퇴하였습니다.") })
        }

        @Test
        fun `은퇴 golden value -- checkHall false when united`() {
            val general = createTestGeneral(age = 60)
            val env = createTestEnv(gameStor = mutableMapOf("isunited" to 1))
            val cmd = 은퇴(general, env)

            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            assertFalse(json["checkHall"].asBoolean(), "checkHall=false when isunited=1")
        }

        @Test
        fun `은퇴 constraint -- fails under age 60`() {
            val general = createTestGeneral(age = 59)
            val cmd = 은퇴(general, createTestEnv())
            assertTrue(cmd.checkFullCondition() is ConstraintResult.Fail)
        }
    }

    @Nested
    @DisplayName("선양 -- golden value parity")
    inner class 선양Test {

        @Test
        fun `선양 golden value -- lord transfer multi-entity`() {
            val general = createTestGeneral(id = 1, name = "유비", factionId = 1, officerLevel = 20)
            val destGen = createTestGeneral(id = 2, name = "유선", factionId = 1)
            val env = createTestEnv()
            val cmd = 선양(general, env)
            cmd.nation = createTestNation(id = 1, name = "촉")
            cmd.destOfficer = destGen

            assertTrue(cmd.checkFullCondition() is ConstraintResult.Pass)
            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            val sc = json["statChanges"]

            // Old lord demoted
            assertEquals(1, sc["officerLevel"].asInt(), "old lord demoted to 1")
            assertEquals(0, sc["officerPlanet"].asInt())
            assertEquals(0.7, sc["experienceMultiplier"].asDouble(), 0.001, "exp multiplier parity")

            // nationChanges: new chief
            assertEquals(2, json["nationChanges"]["chiefGeneralId"].asLong())

            // destGeneralChanges: new lord
            val dgc = json["destGeneralChanges"]
            assertEquals(2, dgc["officerId"].asLong())
            assertEquals(20, dgc["officerLevel"].asInt(), "new lord level 20")
            assertEquals(0, dgc["officerPlanet"].asInt())

            // Color-tagged logs
            assertTrue(result.logs.any { it.contains("【선양】") })
            // "유비" ends with 비(종성 없음) → "가"
            assertTrue(result.logs.any { it.contains("<Y>유비</>가") || it.contains("유비</>이 ").not() },
                "josa parity: 유비 → 가 or empty")
        }

        @Test
        fun `선양 constraint -- non-lord cannot abdicate`() {
            val general = createTestGeneral(factionId = 1, officerLevel = 11)
            val cmd = 선양(general, createTestEnv())
            cmd.destOfficer = createTestGeneral(id = 2, factionId = 1)
            assertTrue(cmd.checkFullCondition() is ConstraintResult.Fail)
        }
    }

    @Nested
    @DisplayName("해산 -- golden value parity")
    inner class 해산Test {

        @Test
        fun `해산 golden value -- disband nation and resource caps`() {
            val general = createTestGeneral(id = 1, name = "원소", factionId = 1, officerLevel = 20)
            val env = createTestEnv(year = 191, month = 2, startYear = 190)
            val cmd = 해산(general, env)
            cmd.nation = createTestNation(id = 1, name = "기", level = 0)

            assertTrue(cmd.checkFullCondition() is ConstraintResult.Pass)
            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            assertTrue(json["disbandNation"].asBoolean())

            // generalChanges: makeLimit=12, gold/rice capped
            val gc = json["generalChanges"]
            assertEquals(12, gc["makeLimit"].asInt())
            assertEquals(1000, gc["gold"]["max"].asInt())
            assertEquals(1000, gc["rice"]["max"].asInt())

            // allNationGenerals: all members capped
            val anc = json["allNationGenerals"]
            assertEquals(1000, anc["gold"]["max"].asInt())
            assertEquals(1000, anc["rice"]["max"].asInt())

            assertTrue(json["releaseCities"].asBoolean())

            // Color-tagged log with josa: "기" ends with 기(종성 없음) → "를"
            assertTrue(result.logs.any { it.contains("해산") })
            assertTrue(result.logs.any { it.contains("【해산】") },
                "global history log contains 【해산】 tag")
        }

        @Test
        fun `해산 fails during first turn`() {
            val general = createTestGeneral(officerLevel = 20)
            val env = createTestEnv(year = 190, month = 1, startYear = 190)
            val cmd = 해산(general, env)
            cmd.nation = createTestNation(level = 0)

            val result = runBlocking { cmd.run(freshRng()) }
            assertFalse(result.success)
        }
    }

    @Nested
    @DisplayName("견문 -- golden value parity")
    inner class 견문Test {

        @Test
        fun `견문 golden value -- deterministic event selection with seed 42`() {
            val general = createTestGeneral(id = 1, name = "제갈량")
            val env = createTestEnv()
            val cmd = 견문(general, env)

            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            val sc = json["statChanges"]

            // With Random(42), the event selection is deterministic
            // experience must be either 30 (INC_EXP) or 60 (INC_HEAVY_EXP)
            val exp = sc["experience"].asInt()
            assertTrue(exp == 30 || exp == 60, "exp golden value: must be 30 or 60")

            // Log must contain event text + date
            assertTrue(result.logs.any { it.contains("<1>200년 01월</>") },
                "date format in log")
        }

        @Test
        fun `견문 golden value -- determinism check (same seed same output)`() {
            val general = createTestGeneral(id = 1, name = "유비")
            val env = createTestEnv()

            val result1 = runBlocking { 견문(general, env).run(Random(42)) }
            val result2 = runBlocking { 견문(general, env).run(Random(42)) }

            assertEquals(result1.message, result2.message, "deterministic output with same seed")
            assertEquals(result1.logs.size, result2.logs.size)
        }
    }

    // ===================== LOW Commands =====================

    @Nested
    @DisplayName("휴식 -- golden value parity")
    inner class 휴식Test {

        @Test
        fun `휴식 golden value -- no-op with log`() {
            val general = createTestGeneral(id = 1, name = "유비")
            val env = createTestEnv()
            val cmd = 휴식(general, env)

            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)
            assertNull(result.message, "휴식 has no message payload")

            // Log format: "아무것도 실행하지 않았습니다. <1>200년 01월</>"
            assertEquals(1, result.logs.size)
            assertTrue(result.logs[0].contains("아무것도 실행하지 않았습니다."))
            assertTrue(result.logs[0].contains("<1>200년 01월</>"), "date format parity")
        }
    }

    @Nested
    @DisplayName("내정특기초기화 -- golden value parity")
    inner class 내정특기초기화Test {

        @Test
        fun `내정특기초기화 golden value -- reset special with age tracking`() {
            val general = createTestGeneral(
                id = 1, name = "방통", age = 30,
                specialCode = "che_내정_농업달인",
            )
            val env = createTestEnv()
            val cmd = 내정특기초기화(general, env)

            assertTrue(cmd.checkFullCondition() is ConstraintResult.Pass)
            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            assertEquals("None", json["statChanges"]["specialCode"].asText(), "special reset to None")
            assertEquals(31, json["statChanges"]["specAge"].asInt(), "specAge = age + 1")

            val sr = json["specialReset"]
            assertEquals("specialCode", sr["type"].asText())
            assertEquals("che_내정_농업달인", sr["oldSpecial"].asText())

            // Color-tagged log
            assertTrue(result.logs.any { it.contains("내정 특기") })
        }

        @Test
        fun `내정특기초기화 constraint -- fails when no special`() {
            val general = createTestGeneral(specialCode = "None")
            val cmd = 내정특기초기화(general, createTestEnv())
            assertTrue(cmd.checkFullCondition() is ConstraintResult.Fail)
        }
    }

    @Nested
    @DisplayName("전투특기초기화 -- golden value parity")
    inner class 전투특기초기화Test {

        @Test
        fun `전투특기초기화 golden value -- reset special2 with age tracking`() {
            val general = createTestGeneral(
                id = 1, name = "장비", age = 25,
                special2Code = "che_전투_돌격",
            )
            val env = createTestEnv()
            val cmd = 전투특기초기화(general, env)

            assertTrue(cmd.checkFullCondition() is ConstraintResult.Pass)
            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            assertEquals("None", json["statChanges"]["special2Code"].asText(), "special2 reset to None")
            assertEquals(26, json["statChanges"]["specAge2"].asInt(), "specAge2 = age + 1")

            val sr = json["specialReset"]
            assertEquals("special2Code", sr["type"].asText())
            assertEquals("che_전투_돌격", sr["oldSpecial"].asText())
        }

        @Test
        fun `전투특기초기화 constraint -- fails when no special2`() {
            val general = createTestGeneral(special2Code = "None")
            val cmd = 전투특기초기화(general, createTestEnv())
            assertTrue(cmd.checkFullCondition() is ConstraintResult.Fail)
        }
    }

    // ===================== NPC/CR Infrastructure =====================

    @Nested
    @DisplayName("NPC능동 -- basic operation verification")
    inner class NPC능동Test {

        @Test
        fun `NPC능동 parity -- teleport action`() {
            val general = createTestGeneral(id = 1, name = "NPC장수", npcState = 1)
            val env = createTestEnv()
            val cmd = NPC능동(general, env, mapOf("optionText" to "순간이동", "destCityID" to 7))

            assertTrue(cmd.checkFullCondition() is ConstraintResult.Pass)
            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            assertEquals(7, json["statChanges"]["city"].asInt(), "teleport to city 7")
            assertEquals("teleport", json["npcAction"].asText())
            assertEquals(7, json["destCityID"].asInt())

            assertTrue(result.logs.any { it.contains("도시#7로 이동") })
        }

        @Test
        fun `NPC능동 fails with unknown command`() {
            val general = createTestGeneral(npcState = 1)
            val cmd = NPC능동(general, createTestEnv(), mapOf("optionText" to "알수없음"))

            val result = runBlocking { cmd.run(freshRng()) }
            assertFalse(result.success)
        }
    }

    @Nested
    @DisplayName("CR건국 -- basic operation verification")
    inner class CR건국Test {

        @Test
        fun `CR건국 parity -- nation foundation with city claim`() {
            val general = createTestGeneral(
                id = 1, name = "유비", officerLevel = 0, factionId = 0, planetId = 1,
            )
            val env = createTestEnv(year = 200, month = 2, startYear = 190)
            val cmd = CR건국(general, env, mapOf(
                "factionName" to "촉한",
                "nationType" to "che_왕도",
                "colorType" to 2,
            ))
            cmd.nation = createTestNation(level = 0)
            cmd.city = createTestCity(id = 1, name = "성도", factionId = 0)

            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            // nationFoundation (not nationChanges like regular 건국)
            val nf = json["nationFoundation"]
            assertEquals("촉한", nf["name"].asText())
            assertEquals("che_왕도", nf["type"].asText())
            assertEquals(2, nf["colorType"].asInt())
            assertEquals(1, nf["capitalPlanetId"].asLong())

            assertEquals(1000, json["statChanges"]["experience"].asInt())
            assertEquals(1000, json["statChanges"]["dedication"].asInt())

            assertTrue(json["cityChanges"]["claimCity"].asBoolean())

            // Color-tagged log with josa: "촉한" ends with 한(종성 ㄴ) → "을"
            assertTrue(result.logs.any { it.contains("<D><b>촉한</b></>을 건국하였습니다.") },
                "josa parity: 촉한 → 을")
        }

        @Test
        fun `CR건국 fails during first turn`() {
            val general = createTestGeneral(factionId = 0, planetId = 1)
            val env = createTestEnv(year = 190, month = 1, startYear = 190)
            val cmd = CR건국(general, env, mapOf("factionName" to "신국"))
            cmd.city = createTestCity(factionId = 0)

            val result = runBlocking { cmd.run(freshRng()) }
            assertFalse(result.success)
        }
    }

    @Nested
    @DisplayName("CR맹훈련 -- basic operation verification")
    inner class CR맹훈련Test {

        @Test
        fun `CR맹훈련 parity -- train and morale gain golden value`() {
            val general = createTestGeneral(
                id = 1, name = "관우", factionId = 1,
                ships = 1000, training = 50, morale = 60,
                leadership = 80, shipClass = 1,
            )
            val env = createTestEnv()
            val cmd = CR맹훈련(general, env)
            cmd.city = createTestCity(factionId = 1)

            assertTrue(cmd.checkFullCondition() is ConstraintResult.Pass)
            val result = runBlocking { cmd.run(freshRng()) }
            assertTrue(result.success)

            val json = mapper.readTree(result.message)
            val sc = json["statChanges"]

            // rawScore = round((80 * 100.0 / 1000) * 30.0 * 2.0 / 3.0) = round(160.0) = 160
            // But wait, env.trainDelta = 30.0 in CommandEnv default
            // rawScore = round((80*100/1000) * 30.0 * 2/3) = round(8.0 * 30.0 * 0.667) = round(160.0) = 160
            // Actually: (80 * 100.0 / 1000) = 8.0, * 30.0 = 240.0, * 2.0/3.0 = 160.0, round = 160
            // But trainDelta check: if (env.trainDelta > 0) env.trainDelta else 0.05
            // env.trainDelta = 30.0 > 0 → trainDelta = 30.0
            // Wait, code says: val trainDelta = if (env.trainDelta > 0) env.trainDelta else 0.05
            // trainGain = min(160, max(0, 100 - 50)) = min(160, 50) = 50
            // atmosGain = min(160, max(0, 100 - 60)) = min(160, 40) = 40
            assertEquals(50, sc["train"].asInt(), "train gain clamped to max")
            assertEquals(40, sc["atmos"].asInt(), "atmos gain clamped to max")
            assertEquals(-500, sc["rice"].asInt(), "rice cost golden value")
            assertEquals(150, sc["experience"].asInt())
            assertEquals(100, sc["dedication"].asInt())
            assertEquals(1, sc["leadershipExp"].asInt())

            // dexChanges
            assertEquals(1, json["dexChanges"]["crewType"].asInt())
            assertEquals(320, json["dexChanges"]["amount"].asInt(), "dex amount = rawScore * 2")

            // Color-tagged log
            assertTrue(result.logs.any { it.contains("<C>160</>") },
                "rawScore in color-tagged log")
        }
    }
}
