package io.baroka.service

import com.jcraft.jsch.Session
import io.baroka.handler.TerminalWebSocketHandler
import io.baroka.handler.TerminalWebSocketHandler.Companion.mapper
import io.baroka.handler.TerminalWebSocketHandler.Companion.sessionMap
import io.baroka.handler.TerminalWebSocketHandler.Companion.shellChannelMap
import io.baroka.model.Message
import io.baroka.model.MessageType
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.IOException
import java.nio.charset.StandardCharsets


@Service
class CommandService {

    fun exitCommand(webSocketSession: WebSocketSession, session: Session, messageDto: Message<*>) {
        val tunnelSessionId = "Tunnel_" + messageDto.session
        val sessionId = messageDto.session!!
        webSocketSession.sendMessage(TextMessage(mapper.writeValueAsString(Message(
            messageType = MessageType.EXIT,
            data = ""))))
        val tunnelSession: Session? = sessionMap.get(tunnelSessionId)
        if(tunnelSession != null) {
            try {
                TerminalWebSocketHandler.localPortMap.get(sessionId)?.let { tunnelSession.delPortForwardingL(it) }
                Thread.sleep(100)
                tunnelSession.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        disconnectSession(sessionId, webSocketSession)
    }

    fun viCommand(webSocketSession: WebSocketSession, session: Session, messageDto: Message<*>) {
        val command = messageDto.data as String
        var isSudo = false
        var fileName: String
        if(command.contains("sudo")) {
            isSudo = true
            fileName = if (command.substring(7).isEmpty()) "" else command.split(" ")[2]
        } else {
            fileName = if (command.substring(2).isEmpty()) "" else command.split(" ")[1]
        }

        webSocketSession.sendMessage(TextMessage(mapper.writeValueAsString(Message(
            messageType = MessageType.VI,
            data = fileName,
            sudo = isSudo))))
    }

    fun command(webSocketSession: WebSocketSession, session: Session, messageDto: Message<*>) {
        val command = messageDto.data as String
        val sessionId = messageDto.session
        val channel = shellChannelMap.get(sessionId)

        if (channel != null) {
            try {
                val outputStream = channel.outputStream
                if(command.contains("nano")) {
                    outputStream.write("echo \"[Preparing] This feature is being prepared.\"\n".toByteArray(StandardCharsets.UTF_8))
                    outputStream.flush()
                } else if (command.startsWith("cd ")) {
                    outputStream.write((command + "\n").toByteArray(StandardCharsets.UTF_8))
                    outputStream.flush()
                    outputStream.write("echo \"[baroka_path]:\"  $(pwd)\n".toByteArray(StandardCharsets.UTF_8))
                    outputStream.flush()
                } else {
                    outputStream.write((command + "\n").toByteArray(StandardCharsets.UTF_8))
                    outputStream.flush()
                }
            } catch (e: IOException) {
                // 명령어 실행 중 예외 발생 시 오류 메시지 전송
                webSocketSession.sendMessage(TextMessage(mapper.writeValueAsString(Message(
                    messageType = MessageType.COMMAND,
                    data = "Error executing command: " + e.message))))
            }
        }
    }


    private fun disconnectSession(sessionId: String, session: WebSocketSession) {
        val sshSession = TerminalWebSocketHandler.sessionMap.get(sessionId)
        if(sshSession != null) {
            sshSession.disconnect()
            TerminalWebSocketHandler.sessionMap.remove(sessionId)
        }
        TerminalWebSocketHandler.pathMap.remove(sessionId)
        TerminalWebSocketHandler.processMap.remove(sessionId)
        session.close()
    }
}