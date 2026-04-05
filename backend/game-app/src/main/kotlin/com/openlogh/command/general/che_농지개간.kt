package com.openlogh.command.general

import com.openlogh.command.CommandEnv
import com.openlogh.entity.Officer

class che_농지개간(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : DomesticCommand(general, env, arg) {

    override val actionName = "농지 개간"
    override val actionKey = "농업"
    override val cityKey = "agri"
    override val statKey = "intel"
    override val debuffFront = 0.5
}
