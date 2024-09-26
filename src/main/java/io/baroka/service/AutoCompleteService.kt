package io.baroka.service

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import io.baroka.handler.TerminalWebSocketHandler.Companion.mapper
import io.baroka.handler.TerminalWebSocketHandler.Companion.pathMap
import io.baroka.model.Message
import io.baroka.model.MessageType
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets


@Service
class AutoCompleteService {

    fun autoCompleteCommand(webSocketSession: WebSocketSession, sshSession: Session, messageDto: Message<String>) {
        val command = messageDto.data!!
        val sessionId = messageDto.session
        val currentPath = pathMap.getOrDefault(sessionId, "~")
        //파일 이름 추출
        val fileNamePrefix = if(command.contains(" ")) command.substring(command.lastIndexOf(' ') + 1) else command

        val channelExec = sshSession.openChannel("exec") as ChannelExec
        val responseStream = ByteArrayOutputStream()
        channelExec.setOutputStream(responseStream)
        channelExec.setCommand("cd " + currentPath + " && compgen -f " + fileNamePrefix)
        channelExec.connect()

        val inputStream = channelExec.inputStream
        var reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val autoCompleteResult = StringBuilder()
        var line: String? = reader.readLine()
        while (line != null) {
            autoCompleteResult.append(line).append("\n")
            line = reader.readLine() // 다음 줄 읽기
        }

        webSocketSession.sendMessage(TextMessage(mapper.writeValueAsString(
            Message(
            session = sessionId,
            messageType =  MessageType.AUTOCOMPLETE,
            data = autoCompleteResult.toString())
        )))

        channelExec.disconnect()
    }
}