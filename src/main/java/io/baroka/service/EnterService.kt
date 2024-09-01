package io.baroka.service

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.Session
import groovy.util.logging.Slf4j
import io.baroka.handler.TerminalWebSocketHandler
import io.baroka.handler.TerminalWebSocketHandler.Companion.mapper
import io.baroka.handler.TerminalWebSocketHandler.Companion.shellChannelMap
import io.baroka.handler.WebSocketOutputStream
import io.baroka.model.Message
import io.baroka.model.MessageType
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.*
import java.nio.charset.StandardCharsets


@Slf4j
@Service
class EnterService {

    fun startShellChannel(webSocketSession: WebSocketSession, sshSession: Session, sessionId:String) {
        val channel = sshSession.openChannel("shell") as ChannelShell

        // Set up input and output streams for the shell channel
        val pipedIn = PipedInputStream()
        val pipedOut = PipedOutputStream(pipedIn)
        channel.inputStream = BufferedInputStream(pipedIn)

        val pipedErrIn = PipedInputStream()
        val pipedErrOut = PipedOutputStream(pipedErrIn)

        // Set output streams to send data back to WebSocket
        channel.outputStream = WebSocketOutputStream(webSocketSession)
        channel.setExtOutputStream(WebSocketOutputStream(webSocketSession))

        shellChannelMap.put(sessionId, channel)
        channel.connect()

        // Read from shell and send to WebSocket

        // Read from shell and send to WebSocket
        val reader = BufferedReader(InputStreamReader(channel.inputStream, StandardCharsets.UTF_8))
        val errReader = BufferedReader(InputStreamReader(pipedErrIn, StandardCharsets.UTF_8))

        val outputThread = createOutputThread(
            reader = reader,
            webSocketSession = webSocketSession,
            sessionId = sessionId)

        // Read from shell error stream and send to WebSocket
        val errorThread = createErrorThread(reader = errReader,
            webSocketSession = webSocketSession,
            sessionId = sessionId)

        
        outputThread.start()
        errorThread.start()

        // Store threads to interrupt later
        webSocketSession.attributes["outputThread"] = outputThread
        webSocketSession.attributes["errorThread"] = errorThread
    }

    private fun createOutputThread(reader: BufferedReader, webSocketSession: WebSocketSession, sessionId: String) : Thread {
        return Thread { 
            try {
                var line: String?
                var lineCount = 0
                while (reader.readLine().also { line = it } != null) {
                    lineCount++
                    val sanitizedLine: String = sanitizeAnsiCodes(line!!)
                    if (sanitizedLine.contains("[baroka_path]")) {
                        val newPath = sanitizedLine.split(":")[1]
                        TerminalWebSocketHandler.pathMap.put(sessionId, newPath)

                        webSocketSession.sendMessage(TextMessage(mapper.writeValueAsString(Message(
                            session = sessionId,
                            messageType = MessageType.PATH,
                            data = newPath))))
                    } else if (sanitizedLine.contains("Preparing")) {
                        synchronized(webSocketSession) {
                            webSocketSession.sendMessage(TextMessage(mapper.writeValueAsString(Message<Any>(
                                session = sessionId,
                                messageType = MessageType.RESULT,
                                data = sanitizedLine))))
                        }
                    } else {
                        webSocketSession.sendMessage(TextMessage(mapper.writeValueAsString(Message<Any>(
                            session = sessionId,
                            messageType = MessageType.RESULT,
                            data = sanitizedLine))))
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun createErrorThread(reader: BufferedReader, webSocketSession: WebSocketSession, sessionId: String) : Thread {
        return Thread {
            try {
                var line: String?
                var lineCount = 0
                while (reader.readLine().also { line = it } != null) {
                    lineCount++
                    val sanitizedLine: String = sanitizeAnsiCodes(line!!)

                    synchronized(webSocketSession) {
                        webSocketSession.sendMessage(TextMessage(mapper.writeValueAsString(Message<Any>(
                            session = sessionId,
                            messageType = MessageType.RESULT,
                            data = sanitizedLine)))) }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
    
    private fun sanitizeAnsiCodes(input: String): String {
        // Remove ANSI escape codes and other control characters
        return TerminalWebSocketHandler.ANSI_PATTERN.matcher(input).replaceAll("")
    }
}