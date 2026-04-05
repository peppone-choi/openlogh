package com.openlogh.command.general

import com.openlogh.command.CommandEnv
import com.openlogh.entity.Officer

class che_성벽보수(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : DomesticCommand(general, env, arg) {

    override val actionName = "성벽 보수"
    override val actionKey = "성벽"
    override val cityKey = "wall"
    override val statKey = "strength"
    override val debuffFront = 0.25
}
