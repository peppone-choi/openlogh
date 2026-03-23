package com.openlogh.service

import com.openlogh.dto.FactionMutationResponse
import com.openlogh.dto.FactionPolicyInfo
import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.SessionStateRepository
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class FactionService(
    private val factionRepository: FactionRepository,
    private val officerRepository: OfficerRepository,
    private val appUserRepository: AppUserRepository,
    private val officerRankService: OfficerRankService,
    private val planetRepository: PlanetRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val sessionStateRepository: SessionStateRepository,
    private val mapService: MapService,
) {
    fun getFaction(id: Long): Faction? = factionRepository.findById(id).orElse(null)

    fun getPolicy(nationId: Long): FactionPolicyInfo? {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return null

        @Suppress("UNCHECKED_CAST")
        val nationNotice = faction.meta["nationNotice"] as? Map<String, Any>
        val notice = (nationNotice?.get("msg") as? String)
            ?: (faction.meta["notice"] as? String)
            ?: ""
        val scoutMsg = (faction.meta["scout_msg"] as? String)
            ?: (faction.meta["scoutMsg"] as? String)
            ?: ""

        return FactionPolicyInfo(
            conscriptionRate = faction.conscriptionRate.toInt(),
            taxRate = faction.taxRate.toInt(),
            secretLimit = faction.secretLimit.toInt(),
            strategicCmdLimit = faction.strategicCmdLimit.toInt(),
            notice = notice,
            scoutMsg = scoutMsg,
            blockWar = faction.warState > 0,
            blockScout = faction.scoutLevel > 0,
        )
    }

    fun verifyPolicyAccess(nationId: Long, loginId: String): Boolean {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return false
        val user = appUserRepository.findByLoginId(loginId) ?: return false
        val officers = officerRepository.findBySessionIdAndUserId(faction.sessionId, user.id)

        return officers.any { officer ->
            officer.factionId == nationId && (
                officer.permission == "ambassador" ||
                    (officer.rank >= 12 && officer.penalty["noChief"] != true)
            )
        }
    }

    fun updateNotice(nationId: Long, msg: String, author: Officer): Boolean {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return false

        val sanitized = msg
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

        faction.meta["notice"] = sanitized
        faction.meta["nationNotice"] = mutableMapOf<String, Any>(
            "msg" to sanitized,
            "author" to author.name,
            "authorID" to author.id,
            "date" to OffsetDateTime.now().toString(),
        )
        factionRepository.save(faction)
        return true
    }

    fun updateBlockScout(nationId: Long, block: Boolean): FactionMutationResponse {
        val faction = factionRepository.findById(nationId).orElse(null)
            ?: return FactionMutationResponse(false, "진영을 찾을 수 없습니다.")

        val world = sessionStateRepository.findById(faction.sessionId.toShort()).orElse(null)
            ?: return FactionMutationResponse(false, "세계를 찾을 수 없습니다.")

        if (world.config["blockChangeScout"] == true) {
            return FactionMutationResponse(false, "임관 설정을 바꿀 수 없도록 설정되어 있습니다.")
        }

        faction.scoutLevel = if (block) 1 else 0
        factionRepository.save(faction)

        return FactionMutationResponse(true)
    }

    fun updateBlockWar(nationId: Long, block: Boolean): FactionMutationResponse {
        val faction = factionRepository.findById(nationId).orElse(null)
            ?: return FactionMutationResponse(false, "진영을 찾을 수 없습니다.")

        val availableCnt = (faction.meta["available_war_setting_cnt"] as? Number)?.toInt() ?: 0
        if (availableCnt <= 0) {
            return FactionMutationResponse(false, "변경 횟수가 초과되었습니다.")
        }

        faction.warState = if (block) 1 else 0
        faction.meta["available_war_setting_cnt"] = availableCnt - 1
        factionRepository.save(faction)

        return FactionMutationResponse(true, availableCnt = availableCnt - 1)
    }
}
