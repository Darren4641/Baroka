package io.baroka.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.baroka.component.SSHConnection;
import io.baroka.entity.Host;
import io.baroka.handler.TerminalWebSocketHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.baroka.constants.Constants.BAROKA_PATH;

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
 * packageName    : io.baroka.service
 * fileName       : SSHService
 * author         : darren
 * date           : 7/19/24
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 7/19/24        darren       최초 생성
 */
@Service
public class BarokaService {

    private final SSHConnection sshConnection;
    //private final HostRepository hostRepository;
    private final File file;
    private final ObjectMapper objectMapper;

    public BarokaService(SSHConnection sshConnection) {
        this.sshConnection = sshConnection;
        this.file = new File("data/hosts.json");
        objectMapper = new ObjectMapper();
    }
    @PostConstruct
    public void init() throws IOException {
        if (!file.exists()) {
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            file.createNewFile();
            objectMapper.writeValue(file, new ArrayList<Host>());
        }
    }

    public Session connectSessionWithPem(String id, String username, String host, int port, String pemPath) throws JSchException, IOException, InterruptedException {
        return sshConnection.createSessionWithPem(username, host, port, pemPath);

    }

    public Session connectSessionWithPassword(String id, String username, String host, int port, String password) throws JSchException{
        return sshConnection.createSessionWithPassword(username, host, port, password);
    }

    public void saveSSH(String title, String username, String host, int port, String password, String pemPath) throws IOException {
        List<Host> hosts = getHostList();
        hosts.add(Host.builder()
                .idx(UUID.randomUUID().toString())
                .title(title)
                .username(username)
                .serverHost(host)
                .serverPort(port)
                .password(password)
                .pem(pemPath)
                .build());
        objectMapper.writeValue(file, hosts);
    }

    public Session connectDestinationWithPassword(String destinationUsername, int localPort, String destinationPassword) throws JSchException{
        return sshConnection.createTunnelSessionWithPassword(destinationUsername, localPort, destinationPassword);
    }

    public Session connectDestinationWithPem(String destinationUsername, int localPort, String destinationPemPath) throws JSchException, IOException, IllegalAccessException {
        return sshConnection.createTunnelSessionWithPem(destinationUsername, localPort, destinationPemPath);
    }

    public void saveTunneling(String title, String tunnelHost, String tunnelUsername, String tunnelPassword, String tunnelPemPath, int tunnelPort, String remoteHost, int remotePort, String destinationUsername, String destinationPassword, String destinationPemPath, int localPort) throws IOException {
        List<Host> hosts = getHostList();
        hosts.add(Host.builder()
                .idx(UUID.randomUUID().toString())
                .title(title)
                .username(destinationUsername)
                .serverHost(remoteHost)
                .serverPort(remotePort)
                .password(destinationPassword)
                .pem(destinationPemPath)
                .tunnelHost(tunnelHost)
                .tunnelUsername(tunnelUsername)
                .tunnelPassword(tunnelPassword)
                .tunnelPort(tunnelPort)
                .tunnelPem(tunnelPemPath)
                .localPort(localPort)
                .build());
        objectMapper.writeValue(file, hosts);
    }

    public List<Host> getHostList() throws IOException {
        if (!file.exists()) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(file, new TypeReference<List<Host>>() {});
    }

    public Boolean deleteHost(String id) {
        try {
            List<Host> hosts = getHostList();
            hosts.removeIf(host -> host.getIdx().equals(id));
            objectMapper.writeValue(file, hosts);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<String> getShell(String sessionId, String path) throws JSchException, IOException {
        Session session = TerminalWebSocketHandler.getSession(sessionId);
        executeCommand(session, "mkdir -p " + path);

        // 특정 경로에서 실행 권한이 있는 .sh 파일들만 찾는 명령어
        List<String> shellFiles = executeCommand(session, "sh -c 'find " + path + " -type f -name \"*.sh\" -perm /111'");

        return shellFiles.stream()
                .map(shell -> {
                    String[] splitShell = shell.split("/");
                    return splitShell[splitShell.length - 1];
                })
                .collect(Collectors.toList());
    }

    public void saveFileContent(String sessionId, String fileName, String content) throws JSchException, IOException {
        Session session = TerminalWebSocketHandler.getSession(sessionId);
        if (session == null) {
            throw new RuntimeException("SSH session not found");
        }

        String remoteFilePath = BAROKA_PATH + "/" + fileName;

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        OutputStream out = channel.getOutputStream();
        InputStream in = channel.getInputStream();
        channel.connect();

        out.write("\0".getBytes());
        out.flush();

        if (in.read() != 0) {
            throw new IOException("File transfer failed.");
        }
        channel.disconnect();
    }


    private List<String> executeCommand(Session session, String command) throws JSchException, IOException {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);

        // Stream to capture command output
        InputStream in = channel.getInputStream();
        channel.connect();

        // Buffer for reading command output
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }

        // Ensure the channel is disconnected properly
        channel.disconnect();
        return lines;
    }
}


