package com.openlogh.service

import com.openlogh.entity.ShipUnit
import com.openlogh.model.CrewProficiency
import com.openlogh.model.ShipClass
import com.openlogh.model.ShipSubtype
import com.openlogh.repository.ShipUnitRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * ShipUnit CRUD 서비스.
 * 유닛 생성 시 ShipStatRegistry에서 서브타입 스탯을 주입한다.
 */
@Service
class ShipUnitService(
    private val shipUnitRepository: ShipUnitRepository,
    private val shipStatRegistry: ShipStatRegistry,
) {

    /**
     * 새 ShipUnit을 생성하고 서브타입 스탯을 주입한다.
     * @param sessionId 세션 ID
     * @param fleetId 소속 함대 ID
     * @param slotIndex 함대 내 슬롯 번호 (0~7)
     * @param shipSubtypeName ShipSubtype.name 예: "BATTLESHIP_I"
     * @param factionType "empire" 또는 "alliance"
     * @param shipCount 초기 함선 수 (0~300)
     */
    @Transactional
    fun createShipUnit(
        sessionId: Long,
        fleetId: Long,
        slotIndex: Short,
        shipSubtypeName: String,
        factionType: String,
        shipCount: Int = 0,
    ): ShipUnit {
        val subtype = runCatching { ShipSubtype.valueOf(shipSubtypeName) }.getOrNull()
        val shipClassName = subtype?.shipClass?.name ?: ShipClass.BATTLESHIP.name

        val unit = ShipUnit(
            sessionId       = sessionId,
            fleetId         = fleetId,
            slotIndex       = slotIndex,
            shipClass       = shipClassName,
            shipSubtype     = shipSubtypeName,
            shipCount       = shipCount.coerceIn(0, 300),
            maxShipCount    = 300,
            crewProficiency = CrewProficiency.GREEN.name,
        )

        // 서브타입 스탯 주입 — ShipStatRegistry에서 조회
        val stat = shipStatRegistry.getShipStat(shipSubtypeName, factionType)
        if (stat != null) {
            unit.armor          = stat.armor
            unit.shield         = stat.shield
            unit.weaponPower    = stat.weaponPower
            unit.speed          = stat.speed
            unit.crewCapacity   = stat.crewCapacity
            unit.supplyCapacity = stat.supplyCapacity
        }

        return shipUnitRepository.save(unit)
    }

    fun findByFleet(fleetId: Long): List<ShipUnit> =
        shipUnitRepository.findByFleetId(fleetId)

    fun findFlagship(fleetId: Long): ShipUnit? =
        shipUnitRepository.findByFleetIdAndIsFlagshipTrue(fleetId)

    @Transactional
    fun applyBattleDamage(unitId: Long, lostShips: Int): ShipUnit? {
        val unit = shipUnitRepository.findById(unitId).orElse(null) ?: return null
        unit.shipCount = (unit.shipCount - lostShips).coerceAtLeast(0)
        return shipUnitRepository.save(unit)
    }
}
