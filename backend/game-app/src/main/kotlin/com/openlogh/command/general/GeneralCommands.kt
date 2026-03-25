@file:Suppress("ClassName", "unused")

package com.openlogh.command.general

import com.openlogh.command.*
import com.openlogh.entity.*
import kotlin.random.Random

// ========== 휴식 (Rest) ==========

class 휴식(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : BaseCommand(general, env, arg) {
    override val actionName = "휴식"

    override suspend fun run(rng: Random): CommandResult {
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 아무것도 실행하지 않았습니다."),
        )
    }
}
