package com.openlogh.command

import com.openlogh.entity.Officer

abstract class OfficerCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>? = null
) : BaseCommand(general, env, arg)
