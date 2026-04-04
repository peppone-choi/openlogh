package com.openlogh.command.general

import com.openlogh.command.CommandEnv
import com.openlogh.entity.General

class che_상업투자(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : DomesticCommand(general, env, arg) {

    override val actionName = "상업 투자"
    override val actionKey = "상업"
    override val cityKey = "comm"
    override val statKey = "intel"
    override val debuffFront = 0.5
}
