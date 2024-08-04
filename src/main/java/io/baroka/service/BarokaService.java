package io.baroka.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.baroka.component.SSHConnection;
import io.baroka.entity.Host;
import io.baroka.exception.InvalidException;
import io.baroka.handler.TerminalWebSocketHandler;
import io.baroka.repository.HostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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
@RequiredArgsConstructor
public class BarokaService {

    private final SSHConnection sshConnection;
    private final HostRepository hostRepository;
    public Session connectSessionWithPem(String id, String username, String host, int port, String pemPath) throws JSchException, IOException, InterruptedException {
        return sshConnection.createSessionWithPem(username, host, port, pemPath);

    }

    public Session connectSessionWithPassword(String id, String username, String host, int port, String password) throws JSchException{
        return sshConnection.createSessionWithPassword(username, host, port, password);
    }

    public void saveSSH(String id, String username, String host, int port, String password, String pemPath) {
        hostRepository.save(
                hostRepository.findByUsernameAndServerHost(username, host)
                        .orElse(Host.builder()
                                .id(id)
                                .username(username)
                                .serverHost(host)
                                .serverPort(port)
                                .password(password)
                                .pem(pemPath)
                                .build()));
    }

    public Session connectDestinationWithPassword(String destinationUsername, int localPort, String destinationPassword) throws JSchException{
        return sshConnection.createTunnelSessionWithPassword(destinationUsername, localPort, destinationPassword);
    }

    public Session connectDestinationWithPem(String destinationUsername, int localPort, String destinationPemPath) throws JSchException, IOException {
        return sshConnection.createTunnelSessionWithPem(destinationUsername, localPort, destinationPemPath);
    }

    public void saveTunneling(String id, String tunnelHost, String tunnelUsername, String tunnelPassword, String tunnelPemPath, int tunnelPort, String remoteHost, int remotePort, String destinationUsername, String destinationPassword, String destinationPemPath, int localPort) {
        hostRepository.save(Host.builder()
                .id(id)
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
    }

    public List<Host> getHostList() {
        return hostRepository.findAll();
    }

    public Boolean deleteHost(Long id) {
        try {
            hostRepository.deleteById(id);
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


