package com.openlogh.command.general

import com.openlogh.command.CommandEnv
import com.openlogh.entity.General

class che_치안강화(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : DomesticCommand(general, env, arg) {

    override val actionName = "치안 강화"
    override val actionKey = "치안"
    override val cityKey = "secu"
    override val statKey = "strength"
    override val debuffFront = 1.0
}
