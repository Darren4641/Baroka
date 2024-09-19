package io.baroka.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import io.baroka.component.SSHConnection
import io.baroka.entity.Host
import io.baroka.exception.InvalidException
import io.baroka.handler.TerminalWebSocketHandler
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*
import kotlin.collections.ArrayList

@Service
class BarokaService (
    private val sshConnection: SSHConnection,
){
    private val file= File("data/hosts.json")
    private val objectMapper = ObjectMapper()

    @PostConstruct
    fun init() {
        if(!file.exists()) {
           val parentDir = file.parentFile
            if(parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            file.createNewFile()
            objectMapper.writeValue(file, ArrayList<Host>())
        }
    }

    fun connectSessionWithPem(username: String, host: String, port: Int, pemPath: String) : Session =
        sshConnection.createSessionWithPem(
            username = username,
            host = host,
            port = port,
            pemPath = pemPath)

    fun connectSessionWithPassword(username: String, host: String, port: Int, password: String) : Session =
        sshConnection.createSessionWithPassword(
            username = username,
            host = host,
            port = port,
            password = password)

    fun saveSSH(title: String, username: String, host: String, port: Int, password: String?, pemPath: String?) {
        val hosts = getHostList().toMutableList()
        hosts.add(
            Host(
                idx = UUID.randomUUID().toString(),
                title = title,
                username = username,
                serverHost = host,
                serverPort = port,
                password = password,
                pem = pemPath,
            )
        )
        objectMapper.writeValue(file, hosts)
    }

    fun connectDestinationWithPassword(destinationUsername: String, localPort: Int, destinationPassword: String) : Session =
        sshConnection.createTunnelSessionWithPassword(
            destinationUsername = destinationUsername,
            localPort = localPort,
            destinationPassword = destinationPassword)

    fun connectDestinationWithPem(destinationUsername: String, localPort: Int, destinationPemPath: String) : Session =
        sshConnection.createTunnelSessionWithPem(
            destinationUsername = destinationUsername,
            localPort = localPort,
            destinationPemPath = destinationPemPath)

    fun saveTunneling(title: String,
                      tunnelHost: String,
                      tunnelUsername: String,
                      tunnelPassword: String?,
                      tunnelPemPath: String?,
                      tunnelPort: Int,
                      remoteHost: String,
                      remotePort: Int,
                      destinationUsername: String,
                      destinationPassword: String?,
                      destinationPemPath: String?,
                      localPort: Int) {
        val hosts = getHostList().toMutableList()

        hosts.add(
            Host(
            idx = UUID.randomUUID().toString(),
            title = title,
            username = destinationUsername,
            serverHost = remoteHost,
            serverPort = remotePort,
            password = destinationPassword,
            pem = destinationPemPath,
            tunnelHost = tunnelHost,
            tunnelUsername = tunnelUsername,
            tunnelPassword = tunnelPassword,
            tunnelPort = tunnelPort,
            tunnelPem = tunnelPemPath,
            localPort = localPort
        ))
        objectMapper.writeValue(file, hosts)
    }
    fun getHostList() : List<Host> {
        return if(!file.exists()) {
            ArrayList<Host>()
        } else {
            objectMapper.readValue(file, object : TypeReference<List<Host>>() {})
        }
    }

    fun deleteHost(id: String): Boolean {
        return try {
            val hosts = getHostList().toMutableList()
            hosts.removeIf { host -> host.idx == id }
            objectMapper.writeValue(file, hosts)
            true
        } catch(e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getBarokaShell(sessionId: String, path: String) : List<String> {
        val session = TerminalWebSocketHandler.getSession(sessionId)

        session?.let {
            executeCommand(session, "mkdir -p " + path)

            val shellFiles = executeCommand(session, "sh -c 'find " + path + " -type f -name \"*.sh\" -perm /111'")

            return shellFiles.map{ shell  ->
                val splitShell = shell.split("/")
                splitShell[splitShell.size - 1]
            }
        }

        throw InvalidException("Not found ssh session")
    }

    private fun  executeCommand(session: Session, command: String) : List<String> {
        val channel : ChannelExec = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)

        val inputStream : InputStream = channel.inputStream
        channel.connect()

        var reader = BufferedReader(InputStreamReader(inputStream))
        val lines = ArrayList<String>().toMutableList()
        var line: String?
        while(reader.readLine().also { line = it } != null) {
            lines.add(line!!)
        }

        channel.disconnect()
        return lines
    }

}