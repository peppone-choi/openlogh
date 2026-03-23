package com.openlogh.gateway.config

import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {
    @Bean
    fun webClientCustomizer(): WebClientCustomizer =
        WebClientCustomizer { builder ->
            builder.exchangeStrategies(
                ExchangeStrategies.builder()
                    .codecs { codecs: ClientCodecConfigurer ->
                        codecs.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) // 16 MB
                    }
                    .build()
            )
        }
}
