package com.openlogh.command

import com.openlogh.entity.General

abstract class NationCommand(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null
) : BaseCommand(general, env, arg)
