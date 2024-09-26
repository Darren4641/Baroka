package io.baroka.service

import com.google.gson.Gson
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import io.baroka.exception.InvalidException
import io.baroka.handler.TerminalWebSocketHandler
import io.baroka.handler.TerminalWebSocketHandler.Companion.getSession
import io.baroka.model.Message
import io.baroka.model.MessageType
import io.baroka.model.VI
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader


@Service
class ViService {

    fun handleFileOperation(webSocketSession: WebSocketSession, messageDto: Message<String>) {
        val gson = Gson()
        val vi = gson.fromJson(messageDto.data as String?, VI::class.java)
        val sessionId = messageDto.session

        try {

            when(vi.operation) {
                "SAVE" -> saveFileContent(sessionId!!, vi.title!!, vi.content!!, vi.remoteDir!!, vi.isBaroka!!, vi.sudo)
            }
        } catch (e: Exception) {
            webSocketSession.sendMessage(
                TextMessage(
                    TerminalWebSocketHandler.mapper.writeValueAsString(Message(
                messageType = MessageType.COMMAND,
                data = "Error executing command: " + e.message)))
            )
        }

    }

    fun fetchFileContent(webSocketSession: WebSocketSession, messageDto: Message<String>) {
        val sessionId = messageDto.session!!
        val gson = Gson()
        val vi = gson.fromJson(messageDto.data as String?, VI::class.java)

        val sshSession = getSession(sessionId)

        if(sshSession == null) {
            webSocketSession.sendMessage(
                TextMessage(
                    TerminalWebSocketHandler.mapper.writeValueAsString(Message(
                        messageType = MessageType.COMMAND,
                        data = "SSH session not found")))
            )
            return
        }

        val channel = sshSession.openChannel("exec") as ChannelExec
        val responseStream = ByteArrayOutputStream()
        channel.setOutputStream(responseStream)
        if(vi.sudo) {
            channel.setCommand("sudo cat " + vi.remoteDir + "/" + vi.title)
        } else {
            channel.setCommand("cat " + vi.remoteDir + "/" + vi.title)
        }

        channel.connect()

        val inputStream = channel.inputStream
        val reader = BufferedReader(InputStreamReader(inputStream))
        val fileContent = StringBuilder()
        var line: String? = reader.readLine()
        while (line != null) {
            fileContent.append(line).append("\n")
            line = reader.readLine() // 다음 줄 읽기
        }

        channel.disconnect()

        webSocketSession.sendMessage(
            TextMessage(
                TerminalWebSocketHandler.mapper.writeValueAsString(Message(
                    messageType = MessageType.VI_CONTENT,
                    data = fileContent.toString())))
        )

    }

    private fun saveFileContent(sessionId: String, title: String, content: String, remoteDir: String, isBaroka: Boolean, isSudo: Boolean) {
        val session = getSession(sessionId)
        if(session == null)
            throw InvalidException("SSH session not found")

        var fileTitle = title
        if(isBaroka && !title.contains(".sh")) {
            val sb = StringBuilder()
            sb.append(title)
            sb.append(".sh")
            fileTitle = sb.toString()
        }

        val remoteFilePath = remoteDir + "/" + fileTitle
        val command = String.format("echo -e '%s' > %s", content.replace("'", "'\\''"), remoteFilePath)

        println("command = ${command}")

        val channel = session.openChannel("exec") as ChannelExec

        val responseStream = ByteArrayOutputStream()
        channel.setOutputStream(responseStream)
        channel.setCommand(command)
        channel.connect()

        if(isBaroka) setExecutablePermission(session, remoteFilePath)
        channel.disconnect()
    }

    private fun setExecutablePermission(session: Session, remoteFilePath: String) {
        val command = "chmod +x " + remoteFilePath
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        val responseStream = ByteArrayOutputStream()
        channel.setOutputStream(responseStream)
        channel.connect()
        channel.disconnect()
    }
}