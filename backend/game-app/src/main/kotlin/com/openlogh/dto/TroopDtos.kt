package com.openlogh.dto

import com.openlogh.entity.Fleet

data class CreateTroopRequest(val worldId: Long, val leaderGeneralId: Long, val nationId: Long, val name: String)

data class TroopActionRequest(val generalId: Long)

data class RenameTroopRequest(val name: String)

data class TroopMemberInfo(val id: Long, val name: String, val picture: String)

data class TroopWithMembers(val troop: Fleet, val members: List<TroopMemberInfo>)
