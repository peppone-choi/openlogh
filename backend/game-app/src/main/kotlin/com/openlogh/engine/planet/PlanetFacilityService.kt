package com.openlogh.engine.planet

import com.openlogh.entity.Planet
import org.springframework.stereotype.Service

/**
 * 행성 시설 서비스.
 *
 * gin7 §5.5: 행성 내 시설별 기능 접근 제어.
 *
 * 시설 목록은 Planet.meta["facilities"] JSON 배열에 저장.
 * 각 항목은 PlanetFacility 형식의 맵으로 직렬화됨.
 *
 * 주요 시설:
 * - 조병공창(ARSENAL/SHIPYARD): 함선 건조 필수
 * - 사관학교(ACADEMY): 수강/병기연습 필수
 * - 거주구(RESIDENTIAL), 호텔(HOTEL), 회의실(CONFERENCE),
 *   주점(TAVERN), 총독부(GOVERNMENT), 사령부(HEADQUARTERS)
 */
@Service
class PlanetFacilityService {

    /** 행성 시설 유형 열거 (gin7 §5.5 시설 목록) */
    enum class Facility(val code: String, val displayName: String) {
        /** 조병공창: 함선 건조/수리 */
        SHIPYARD("arsenal", "조병공창"),
        /** 사관학교: 수강/병기연습/강의 */
        ACADEMY("academy", "사관학교"),
        /** 거주구: 개인 생활 공간 */
        RESIDENTIAL("residential", "거주구"),
        /** 호텔: 사교/숙박 */
        HOTEL("hotel", "호텔"),
        /** 회의실: 공식 회의 */
        CONFERENCE("conference", "회의실"),
        /** 주점: 비공식 모임, 정보 수집 */
        TAVERN("tavern", "주점"),
        /** 총독부: 행정/정치 */
        PLAZA("government", "총독부"),
        /** 사령부: 군사 지휘 */
        MANSION("headquarters", "사령부"),
        ;

        companion object {
            private val byCode = entries.associateBy { it.code }
            fun fromCode(code: String): Facility? = byCode[code]
        }
    }

    /**
     * 행성 시설 목록 반환.
     *
     * Planet.meta["facilities"] 배열에서 type 코드를 읽어 Facility 열거형으로 변환.
     */
    fun getFacilities(planet: Planet): List<Facility> {
        @Suppress("UNCHECKED_CAST")
        val raw = planet.meta["facilities"] as? List<*> ?: return emptyList()
        return raw.mapNotNull { item ->
            val type = when (item) {
                is Map<*, *> -> item["type"] as? String
                is String    -> item
                else         -> null
            }
            type?.let { Facility.fromCode(it) }
        }
    }

    /**
     * 조병공창 보유 여부.
     *
     * gin7 §5.5: 함선 건조/수리는 조병공창 보유 행성에서만 가능.
     */
    fun hasShipyard(planet: Planet): Boolean =
        hasFacility(planet, Facility.SHIPYARD)

    /**
     * 사관학교 보유 여부.
     *
     * gin7 §5.5: 수강/병기연습은 사관학교 보유 행성에서만 가능.
     */
    fun hasAcademy(planet: Planet): Boolean =
        hasFacility(planet, Facility.ACADEMY)

    /**
     * 함선 건조 가능 여부.
     *
     * 조병공창 보유 여부와 동일.
     */
    fun canConstructShips(planet: Planet): Boolean = hasShipyard(planet)

    /**
     * 특정 시설 보유 여부.
     */
    fun hasFacility(planet: Planet, facility: Facility): Boolean =
        getFacilities(planet).any { it == facility }

    /**
     * 시설 코드로 보유 여부 직접 확인.
     */
    fun hasFacilityByCode(planet: Planet, facilityCode: String): Boolean {
        @Suppress("UNCHECKED_CAST")
        val raw = planet.meta["facilities"] as? List<*> ?: return false
        return raw.any { item ->
            when (item) {
                is Map<*, *> -> item["type"] as? String == facilityCode
                is String    -> item == facilityCode
                else         -> false
            }
        }
    }
}
