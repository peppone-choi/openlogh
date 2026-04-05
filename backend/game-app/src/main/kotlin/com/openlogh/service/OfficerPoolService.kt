package com.openlogh.service

import com.openlogh.entity.Officer
import com.openlogh.repository.OfficerRepository
import org.springframework.stereotype.Service
import kotlin.random.Random

/**
 * Service for managing the NPC officer pool.
 * Used by strategic commands like 의병모집 to spawn new NPC generals.
 */
@Service
class OfficerPoolService(
    private val officerRepository: OfficerRepository,
) {
    /**
     * Pick and create an NPC officer from the pool.
     *
     * @param worldId The world ID
     * @param nationId The faction to assign the officer to
     * @param cityId The planet to place the officer in
     * @param npcType NPC type code (e.g. 4 for militia)
     * @param birthYear Birth year for the NPC
     * @param deathYear Death year for the NPC
     * @param killTurn Turn at which the NPC will be removed
     * @param gold Initial gold
     * @param rice Initial rice
     * @param experience Initial experience
     * @param dedication Initial dedication
     * @param specAge Special age field
     * @param rng Random number generator
     * @return The created Officer entity
     */
    fun pickAndCreateNpc(
        worldId: Long,
        nationId: Long,
        cityId: Long,
        npcType: Int,
        birthYear: Int,
        deathYear: Int,
        killTurn: Int,
        gold: Int,
        rice: Int,
        experience: Int,
        dedication: Int,
        specAge: Int,
        rng: Random,
    ): Officer {
        val leadership = (rng.nextInt(40, 80)).toShort()
        val strength = (rng.nextInt(30, 70)).toShort()
        val intel = (rng.nextInt(30, 70)).toShort()

        val officer = Officer(
            sessionId = worldId,
            factionId = nationId,
            planetId = cityId,
            name = generateNpcName(rng),
            leadership = leadership,
            command = strength,
            intelligence = intel,
            funds = gold, supplies = rice,
            experience = experience,
            dedication = dedication,
            npcState = npcType.toShort(),
            bornYear = birthYear.toShort(),
            deadYear = deathYear.toShort(),
            killTurn = killTurn.toShort(),
            specAge = specAge.toShort(),
            officerLevel = 1,
        )

        return officerRepository.save(officer)
    }

    private fun generateNpcName(rng: Random): String {
        val surnames = listOf("장", "왕", "이", "조", "유", "진", "한", "위", "마", "곽", "서", "호", "주", "정", "손")
        val givenNames = listOf("무", "영", "덕", "충", "안", "보", "성", "문", "호", "용", "걸", "평", "의", "강", "흥")
        return "${surnames[rng.nextInt(surnames.size)]}${givenNames[rng.nextInt(givenNames.size)]}${givenNames[rng.nextInt(givenNames.size)]}"
    }
}
