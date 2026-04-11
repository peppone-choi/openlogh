package com.openlogh.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Phase 24-23 (gap E1, gin7 매뉴얼 p8-9):
 *
 * gin7 원작은 한 게임 세션당 최대 2000명의 장교가 동시에 참가하는 것을 권장
 * 스펙으로 명시한다. v2.4 까지 `game_const.json` 의 `defaultMaxGeneral` 은
 * OpenSamguk 시절의 500 값을 그대로 들고 있어 LOGH 세션이 매뉴얼 규모까지
 * 확장되지 못했다.
 *
 * 본 회귀 테스트는 리소스 번들의 `defaultMaxGeneral` 이 2000 으로 고정되어
 * 있음을 보증한다. 이후 누군가 값을 다시 낮추면 CI 에서 바로 드러난다.
 *
 * 운영 측면에서는 세션별 `world.config.maxGeneral` override 가 여전히 가능
 * 하므로 특정 세션만 더 타이트하게 제한하는 기존 워크플로우는 그대로 동작한다.
 */
class SessionCapacityTest {

    @Test
    fun `defaultMaxGeneral is pinned at gin7 manual value 2000`() {
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        // shared 모듈의 classpath 에 포함되어 game-app 테스트에서도 읽힌다.
        val stream = this::class.java.classLoader.getResourceAsStream("data/game_const.json")
            ?: error("data/game_const.json not found on classpath")
        val constants: Map<String, Any> = mapper.readValue(
            stream,
            object : TypeReference<Map<String, Any>>() {}
        )

        val value = (constants["defaultMaxGeneral"] as Number).toInt()
        assertEquals(
            2000, value,
            "Phase 24-23: gin7 매뉴얼 p8-9 세션당 2000명 상한을 기준으로 고정 — " +
                "값을 다시 변경하려면 .planning 에 근거를 남기고 이 테스트를 갱신할 것."
        )
    }
}
