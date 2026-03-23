package com.openlogh.gateway.repository

import com.openlogh.gateway.entity.SystemSetting
import org.springframework.data.jpa.repository.JpaRepository

interface SystemSettingRepository : JpaRepository<SystemSetting, String>
