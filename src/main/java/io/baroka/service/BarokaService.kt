package io.baroka.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.jcraft.jsch.Session
import io.baroka.component.SSHConnection
import io.baroka.entity.Host
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.io.File
import java.util.*
import java.util.function.Predicate

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

    fun saveSSH(title: String, username: String, host: String, port: Int, password: String, pemPath: String) {
        var hosts = getHostList().toMutableList()
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
                      tunnelPassword: String,
                      tunnelPemPath: String,
                      tunnelPort: Int,
                      remoteHost: String,
                      remotePort: Int,
                      destinationUsername: String,
                      destinationPassword: String,
                      destinationPemPath: String,
                      localPort: Int) {
        var hosts = getHostList().toMutableList()

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

    fun deleteHost(id: String): Boolean? {
        return try {
            val hosts = getHostList()
            hosts.removeIf(Predicate<Host> { (idx): Host -> idx == id })
            objectMapper.writeValue(file, hosts)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

}