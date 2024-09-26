package io.baroka

import io.baroka.config.MessagingConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@EnableConfigurationProperties(MessagingConfig::class)
@ComponentScan(basePackages = ["io.baroka.*"])
@SpringBootApplication
class BarokaApplication

fun main(args: Array<String>) {
    runApplication<BarokaApplication>(*args)
}