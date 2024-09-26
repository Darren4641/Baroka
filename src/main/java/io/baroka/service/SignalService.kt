package io.baroka.service

import com.jcraft.jsch.Session
import io.baroka.exception.InvalidException
import io.baroka.handler.TerminalWebSocketHandler.Companion.shellChannelMap
import io.baroka.model.Message
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import java.io.IOException
import java.nio.charset.StandardCharsets


@Service
class SignalService {

    fun sendSignalToShell(webSocketSession: WebSocketSession, sshSession: Session, messageDto: Message<String>) {
        val signal = messageDto.data as String
        val sessionId = messageDto.session
        val channel = shellChannelMap.get(sessionId)

        if(channel != null) {
            try {
                val outputStream = channel.outputStream
                outputStream.write("\u0003".toByteArray(StandardCharsets.UTF_8))
                outputStream.flush()
            } catch (e: IOException) {
                throw InvalidException("Error sending signal to session ${sessionId} ${e.message}")
            }
        } else {
            throw InvalidException("No active channel found for session: ${sessionId}")
        }
    }
}