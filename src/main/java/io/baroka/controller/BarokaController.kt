package io.baroka.controller

import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import io.baroka.exception.InvalidException
import io.baroka.handler.TerminalWebSocketHandler
import io.baroka.service.BarokaService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.net.BindException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*


@Controller
class BarokaController (
    val barokaService: BarokaService,
    @Value("\${baroka.path}")
    val path: String
) {

    @GetMapping("/")
    fun index(model: Model) : String {
        val hostList = barokaService.getHostList()
        model.addAttribute("hostList", hostList)
        return "index"
    }

    @DeleteMapping("/deleteHost/{id}")
    @ResponseBody
    fun deleteHost(@PathVariable id: String) : ResponseEntity<Void> {
        val isDeleted = barokaService.deleteHost(id)

        return if(isDeleted) {
            ResponseEntity.ok().build()
        } else {
            ResponseEntity.status(500).build()
        }
    }

    @PostMapping("/connect")
    fun connectSSH(@RequestParam title: String,
                   @RequestParam username: String,
                   @RequestParam host: String,
                   @RequestParam port: Int,
                   @RequestParam(value = "password", required = false) password: String?,
                   @RequestParam(value = "pemKey", required = false) pemKey: MultipartFile?,
                   model: Model) : String {
        val hostList = barokaService.getHostList()
        model.addAttribute("hostList", hostList)

        try {
            val session: Session = connectPemOrPassword(
                username = username,
                host = host,
                port = port,
                pemPath = pemKey?.takeIf { !it.isEmpty }?.let { uploadPem(it) },
                password = password)

            barokaService.saveSSH(
                title = title,
                username = username,
                host = host,
                port = port,
                password = password,
                pemPath = pemKey?.takeIf { !it.isEmpty }?.let { uploadPem(it) })

            val sessionId = UUID.randomUUID().toString()
            TerminalWebSocketHandler.addSession(sessionId, session)

            val barokaList = barokaService.getBarokaShell(sessionId, path)
            addBarokaAttributes(
                model = model,
                sessionId = sessionId,
                username = username,
                barokaList = barokaList,
                title = title,
                localPort = null,
            )

            return "terminal"
        } catch (e: Exception) {
            e.printStackTrace()
            model.addAttribute("error", "SSH connection failed: " + e.message)
            return "index"
        }
    }

    @PostMapping("/connectWithPath")
    fun connectSSH (@RequestParam("title") title: String,
                    @RequestParam("username") username: String,
                    @RequestParam("host") host: String,
                    @RequestParam("port") port: Int,
                    @RequestParam(value = "password", required = false) password: String?,
                    @RequestParam("pemKeyPath") pemPath: String?,
                    model: Model) : String {
        try {
            val session: Session = connectPemOrPassword(
                username = username,
                host = host,
                port = port,
                pemPath = pemPath,
                password = password)

            val sessionId = UUID.randomUUID().toString()
            TerminalWebSocketHandler.addSession(sessionId, session)

            val barokaList = barokaService.getBarokaShell(sessionId, path)

            addBarokaAttributes(
                model = model,
                sessionId = sessionId,
                username = username,
                barokaList = barokaList,
                title = title,
                localPort = null,
            )

            return "terminal"
        } catch (e: Exception) {
            e.printStackTrace();
            model.addAttribute("error", "SSH connection failed: " + e.message);
            return "index";
        }
    }

    @PostMapping("/tunnel")
    fun createTunnel(@RequestParam("title") title: String,
                     @RequestParam("tunnelHost") tunnelHost: String,
                     @RequestParam("tunnelPort") tunnelPort: Int,
                     @RequestParam("tunnelUsername") tunnelUsername: String,
                     @RequestParam("tunnelPassword") tunnelPassword: String?,
                     @RequestParam("tunnelPemKey") tunnelPemKey: MultipartFile?,
                     @RequestParam("localPort") localPort: Int,
                     @RequestParam("remoteHost") remoteHost: String,
                     @RequestParam("remotePort") remotePort: Int,
                     @RequestParam("destinationUsername") destinationUsername: String,
                     @RequestParam("destinationPassword") destinationPassword: String?,
                     @RequestParam("destinationPemKey") destinationPemKey: MultipartFile?,
                     model: Model) : String {
        try {
            val tunnelSession: Session = connectPemOrPassword(
                username = tunnelUsername,
                host = tunnelHost,
                port = tunnelPort,
                pemPath = tunnelPemKey?.takeIf { !it.isEmpty }?.let { uploadPem(it) },
                password = tunnelPassword)

            setPortForwardingL(
                tunnelSession = tunnelSession,
                localPort = localPort,
                remoteHost = remoteHost,
                remotePort = remotePort)

            val destinationSession = connectJumpPemOrPassword(
                username = destinationUsername,
                localPort = localPort,
                pemPath = destinationPemKey?.takeIf { !it.isEmpty }?.let { uploadPem(it) },
                password = destinationPassword)

            val sessionId = UUID.randomUUID().toString()
            TerminalWebSocketHandler.addSession("Tunnel_$sessionId", tunnelSession)
            TerminalWebSocketHandler.addSession(sessionId, destinationSession)

            barokaService.saveTunneling(
                title = title,
                tunnelHost = tunnelHost,
                tunnelUsername = tunnelUsername,
                tunnelPassword = tunnelPassword,
                tunnelPemPath = tunnelPemKey?.takeIf { !it.isEmpty }?.let { uploadPem(it) },
                tunnelPort = tunnelPort,
                remoteHost = remoteHost,
                remotePort = remotePort,
                destinationUsername = destinationUsername,
                destinationPassword = destinationPassword,
                destinationPemPath = destinationPemKey?.takeIf { !it.isEmpty }?.let { uploadPem(it) },
                localPort = localPort)

            val barokaList = barokaService.getBarokaShell(sessionId, path)
            addBarokaAttributes(
                model = model,
                sessionId = sessionId,
                username = destinationUsername,
                barokaList = barokaList,
                title = title,
                localPort = localPort)
            return "terminal"
        } catch (e: Exception) {
            e.printStackTrace();
            model.addAttribute("error", "SSH connection failed: " + e.message);
            return "index";
        }
    }

    @PostMapping("/tunnelWithPath")
    fun createTunnel(@RequestParam("title") title: String,
                     @RequestParam("tunnelHost") tunnelHost: String,
                     @RequestParam("tunnelPort") tunnelPort: Int,
                     @RequestParam("tunnelUsername") tunnelUsername: String,
                     @RequestParam("tunnelPassword") tunnelPassword: String?,
                     @RequestParam("tunnelPemKeyPath") tunnelPemKeyPath: String?,
                     @RequestParam("localPort") localPort: Int,
                     @RequestParam("remoteHost") remoteHost: String,
                     @RequestParam("remotePort") remotePort: Int,
                     @RequestParam("destinationUsername") destinationUsername: String,
                     @RequestParam("destinationPassword") destinationPassword: String?,
                     @RequestParam("destinationPemKeyPath") destinationPemKeyPath: String?,
                     model: Model) : String {
        try {
            val tunnelSession: Session = connectPemOrPassword(
                username = tunnelUsername,
                host = tunnelHost,
                port = tunnelPort,
                pemPath = tunnelPemKeyPath,
                password = tunnelPassword)

            setPortForwardingL(
                tunnelSession = tunnelSession,
                localPort = localPort,
                remoteHost = remoteHost,
                remotePort = remotePort)

            val destinationSession = connectJumpPemOrPassword(
                username = destinationUsername,
                localPort = localPort,
                pemPath = destinationPemKeyPath,
                password = destinationPassword)

            val sessionId = UUID.randomUUID().toString()
            TerminalWebSocketHandler.addSession("Tunnel_$sessionId", tunnelSession)
            TerminalWebSocketHandler.addSession(sessionId, destinationSession)

            val barokaList = barokaService.getBarokaShell(sessionId, path)
            addBarokaAttributes(
                model = model,
                sessionId = sessionId,
                username = destinationUsername,
                barokaList = barokaList,
                title = title,
                localPort = localPort)
            return "terminal"
        } catch (e: Exception) {
            e.printStackTrace();
            model.addAttribute("error", "SSH connection failed: " + e.message);
            return "index";
        }
    }

    @GetMapping("/file-list")
    fun getFileList(@RequestParam("sessionId") sessionId: String, model: Model) : String {
        val barokaList = barokaService.getBarokaShell(sessionId, path)
        model.addAttribute("barokaFiles", barokaList)
        model.addAttribute("colors", generateRandomColors(barokaList.size))
        return "terminal :: file-list"
    }

    private fun connectPemOrPassword(username: String, host: String, port: Int, pemPath: String?, password: String?) : Session {
        return if(!pemPath.isNullOrEmpty()) {
            barokaService.connectSessionWithPem(
                username = username,
                host = host,
                port = port,
                pemPath = pemPath)
        } else if(!password.isNullOrEmpty()) {
            barokaService.connectSessionWithPassword(
                username = username,
                host = host,
                port = port,
                password = password)
        } else {
            throw InvalidException("Either password or PEM key must be provided.")
        }
    }

    private fun connectJumpPemOrPassword(username: String, localPort: Int, pemPath: String?, password: String?) : Session {
        return if(!pemPath.isNullOrEmpty()) {
            barokaService.connectDestinationWithPem(
                destinationUsername = username,
                localPort = localPort,
                destinationPemPath = pemPath
            )
        } else if(!password.isNullOrEmpty()) {
            barokaService.connectDestinationWithPassword(
                destinationUsername = username,
                localPort = localPort,
                destinationPassword = password
            )
        } else {
            throw InvalidException("Either password or PEM key must be provided.")
        }
    }

    private fun uploadPem(pem: MultipartFile) : String {
        try {
            val uploadDir = "pem-keys/"
            val fileName = pem.getOriginalFilename()
            val filePath = Paths.get(uploadDir + fileName)

            if(!Files.exists(filePath.parent))
                Files.createDirectories(filePath.parent)

            Files.write(filePath, pem.bytes)
            return filePath.toString()
        } catch (e: IOException) {
            e.printStackTrace()
            throw InvalidException("Failed to save the pem key.")
        }
    }

    private fun generateRandomColors(count: Int) : List<String> {
        var colors = mutableListOf<String>()
        val random = Random()
        repeat(count) {
            val R = random.nextInt(2256)
            val G = random.nextInt(2256)
            val B = random.nextInt(2256)
            colors.add(String.format("#%02x%02x%02x", R, G, B))
        }
        return colors
    }

    private fun addBarokaAttributes(model: Model, sessionId: String, username: String, barokaList: List<String>, title: String, localPort: Int?) {
        model.addAttribute("sessionId", sessionId)
        model.addAttribute("username", username)
        model.addAttribute("barokaFiles", barokaList)
        model.addAttribute("colors", generateRandomColors(barokaList.size))
        model.addAttribute("barokaPath", path)
        model.addAttribute("title", title)
        model.addAttribute("localPort", localPort)
    }

    private fun setPortForwardingL(tunnelSession: Session, localPort: Int, remoteHost: String, remotePort: Int) {
        try {
            tunnelSession.setPortForwardingL(localPort, remoteHost, remotePort)
        } catch (e: JSchException) {
            if(e.cause is BindException) tunnelSession.setPortForwardingL(0, remoteHost, remotePort)
            else throw InvalidException(e.message!!)
        }
    }
}