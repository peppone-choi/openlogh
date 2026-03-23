package com.openlogh

import org.junit.jupiter.api.Test
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(classes = [OpenloghApplicationTests.TestConfig::class])
@ActiveProfiles("test")
class OpenloghApplicationTests {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    class TestConfig

    @Test
    fun contextLoads() {
    }
}
