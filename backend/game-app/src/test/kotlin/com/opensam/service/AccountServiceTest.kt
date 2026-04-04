package com.opensam.service

import com.opensam.entity.AppUser
import com.opensam.entity.General
import com.opensam.repository.AppUserRepository
import com.opensam.repository.GeneralRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional

class AccountServiceTest {
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var generalRepository: GeneralRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var service: AccountService

    @BeforeEach
    fun setUp() {
        appUserRepository = mock(AppUserRepository::class.java)
        generalRepository = mock(GeneralRepository::class.java)
        passwordEncoder = mock(PasswordEncoder::class.java)
        service = AccountService(appUserRepository, generalRepository, passwordEncoder)
    }

    @Test
    fun `deleteAccount schedules deletion after password verification`() {
        val user = AppUser(
            id = 1,
            loginId = "user",
            displayName = "유저",
            passwordHash = "encoded",
            meta = mutableMapOf(),
        )
        `when`(appUserRepository.findByLoginId("user")).thenReturn(user)
        `when`(passwordEncoder.matches("secret", "encoded")).thenReturn(true)

        val result = service.deleteAccount("user", "secret")

        assertTrue(result)
        assertNotNull(user.meta["deleteRequestedAt"])
        assertNotNull(user.meta["deleteAfter"])
        verify(appUserRepository).save(user)
        verify(generalRepository, never()).findByUserId(user.id)
    }

    @Test
    fun `deleteAccount rejects wrong password`() {
        val user = AppUser(
            id = 1,
            loginId = "user",
            displayName = "유저",
            passwordHash = "encoded",
        )
        `when`(appUserRepository.findByLoginId("user")).thenReturn(user)
        `when`(passwordEncoder.matches("wrong", "encoded")).thenReturn(false)

        val result = service.deleteAccount("user", "wrong")

        assertFalse(result)
        verify(appUserRepository, never()).save(user)
    }

    @Test
    fun `updateSettings persists third use and picture to user and generals`() {
        val user = AppUser(
            id = 3,
            loginId = "user",
            displayName = "유저",
            passwordHash = "encoded",
            meta = mutableMapOf(),
        )
        val general = General(
            id = 10,
            userId = 3,
            worldId = 1,
            name = "장수",
            picture = "old",
        )
        `when`(appUserRepository.findByLoginId("user")).thenReturn(user)
        `when`(generalRepository.findByUserId(3L)).thenReturn(listOf(general))

        val result = service.updateSettings(
            loginId = "user",
            defenceTrain = 70,
            tournamentState = 1,
            potionThreshold = 25,
            autoNationTurn = true,
            preRiseDelete = true,
            preOpenDelete = false,
            borderReturn = true,
            customCss = ".test{}",
            thirdUse = false,
            picture = "/icons/new.png",
        )

        assertTrue(result)
        assertEquals(false, user.meta["thirdUse"])
        assertEquals("/icons/new.png", user.meta["picture"])
        assertEquals(0, user.meta["imageServer"])
        assertEquals("/icons/new.png", general.picture)
        assertEquals(0.toShort(), general.imageServer)
        assertEquals(70.toShort(), general.defenceTrain)
        assertEquals(1.toShort(), general.tournamentState)
        assertEquals(25, general.meta["potionThreshold"])
        assertEquals(true, general.meta["autoNationTurn"])
        assertEquals(true, general.meta["preRiseDelete"])
        assertEquals(false, general.meta["preOpenDelete"])
        assertEquals(true, general.meta["borderReturn"])
        assertEquals(".test{}", general.meta["customCss"])
        verify(appUserRepository).save(user)
        verify(generalRepository).save(general)
    }

    @Test
    fun `updateIconUrl syncs picture to all player generals`() {
        val user = AppUser(
            id = 4,
            loginId = "user",
            displayName = "유저",
            passwordHash = "encoded",
            meta = mutableMapOf(),
        )
        val playerGeneral = General(
            id = 10,
            userId = 4,
            worldId = 1,
            name = "장수",
            picture = "old.png",
            npcState = 0,
        )
        val npcGeneral = General(
            id = 11,
            userId = 4,
            worldId = 1,
            name = "NPC장수",
            picture = "npc.png",
            npcState = 1,
        )
        `when`(appUserRepository.findByLoginId("user")).thenReturn(user)
        `when`(generalRepository.findByUserId(4L)).thenReturn(listOf(playerGeneral, npcGeneral))

        val result = service.updateIconUrl("user", "/uploads/icons/new.png")

        assertTrue(result)
        assertEquals("/uploads/icons/new.png", user.meta["picture"])
        assertEquals(0, user.meta["imageServer"])
        // Player general should be synced
        assertEquals("/uploads/icons/new.png", playerGeneral.picture)
        assertEquals(0.toShort(), playerGeneral.imageServer)
        verify(generalRepository).save(playerGeneral)
        // NPC general (npcState != 0) should NOT be synced
        assertEquals("npc.png", npcGeneral.picture)
        verify(generalRepository, never()).save(npcGeneral)
    }

    @Test
    fun `updateIconUrl clears picture when blank url`() {
        val user = AppUser(
            id = 4,
            loginId = "user",
            displayName = "유저",
            passwordHash = "encoded",
            meta = mutableMapOf("picture" to "old.png", "imageServer" to 0),
        )
        val general = General(
            id = 10,
            userId = 4,
            worldId = 1,
            name = "장수",
            picture = "old.png",
            npcState = 0,
        )
        `when`(appUserRepository.findByLoginId("user")).thenReturn(user)
        `when`(generalRepository.findByUserId(4L)).thenReturn(listOf(general))

        val result = service.updateIconUrl("user", "")

        assertTrue(result)
        assertFalse(user.meta.containsKey("picture"))
        assertFalse(user.meta.containsKey("imageServer"))
        assertEquals("", general.picture)
        verify(generalRepository).save(general)
    }

    @Test
    fun `getDetailedInfo extracts oauth provider name`() {
        val user = AppUser(
            id = 5,
            loginId = "user",
            displayName = "유저",
            passwordHash = "encoded",
            meta = mutableMapOf(
                "thirdUse" to true,
                "oauthProviders" to mutableListOf(
                    mutableMapOf(
                        "provider" to "kakao",
                        "accessTokenValidUntil" to "2099-01-01T00:00:00Z",
                    ),
                ),
            ),
        )
        `when`(appUserRepository.findByLoginId("user")).thenReturn(user)

        val info = service.getDetailedInfo("user")

        assertEquals("KAKAO", info?.get("oauthType"))
        assertEquals("2099-01-01T00:00:00Z", info?.get("tokenValidUntil"))
        assertEquals(true, info?.get("thirdUse"))
    }
}
