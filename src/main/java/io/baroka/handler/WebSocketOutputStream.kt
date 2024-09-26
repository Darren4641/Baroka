package io.baroka.handler

import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.OutputStream
import java.nio.charset.StandardCharsets


class WebSocketOutputStream (
    val session: WebSocketSession
) : OutputStream() {

    @Override
    override fun write(b: Int) {
        session.sendMessage(TextMessage(byteArrayOf(b.toByte())))
    }

    @Override
    override fun write(b: ByteArray) {
        val message = String(b, StandardCharsets.UTF_8)
        session.sendMessage(TextMessage(message))
    }

    @Override
    override fun write(b: ByteArray, off: Int, len: Int) {
        val message = String(b, off, len, StandardCharsets.UTF_8)
        session.sendMessage(TextMessage(message))
    }
}