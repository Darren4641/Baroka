package io.baroka.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.baroka.config.MessagingConfig;
import io.baroka.model.Message;
import io.baroka.model.MessageType;
import io.baroka.model.VI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static io.baroka.constants.Constants.BAROKA_PATH;
import static io.baroka.model.MessageType.VI;

/**
 * -------------------------------------------------------------------------------------
 * ::::::'OO::::'OOO::::'OO:::'OO:'OO::::'OO:'OOOOOOOO:::'OOOOOOO::'OO::::'OO:'OO....OO:
 * :::::: OO:::'OO OO:::. OO:'OO:: OO::::.OO: OO.....OO:'OO.....OO: OO:::: OO: OOO...OO:
 * :::::: OO::'OO:..OO:::. OOOO::: OO::::.OO: OO::::.OO: OO::::.OO: OO:::: OO: OOOO..OO:
 * :::::: OO:'OO:::..OO:::. OO:::: OO::::.OO: OOOOOOOO:: OO::::.OO: OO:::: OO: OO.OO.OO:
 * OO:::: OO: OOOOOOOOO:::: OO:::: OO::::.OO: OO.. OO::: OO::::.OO: OO:::: OO: OO..OOOO:
 * :OO::::OO: OO.....OO:::: OO:::: OO::::.OO: OO::. OO:: OO::::.OO: OO:::: OO: OO:..OOO:
 * ::OOOOOO:: OO:::..OO:::: OO::::. OOOOOOO:: OO:::. OO:. OOOOOOO::. OOOOOOO:: OO::..OO:
 * :......:::..:::::..:::::..::::::.......:::..:::::..:::.......::::.......:::..::::..::
 * <p>
 * packageName    : io.baroka.handler
 * fileName       : TerminalWebSocketHandler
 * author         : darren
 * date           : 7/20/24
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 7/20/24        darren       최초 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper mapper;
    private final MessagingConfig messagingConfig;
    private ConcurrentHashMap<String, ScheduledExecutorService> executor = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Integer> missedACK = new ConcurrentHashMap<>();
    private static final Map<String, String> pathMap = new ConcurrentHashMap<>();
    private static final Map<String, Session> sessionMap = new ConcurrentHashMap<>();
    private static final Map<String, ChannelShell> shellChannelMap = new ConcurrentHashMap<>();
    private static final Map<String, Process> processMap = new ConcurrentHashMap<>();

    // ANSI escape code pattern, including specific sequences like \u001B[?2004l
    private static final Pattern ANSI_PATTERN = Pattern.compile("\\x1B\\[[0-9;?]*[a-zA-Z]");

    @Value("${baroka.path}")
    private String path;
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (sessionMap.isEmpty()) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(Message.builder()
                    .messageType(MessageType.EXIT)
                    .data("[bad] No session ID provided")
                    .build())));
            session.close();
        }

        doACK(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        missedACKClear(session.getId());
        String payload = message.getPayload();
        log.info("payload : {}", payload);

        Message messageDto = mapper.readValue(payload, Message.class);

        boolean hasSession = true;
        if (messageDto.getMessageType().equals(MessageType.ENTER)) {
            String sessionId = messageDto.getSession();
            Session SSHSession = sessionMap.get(sessionId);
            if (SSHSession != null) {
                session.getAttributes().put("sshSession", SSHSession);
                pathMap.put(sessionId, "~"); // 초기 경로 설정
                startShellChannel(session, SSHSession, sessionId);
            } else {
                hasSession = false;
            }

        } else if (messageDto.getMessageType().equals(MessageType.COMMAND)) {
            Session sshSession = (Session) session.getAttributes().get("sshSession");
            if (sshSession != null) {
                try {

                    if(((String) messageDto.getData()).equals("exit")) {
                        String tunnelSessionId = "Tunnel_" + messageDto.getSession();
                        String sessionId = messageDto.getSession();
                        session.sendMessage(new TextMessage(mapper.writeValueAsString(Message.builder()
                                .messageType(MessageType.EXIT)
                                .data("")
                                .build())));
                        disconnectSession(tunnelSessionId, session);
                        disconnectSession(sessionId, session);

                    } else if(((String) messageDto.getData()).startsWith("vi")) {
                        String command = ((String) messageDto.getData());
                        String fileName = command.substring(2).isEmpty() ? "" : command.split(" ")[1];
                        session.sendMessage(new TextMessage(mapper.writeValueAsString(Message.builder()
                                .messageType(VI)
                                .data(fileName)
                                .build())));
                    }else {
                        sendCommandToShell(session, messageDto);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    session.sendMessage(new TextMessage(mapper.writeValueAsString(Message.builder()
                            .messageType(MessageType.COMMAND)
                            .data("Error executing command: " + e.getMessage())
                            .build())));
                }
            } else {
                hasSession = false;
            }
        } else if (messageDto.getMessageType().equals(MessageType.AUTOCOMPLETE)) {
            Session sshSession = (Session) session.getAttributes().get("sshSession");
            if (sshSession != null) {
                try {
                    autoCompleteCommand(session, sshSession, messageDto);
                } catch (Exception e) {
                    session.sendMessage(new TextMessage(mapper.writeValueAsString(Message.builder()
                            .messageType(MessageType.COMMAND)
                            .data("Error during autocomplete: " + e.getMessage())
                            .build())));
                }
            } else {
                hasSession = false;
            }
        } else if (messageDto.getMessageType().equals(MessageType.SIGNAL)) {
            Session sshSession = (Session) session.getAttributes().get("sshSession");
            if (sshSession != null) {
                try {
                    sendSignalToShell(session, sshSession, messageDto);
                } catch (Exception e) {
                    session.sendMessage(new TextMessage(mapper.writeValueAsString(Message.builder()
                            .messageType(MessageType.COMMAND)
                            .data("Error sending signal: " + e.getMessage())
                            .build())));
                }
            } else {
                hasSession = false;
            }
        } else if(messageDto.getMessageType().equals(MessageType.EXIT)) {
            String tunnelSessionId = "Tunnel_" + messageDto.getSession();
            String sessionId = messageDto.getSession();

            disconnectSession(tunnelSessionId, session);
            disconnectSession(sessionId, session);
        } else if(messageDto.getMessageType().equals(MessageType.VI_OPERATION)) {
            handleFileOperation(session, messageDto);
        }  else if (messageDto.getMessageType().equals(MessageType.VI_CONTENT)) {
            fetchFileContent(session, messageDto);
        }

        if (!hasSession) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(Message.builder()
                    .messageType(MessageType.COMMAND)
                    .data("No SSH session available")
                    .build())));
            session.close();
        }
    }

    private void disconnectSession(String sessionId, WebSocketSession session) throws IOException {
        Session sshSession = sessionMap.get(sessionId);
        if(sshSession != null) {
            sshSession.disconnect();
            sessionMap.remove(sessionId);
        }
        pathMap.remove(sessionId);
        processMap.remove(sessionId);
        session.close();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = (String) session.getAttributes().get("sessionId");
        if (sessionId != null) {
            Session sshSession = sessionMap.get(sessionId);
            if (sshSession != null) {
                sshSession.disconnect();
            }
            removeSession(sessionId);
        }
        super.afterConnectionClosed(session, status);
    }

    private void startShellChannel(WebSocketSession webSocketSession, Session sshSession, String sessionId) throws JSchException, IOException {
        ChannelShell channel = (ChannelShell) sshSession.openChannel("shell");

        // Set up input and output streams for the shell channel
        PipedInputStream pipedIn = new PipedInputStream();
        PipedOutputStream pipedOut = new PipedOutputStream(pipedIn);
        channel.setInputStream(new BufferedInputStream(pipedIn));

        PipedInputStream pipedErrIn = new PipedInputStream();
        PipedOutputStream pipedErrOut = new PipedOutputStream(pipedErrIn);

        // Set output streams to send data back to WebSocket
        channel.setOutputStream(new WebSocketOutputStream(webSocketSession));
        channel.setExtOutputStream(new WebSocketOutputStream(webSocketSession));

        shellChannelMap.put(sessionId, channel);
        channel.connect();

        // Read from shell and send to WebSocket
        BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream(), StandardCharsets.UTF_8));
        BufferedReader errReader = new BufferedReader(new InputStreamReader(pipedErrIn, StandardCharsets.UTF_8));

        Thread outputThread = new Thread(() -> {
            try {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    lineCount++;
                    String sanitizedLine = sanitizeAnsiCodes(line);
                    if (sanitizedLine.contains("[baroka_path]")) {
                        String newPath = sanitizedLine.split(":")[1];
                        pathMap.put(sessionId, newPath);

                        webSocketSession.sendMessage(new TextMessage(mapper.writeValueAsString(new Message<>(sessionId, MessageType.PATH, newPath))));
                    } else if (sanitizedLine.contains("Preparing")) {
                        synchronized (webSocketSession) {
                            webSocketSession.sendMessage(new TextMessage(mapper.writeValueAsString(new Message<>(sessionId, MessageType.RESULT, sanitizedLine))));
                        }
                    } else {
                        webSocketSession.sendMessage(new TextMessage(mapper.writeValueAsString(new Message<>(sessionId, MessageType.RESULT, sanitizedLine))));
                    }

                }

            } catch (IOException e) {
                log.error("Error reading from shell channel", e);
            }
        });

        // Read from shell error stream and send to WebSocket
        Thread errorThread = new Thread(() -> {
            try {
                String line;
                while ((line = errReader.readLine()) != null) {
                    String sanitizedLine = sanitizeAnsiCodes(line);
                    synchronized (webSocketSession) {
                        webSocketSession.sendMessage(new TextMessage(mapper.writeValueAsString(new Message<>(sessionId, MessageType.RESULT, sanitizedLine))));
                    }
                }
            } catch (IOException e) {
                log.error("Error reading from shell error stream", e);
            }
        });

        outputThread.start();
        errorThread.start();

        // Store threads to interrupt later
        webSocketSession.getAttributes().put("outputThread", outputThread);
        webSocketSession.getAttributes().put("errorThread", errorThread);
    }

    private void sendCommandToShell(WebSocketSession webSocketSession, Message messageDto) throws IOException, JSchException {
        String command = (String) messageDto.getData();
        String sessionId = messageDto.getSession();
        ChannelShell channel = shellChannelMap.get(sessionId);

        if (channel != null) {
            try {
                OutputStream outputStream = channel.getOutputStream();

                if(command.startsWith("vi") || command.startsWith("nano")) {
                    outputStream.write("echo \"[Preparing] This feature is being prepared.\"\n".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                } else if (command.startsWith("cd ")) {
                    outputStream.write((command + "\n").getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    outputStream.write("echo \"[baroka_path]:\"  $(pwd)\n".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                } else {
                    outputStream.write((command + "\n").getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                }


            } catch (IOException e) {
                // 명령어 실행 중 예외 발생 시 오류 메시지 전송
                webSocketSession.sendMessage(new TextMessage(mapper.writeValueAsString(
                        Message.builder().messageType(MessageType.COMMAND)
                                .data("Error executing command: " + e.getMessage())
                                .build())));
                log.error("Error executing command for session {}: {}", sessionId, e.getMessage(), e);
            }
        }
    }


    private void autoCompleteCommand(WebSocketSession webSocketSession, Session sshSession, Message<String> messageDto) throws IOException, JSchException {
        String command = messageDto.getData();
        String sessionId = messageDto.getSession();
        String currentPath = pathMap.getOrDefault(sessionId, "~");

        // 파일 이름 추출
        String fileNamePrefix = command.contains(" ") ? command.substring(command.lastIndexOf(' ') + 1) : command;

        log.info("Starting autocomplete for file: {} in path: {}", fileNamePrefix, currentPath);

        ChannelExec channelExec = (ChannelExec) sshSession.openChannel("exec");
        ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
        channelExec.setOutputStream(responseStream);
        channelExec.setCommand("cd " + currentPath + " && compgen -f " + fileNamePrefix);
        channelExec.connect();

        InputStream in = channelExec.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder autoCompleteResult = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            autoCompleteResult.append(line).append("\n");
        }

        log.info("Autocomplete Result: {}", autoCompleteResult.toString());

        webSocketSession.sendMessage(new TextMessage(mapper.writeValueAsString(new Message<>(sessionId, MessageType.AUTOCOMPLETE, autoCompleteResult.toString()))));

        channelExec.disconnect();
    }

    private void getBarokaDirectory(WebSocketSession webSocketSession, Session sshSession) throws JSchException, IOException {
        // Create directory if not exists
        String checkAndCreateCommand = "mkdir -p " + BAROKA_PATH;
        executeCommand(sshSession, checkAndCreateCommand);

        // List .sh files in the directory
        String listFilesCommand = "ls " + BAROKA_PATH + "*.sh";
        String files = executeCommand(sshSession, listFilesCommand);

        // Send the file list back to the client
        webSocketSession.sendMessage(new TextMessage(mapper.writeValueAsString(
                Message.builder().messageType(MessageType.COMMAND)
                        .data(files)
                        .build())));
    }

    private void sendSignalToShell(WebSocketSession webSocketSession, Session sshSession, Message messageDto) throws IOException, JSchException {
        String signal = (String) messageDto.getData();
        String sessionId = messageDto.getSession();
        ChannelShell channel = shellChannelMap.get(sessionId);

        if (channel != null) {
            try {
                // SIGINT 신호를 보냅니다. 실제로 JSch 라이브러리는 신호 전송을 직접 지원하지 않습니다.
                // 그러나 이 방법으로 가상의 Ctrl+C를 구현할 수 있습니다.
                OutputStream outputStream = channel.getOutputStream();
                outputStream.write(("\u0003").getBytes(StandardCharsets.UTF_8)); // \u0003는 Ctrl+C의 ASCII 코드입니다.
                outputStream.flush();

                log.info("Sent SIGINT signal to session: {}", sessionId);
            } catch (IOException e) {
                log.error("Error sending signal to session {}: {}", sessionId, e.getMessage(), e);
            }
        } else {
            log.error("No active channel found for session: {}", sessionId);
        }
    }


    public static void addSession(String sessionId, Session session) {
        sessionMap.put(sessionId, session);
    }

    public static Session getSession(String sessionId) {
        return sessionMap.get(sessionId);
    }

    public static void removeSession(String sessionId) {
        sessionMap.remove(sessionId);
        ChannelShell channel = shellChannelMap.remove(sessionId);
        if (channel != null) {
            channel.disconnect();
        }
    }

    private void doACK(WebSocketSession session) {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executor.put(session.getId(), executorService);
        missedACK.put(session.getId(), 0);

        executorService.scheduleAtFixedRate(() -> {
            try {
                if (session.isOpen()) {
                    Integer missedCount = missedACK.getOrDefault(session.getId(), 0);
                    if (missedCount >= messagingConfig.getAckLimitCount()) {
                        session.close();
                        executorService.shutdown();
                        executor.remove(session.getId());
                        missedACK.remove(session.getId());
                    } else {
                        session.sendMessage(new TextMessage(mapper.writeValueAsString(Message.builder()
                                .messageType(MessageType.ACK)
                                .build())));
                        missedACK.put(session.getId(), missedCount + 1);
                    }
                } else {
                    executorService.shutdown();
                    executor.remove(session.getId());
                    missedACK.remove(session.getId());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, messagingConfig.getAckTime(), TimeUnit.MILLISECONDS);
    }


    private String executeCommand(Session session, String command) throws JSchException, IOException {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
        channel.setOutputStream(responseStream);
        channel.connect();

        String response = responseStream.toString();
        channel.disconnect();
        return response;
    }

    private void missedACKClear(String sessionId) {
        missedACK.put(sessionId, 0);
    }

    private String sanitizeAnsiCodes(String input) {
        // Remove ANSI escape codes and other control characters
        return ANSI_PATTERN.matcher(input).replaceAll("");
    }

    private void handleFileOperation(WebSocketSession session, Message messageDto) throws IOException, JSchException {
        Gson gson = new Gson();

        io.baroka.model.VI vi = gson.fromJson((String) messageDto.getData(), io.baroka.model.VI.class);
        String sessionId = messageDto.getSession();

        try {
            switch (vi.getOperation()) {
                case "SAVE":
                    saveFileContent(sessionId, vi.getTitle(), vi.getContent(), vi.getRemoteDir(), vi.getIsBaroka());
                    break;

            }
        }catch (Exception e) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(
                    Message.builder().messageType(MessageType.COMMAND)
                            .data("Error executing command: " + e.getMessage())
                            .build())));
        }

    }
    private void saveFileContent(String sessionId, String title, String content, String remoteDir, Boolean isBaroka) throws JSchException, IOException {
        Session session = TerminalWebSocketHandler.getSession(sessionId);
        if (session == null) {
            throw new RuntimeException("SSH session not found");
        }
        if(isBaroka && !title.contains(".sh")) {
            StringBuilder sb = new StringBuilder();
            sb.append(title);
            sb.append(".sh");
            title = sb.toString();
        }

        String remoteFilePath = remoteDir + "/" + title;
        String command = String.format("cat > %s << 'EOF'\n%s\nEOF\n", remoteFilePath, content);

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
        channel.setOutputStream(responseStream);
        channel.connect();
        if (isBaroka) {
            setExecutablePermission(session, remoteFilePath);
        }
        // Check for errors in the command execution
        InputStream in = channel.getInputStream();
        int exitStatus = channel.getExitStatus();
        if (exitStatus != 0) {
            String response = responseStream.toString();
            throw new IOException("File transfer failed with exit status: " + exitStatus + ". Response: " + response);
        }

        channel.disconnect();
    }

    private void setExecutablePermission(Session session, String remoteFilePath) throws JSchException, IOException {
        String command = "chmod +x " + remoteFilePath;
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
        channel.setOutputStream(responseStream);
        channel.connect();

        // Check for errors in the command execution
        InputStream in = channel.getInputStream();
        int exitStatus = channel.getExitStatus();
        if (exitStatus != 0) {
            String response = responseStream.toString();
            throw new IOException("Setting executable permission failed with exit status: " + exitStatus + ". Response: " + response);
        }

        channel.disconnect();
    }

    private void fetchFileContent(WebSocketSession session, Message messageDto) throws JSchException, IOException {
        String sessionId = messageDto.getSession();
        Gson gson = new Gson();
        io.baroka.model.VI vi = gson.fromJson((String) messageDto.getData(), io.baroka.model.VI.class);
        Session sshSession = TerminalWebSocketHandler.getSession(sessionId);

        if (sshSession == null) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(Message.builder()
                    .messageType(MessageType.RESULT)
                    .data("SSH session not found")
                    .build())));
            return;
        }

        ChannelExec channel = (ChannelExec) sshSession.openChannel("exec");
        ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
        channel.setOutputStream(responseStream);
        channel.setCommand("cat " + vi.getRemoteDir() + "/" + vi.getTitle());
        channel.connect();

        InputStream in = channel.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder fileContent = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            fileContent.append(line).append("\n");
        }

        channel.disconnect();

        session.sendMessage(new TextMessage(mapper.writeValueAsString(Message.builder()
                .messageType(MessageType.VI_CONTENT)
                .data(fileContent.toString())
                .build())));
    }

    private static class WebSocketOutputStream extends OutputStream {
        private final WebSocketSession session;

        public WebSocketOutputStream(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void write(int b) throws IOException {
            session.sendMessage(new TextMessage(new byte[]{(byte) b}));
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            session.sendMessage(new TextMessage(new String(b, off, len, StandardCharsets.UTF_8)));
        }
    }
}