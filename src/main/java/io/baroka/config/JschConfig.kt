package io.baroka.config

import com.jcraft.jsch.JSch
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JschConfig {

    @Bean
    fun jsch() : JSch = JSch()
}