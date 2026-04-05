package com.openlogh.command.general

import com.openlogh.command.CommandEnv
import com.openlogh.entity.Officer

class 내정특기초기화(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : 전투특기초기화(general, env, arg) {

    override val actionName = "내정 특기 초기화"
    // Legacy: specialType = 'special', speicalAge = 'specage'
    override val specialField = "specialCode"
    override val specAgeField = "specAge"
    override val specialText = "내정 특기"
}
