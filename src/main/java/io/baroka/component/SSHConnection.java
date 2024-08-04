package io.baroka.component;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.Properties;
import java.util.Set;

import static io.baroka.constants.Constants.*;

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
 * packageName    : io.baroka.component
 * fileName       : SSHConnection
 * author         : darren
 * date           : 7/19/24
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 7/19/24        darren       최초 생성
 */
@Component
@RequiredArgsConstructor
public class SSHConnection {
    private final JSch jsch;

    public Session createSessionWithPem(String username, String host, int port, String pemPath) throws JSchException, IOException, InterruptedException {
        byte[] pemBytes = Files.readAllBytes(Paths.get(pemPath));
        jsch.addIdentity(KEY_NAME, pemBytes, null, null);
        Session session = jsch.getSession(username, host, port);

        Properties config = new Properties();
        config.put(STRICT_HOST_KEY_CHECKING, NO);
        session.setConfig(config);


        session.connect();

        return session;
    }

    public Session createSessionWithPassword(String username, String host, int port, String password) throws JSchException {
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        Properties config = new Properties();
        config.put(STRICT_HOST_KEY_CHECKING, NO);
        session.setConfig(config);
        session.connect();
        return session;
    }

    public Session createTunnelSessionWithPassword(String destinationUsername, int localPort, String destinationPassword) throws JSchException {
        Session session = jsch.getSession(destinationUsername, LOCAL_HOST, localPort);
        session.setPassword(destinationPassword);
        Properties config = new Properties();
        config.put(STRICT_HOST_KEY_CHECKING, NO);
        session.setConfig(config);
        session.connect();
        return session;
    }

    public Session createTunnelSessionWithPem(String destinationUsername, int localPort, String destinationPemPath) throws JSchException, IOException {
        byte[] pemBytes = Files.readAllBytes(Paths.get(destinationPemPath));
        jsch.addIdentity(KEY_NAME, pemBytes, null, null);

        Session session = jsch.getSession(destinationUsername, LOCAL_HOST, localPort);
        Properties config = new Properties();
        config.put(STRICT_HOST_KEY_CHECKING, NO);
        session.setConfig(config);
        session.connect();
        return session;
    }
//    public void createTunnel(String username, String host, int port, InputStream privateKeyInputStream, int localPort, int remotePort, String remoteHost) throws Exception {
//        Session session = createSession(username, host, port, privateKeyInputStream);
//        session.setPortForwardingL(localPort, remoteHost, remotePort);
//    }

}
