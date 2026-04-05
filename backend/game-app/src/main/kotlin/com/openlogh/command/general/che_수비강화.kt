package com.openlogh.command.general

import com.openlogh.command.CommandEnv
import com.openlogh.entity.Officer

class che_수비강화(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : DomesticCommand(general, env, arg) {

    override val actionName = "수비 강화"
    override val actionKey = "수비"
    override val cityKey = "def"
    override val statKey = "strength"
    override val debuffFront = 0.5
}
