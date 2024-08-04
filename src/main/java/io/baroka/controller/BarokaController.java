package io.baroka.controller;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.baroka.entity.Host;
import io.baroka.exception.InvalidException;
import io.baroka.handler.TerminalWebSocketHandler;
import io.baroka.service.BarokaService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static io.baroka.constants.Constants.ASCIIART_BAROKA;

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
 * packageName    : io.baroka.controller
 * fileName       : BarokaController
 * author         : darren
 * date           : 7/19/24
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 7/19/24        darren       최초 생성
 */
@Controller
@RequiredArgsConstructor
public class BarokaController {

    private final BarokaService barokaService;
    @Value("${baroka.path}")
    private String path;
    @GetMapping(value =  "/")
    public String index(Model model) {
        List<Host> hostList = barokaService.getHostList();
        model.addAttribute("hostList", hostList);
        return "index";
    }

    @DeleteMapping("/deleteHost/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteHost(@PathVariable Long id) {
        boolean isDeleted = barokaService.deleteHost(id);

        if (isDeleted) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(500).build();
        }
    }


    @PostMapping("/connect")
    public String connectSSH(@RequestParam("id") String id,
                             @RequestParam("username") String username,
                             @RequestParam("host") String host,
                             @RequestParam("port") int port,
                             @RequestParam(value = "password", required = false) String password,
                             @RequestParam(value = "pemKey", required = false) MultipartFile pemKey,
                             Model model) {
        List<Host> hostList = barokaService.getHostList();
        model.addAttribute("hostList", hostList);
        try {
            Session session;
            if(pemKey != null && !pemKey.isEmpty()) {
                session = barokaService.connectSessionWithPem(id, username, host, port, uploadPem(pemKey));
            } else if (!StringUtils.isEmpty(password)) {
                session = barokaService.connectSessionWithPassword(id, username, host, port, password);
            } else {
                throw new InvalidException("Either password or PEM key must be provided.");
            }
            barokaService.saveSSH(
                    id,
                    username,
                    host,
                    port,
                    password,
                    (pemKey != null && !pemKey.isEmpty()) ? uploadPem(pemKey) : null
            );
            String sessionId = UUID.randomUUID().toString();
            TerminalWebSocketHandler.addSession(sessionId, session);

            model.addAttribute("sessionId", sessionId);
            model.addAttribute("username", username);
            List<String> barokaList = barokaService.getShell(sessionId, path);
            model.addAttribute("barokaFiles", barokaList);
            model.addAttribute("colors", generateRandomColors(barokaList.size()));
            model.addAttribute("barokaPath", path);
            return "terminal";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "SSH connection failed: " + e.getMessage());
            return "index";
        }
    }

    @PostMapping("/connectWithPath")
    public String connectSSH(@RequestParam("id") String id,
                             @RequestParam("username") String username,
                             @RequestParam("host") String host,
                             @RequestParam("port") int port,
                             @RequestParam(value = "password", required = false) String password,
                             @RequestParam("pemKeyPath") String pemPath,
                             Model model) {
        try {
            Session session;
            if(pemPath != null && !pemPath.isEmpty()) {
                session = barokaService.connectSessionWithPem(id, username, host, port, pemPath);
            } else if (!StringUtils.isEmpty(password)) {
                session = barokaService.connectSessionWithPassword(id, username, host, port, password);
            } else {
                throw new InvalidException("Either password or PEM key must be provided.");
            }
            String sessionId = UUID.randomUUID().toString();
            TerminalWebSocketHandler.addSession(sessionId, session);

            model.addAttribute("sessionId", sessionId);
            model.addAttribute("username", username);
            List<String> barokaList = barokaService.getShell(sessionId, path);
            model.addAttribute("barokaFiles", barokaList);
            model.addAttribute("colors", generateRandomColors(barokaList.size()));
            model.addAttribute("barokaPath", path);
            return "terminal";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "SSH connection failed: " + e.getMessage());
            return "index";
        }
    }

    @PostMapping("/tunnel")
    public String createTunnel(@RequestParam("id") String id,
                               @RequestParam("tunnelHost") String tunnelHost,
                               @RequestParam("tunnelPort") int tunnelPort,
                               @RequestParam("tunnelUsername") String tunnelUsername,
                               @RequestParam("tunnelPassword") String tunnelPassword,
                               @RequestParam("tunnelPemKey") MultipartFile tunnelPemKey,
                               @RequestParam("localPort") int localPort,
                               @RequestParam("remoteHost") String remoteHost,
                               @RequestParam("remotePort") int remotePort,
                               @RequestParam("destinationUsername") String destinationUsername,
                               @RequestParam("destinationPassword") String destinationPassword,
                               @RequestParam("destinationPemKey") MultipartFile destinationPemKey,
                               Model model) {
        try {
            Session tunnelSession;
            // ssh -i pem -L localport:remoteHost:remotePort tunnelUsername@tunnelHost -p 29290
            if(tunnelPemKey != null && !tunnelPemKey.isEmpty()) {
                tunnelSession = barokaService.connectSessionWithPem(id, tunnelUsername, tunnelHost, tunnelPort, uploadPem(tunnelPemKey));
            } else if (!StringUtils.isEmpty(tunnelPassword)) {
                tunnelSession = barokaService.connectSessionWithPassword(id, tunnelUsername, tunnelHost, tunnelPort, tunnelPassword);
            } else {
                throw new InvalidException("[Tunnel] Either password or PEM key must be provided.");
            }
            tunnelSession.setPortForwardingL(localPort, remoteHost, remotePort);

            Session destinationSession;
            if(destinationPemKey != null && !destinationPemKey.isEmpty()) {
                destinationSession = barokaService.connectDestinationWithPem(destinationUsername, localPort, uploadPem(destinationPemKey));
            } else if(!StringUtils.isEmpty(destinationPassword)) {
                destinationSession = barokaService.connectDestinationWithPassword(destinationUsername, localPort, destinationPassword);
            } else {
                throw new InvalidException("[Remote] Either password or PEM key must be provided.");
            }
            String sessionId = UUID.randomUUID().toString();
            TerminalWebSocketHandler.addSession("Tunnel_" + sessionId, tunnelSession);
            TerminalWebSocketHandler.addSession(sessionId, destinationSession);
            barokaService.saveTunneling(
                    id,
                    tunnelHost,
                    tunnelUsername,
                    tunnelPassword,
                    tunnelPemKey != null ? uploadPem(tunnelPemKey) : null,
                    tunnelPort,
                    remoteHost,
                    remotePort,
                    destinationUsername,
                    destinationPassword,
                    (destinationPemKey != null && !destinationPemKey.isEmpty()) ? uploadPem(destinationPemKey) : null,
                    localPort
            );
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("username", destinationUsername);
            List<String> barokaList = barokaService.getShell(sessionId, path);
            model.addAttribute("barokaFiles", barokaList);
            model.addAttribute("colors", generateRandomColors(barokaList.size()));
            model.addAttribute("barokaPath", path);
            return "terminal";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "SSH connection failed: " + e.getMessage());
            return "index";
        }
    }

    @PostMapping("/tunnelWithPath")
    public String createTunnel(@RequestParam("id") String id,
                               @RequestParam("tunnelHost") String tunnelHost,
                               @RequestParam("tunnelPort") int tunnelPort,
                               @RequestParam("tunnelUsername") String tunnelUsername,
                               @RequestParam("tunnelPassword") String tunnelPassword,
                               @RequestParam("tunnelPemKeyPath") String tunnelPemKeyPath,
                               @RequestParam("localPort") int localPort,
                               @RequestParam("remoteHost") String remoteHost,
                               @RequestParam("remotePort") int remotePort,
                               @RequestParam("destinationUsername") String destinationUsername,
                               @RequestParam("destinationPassword") String destinationPassword,
                               @RequestParam("destinationPemKeyPath") String destinationPemKeyPath,
                               Model model) {
        try {
            Session tunnelSession;
            if(tunnelPemKeyPath != null && !tunnelPemKeyPath.isEmpty()) {
                tunnelSession = barokaService.connectSessionWithPem(id, tunnelUsername, tunnelHost, tunnelPort, tunnelPemKeyPath);
            } else if (!StringUtils.isEmpty(tunnelPassword)) {
                tunnelSession = barokaService.connectSessionWithPassword(id, tunnelUsername, tunnelHost, tunnelPort, tunnelPassword);
            } else {
                throw new InvalidException("[Tunnel] Either password or PEM key must be provided.");
            }
            tunnelSession.setPortForwardingL(localPort, remoteHost, remotePort);

            Session destinationSession;
            if(destinationPemKeyPath != null && !destinationPemKeyPath.isEmpty()) {
                destinationSession = barokaService.connectDestinationWithPem(destinationUsername, localPort, destinationPemKeyPath);
            } else if(!StringUtils.isEmpty(destinationPassword)) {
                destinationSession = barokaService.connectDestinationWithPassword(destinationUsername, localPort, destinationPassword);
            } else {
                throw new InvalidException("[Remote] Either password or PEM key must be provided.");
            }
            String sessionId = UUID.randomUUID().toString();
            TerminalWebSocketHandler.addSession("Tunnel_" + sessionId, tunnelSession);
            TerminalWebSocketHandler.addSession(sessionId, destinationSession);

            model.addAttribute("sessionId", sessionId);
            model.addAttribute("username", destinationUsername);
            List<String> barokaList = barokaService.getShell(sessionId, path);
            model.addAttribute("barokaFiles", barokaList);
            model.addAttribute("colors", generateRandomColors(barokaList.size()));
            model.addAttribute("barokaPath", path);
            return "terminal";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "SSH connection failed: " + e.getMessage());
            return "index";
        }
    }

    @GetMapping("/file-list")
    public String getFileList(@RequestParam("sessionId") String sessionId, Model model) throws JSchException, IOException {
        List<String> barokaList = barokaService.getShell(sessionId, path);
        model.addAttribute("barokaFiles", barokaList);
        model.addAttribute("colors", generateRandomColors(barokaList.size()));
        return "terminal :: file-list";
    }

    private String uploadPem(MultipartFile pem) {
        try {
            String uploadDir = "pem-keys/";
            String fileName = pem.getOriginalFilename();
            Path filePath = Paths.get(uploadDir + fileName);

            if(!Files.exists(filePath.getParent())) {
                Files.createDirectories(filePath.getParent());
            }

            Files.write(filePath, pem.getBytes());
            return filePath.toString();
        } catch (IOException e) {
            e.printStackTrace();
            throw new InvalidException("Failed to save the pem key.");
        }

    }

    private List<String> generateRandomColors(int count) {
        List<String> colors = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            int r = random.nextInt(256);
            int g = random.nextInt(256);
            int b = random.nextInt(256);
            colors.add(String.format("#%02x%02x%02x", r, g, b));
        }
        return colors;
    }

}
