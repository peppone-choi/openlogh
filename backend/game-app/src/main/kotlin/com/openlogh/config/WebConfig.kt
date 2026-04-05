package com.openlogh.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Paths

@Configuration
class WebConfig : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val iconDir = Paths.get(System.getProperty("user.home"), ".opensam", "icons").toAbsolutePath()
        registry.addResourceHandler("/uploads/icons/**")
            .addResourceLocations("file:$iconDir/")
    }
}
