package io.baroka.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.Session
import io.baroka.config.MessagingConfig
import io.baroka.model.Message
import io.baroka.model.MessageType
import io.baroka.service.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern


@Component
class TerminalWebSocketHandler (
    val messageConfig : MessagingConfig,
    val enterService: EnterService,
    val commandService: CommandService,
    val autoCompleteService: AutoCompleteService,
    val signalService: SignalService,
    val viService: ViService,
    @Value("\${baroka.path}")
    val path: String
) : TextWebSocketHandler() {

    companion object {
        val ANSI_PATTERN: Pattern = Pattern.compile("\\x1B\\[[0-9;?]*[a-zA-Z]")
        val mapper: ObjectMapper = ObjectMapper()
        val executor = ConcurrentHashMap<String, ScheduledExecutorService>()
        val missedACK = ConcurrentHashMap<String, Int>()
        val pathMap = ConcurrentHashMap<String, String>()
        val sessionMap = ConcurrentHashMap<String, Session>()
        val shellChannelMap = ConcurrentHashMap<String, ChannelShell>()
        val processMap = ConcurrentHashMap<String, Process>()
        val localPortMap = ConcurrentHashMap<String, Int>()

        fun addSession(sessionId: String, session: Session) {
            sessionMap.put(sessionId, session)
        }

        fun getSession(sessionId: String) : Session? = sessionMap.get(sessionId)
    }


    @Override
    override fun afterConnectionEstablished(session : WebSocketSession) {
        if(sessionMap.isEmpty()) {
            session.sendMessage(TextMessage(mapper.writeValueAsString(
                Message<String>(
                    messageType = MessageType.EXIT,
                    data = "[bad] No session ID provided"))))
            session.close()
        }
        doACK(session)
    }

    @Override
    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        missedACKClear(session.id)
        val payload = message.payload

        val messageDto = mapper.readValue(payload, Message::class.java)

        var hasSession = true
        if(messageDto.messageType == MessageType.ENTER) {
            val SSHSession = sessionMap.get(session.id)
            if(SSHSession != null) {
                session.attributes.put("sshSession", SSHSession)
                pathMap.put(session.id, "~")
                enterService.startShellChannel(session, SSHSession, session.id)
            } else {
                hasSession = false
            }
            if(messageDto.localPort != null) localPortMap.put(session.id, messageDto.localPort)
        } else if(messageDto.messageType == MessageType.COMMAND) {
            val sshSession = session.attributes["sshSession"] as Session?
            if (sshSession != null) {
                try {
                    if (messageDto.data as String == "exit") {
                        commandService.exitCommand(
                            webSocketSession = session,
                            session = sshSession,
                            messageDto = messageDto)
                    } else if((messageDto.data as String).contains("vi")) {
                        commandService.viCommand(
                            webSocketSession = session,
                            session = sshSession,
                            messageDto = messageDto)
                    } else {
                        commandService.command(
                            webSocketSession = session,
                            session = sshSession,
                            messageDto = messageDto)
                    }
                } catch (e: Exception) {
                    e.printStackTrace();
                    session.sendMessage(TextMessage(mapper.writeValueAsString(Message(
                        messageType = MessageType.COMMAND,
                        data = "Error executing command: " + e.message))))
                }
            } else {
                hasSession = false
            }

        } else if(messageDto.messageType == MessageType.AUTOCOMPLETE) {
            val sshSession = session.attributes["sshSession"] as Session?
            if(sshSession != null) {
                try {
                    autoCompleteService.autoCompleteCommand(session, sshSession, messageDto as Message<String>)
                } catch (e: Exception) {
                    session.sendMessage(TextMessage(mapper.writeValueAsString(Message(
                        messageType = MessageType.COMMAND,
                        data = "Error during autocomplete: " + e.message))))
                }
            } else {
                hasSession = false
            }
        } else if(messageDto.messageType == MessageType.SIGNAL) {
            val sshSession = session.attributes["sshSession"] as Session?
            if(sshSession != null) {
                try {
                    signalService.sendSignalToShell(session, sshSession, messageDto as Message<String>)
                } catch (e: Exception) {
                    session.sendMessage(TextMessage(mapper.writeValueAsString(Message(
                        messageType = MessageType.COMMAND,
                        data = "Error sending signal: " + e.message))))
                }
            } else {
                hasSession = false
            }
        } else if(messageDto.messageType == MessageType.VI_OPERATION) {
            viService.handleFileOperation(session, message as Message<String>)
        } else if(messageDto.messageType == MessageType.VI_CONTENT) {
            viService.fetchFileContent(session, message as Message<String>)
        }

        synchronized(session) {
            if(!hasSession) {
                session.sendMessage(TextMessage(mapper.writeValueAsString(Message(
                    messageType = MessageType.COMMAND,
                    data = "No SSH session available"))))
                session.close()
            }
        }

    }

    @Override
    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val sessionId = session.attributes.get("sessionId")

        val tunnelSessionId = "Tunnel_" + sessionId
        if(sessionId != null) {
            sessionMap.get(sessionId)?.disconnect()
            val tunnelSession = sessionMap.get(tunnelSessionId)
            if(tunnelSession != null) {
                localPortMap.get(sessionId)?.let { tunnelSession.delPortForwardingL(it) }
                Thread.sleep(100)
                tunnelSession.disconnect()
            }
            sessionMap.remove(sessionId)
            shellChannelMap.remove(sessionId)?.disconnect()
        }
        super.afterConnectionClosed(session, status)
    }



    private fun doACK(session: WebSocketSession) {
        val executorService = Executors.newSingleThreadScheduledExecutor()
        executor.put(session.id, executorService)
        missedACK.put(session.id, 0)

        executorService.scheduleAtFixedRate({
            try {
                if(session.isOpen) {
                    var missedCount = missedACK.getOrDefault(session.id, 0)
                    if (missedCount >= messageConfig.ackLimitCount) {
                        session.close()
                        executorService.shutdown()
                        executor.remove(session.id)
                        missedACK.remove(session.id)
                    } else {
                        session.sendMessage(TextMessage(mapper.writeValueAsString(Message<String>(
                            messageType = MessageType.ACK
                        ))))
                        missedACK.put(session.id, missedCount + 1)
                    }
                } else {
                    executorService.shutdown()
                    executor.remove(session.id)
                    missedACK.remove(session.id)
                }
            }catch (e: Exception) {
                e.printStackTrace()
            }
        }, 0, messageConfig.ackTime, TimeUnit.MILLISECONDS)

    }

    private fun missedACKClear(sessionId: String) {
        missedACK.put(sessionId, 0)
    }






}