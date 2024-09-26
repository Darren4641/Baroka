package io.baroka.config

import io.baroka.handler.TerminalWebSocketHandler
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = ["messaging.socket"], havingValue = "true")
class WebSocketConfig (
    val terminalWebSocketHandler: TerminalWebSocketHandler
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(terminalWebSocketHandler, "/terminal").setAllowedOrigins("*")
        registry.addHandler(terminalWebSocketHandler, "/terminal").setAllowedOrigins("*").withSockJS()
    }
}