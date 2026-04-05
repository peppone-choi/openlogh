package com.openlogh.service

import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.GeneralRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Syncs the general's icon/picture from the member (account) profile.
 * Legacy: j_adjust_icon.php
 */
@Service
class IconSyncService(
    private val generalRepository: GeneralRepository,
    private val appUserRepository: AppUserRepository,
) {
    data class SyncResult(val result: Boolean, val reason: String)

    @Transactional
    fun syncIcon(loginId: String): SyncResult {
        val user = appUserRepository.findByLoginId(loginId)
            ?: return SyncResult(false, "회원 기록 정보가 없습니다")

        val generals = generalRepository.findByUserId(user.id).filter { it.npcState.toInt() == 0 }
        if (generals.isEmpty()) {
            return SyncResult(true, "등록된 장수가 없습니다")
        }

        val userPicture = user.meta["picture"] as? String
        val userImageServer = (user.meta["imageServer"] as? Number)?.toShort()

        for (general in generals) {
            if (userPicture != null) general.picture = userPicture
            if (userImageServer != null) general.imageServer = userImageServer
            generalRepository.save(general)
        }

        return SyncResult(true, "success")
    }
}
