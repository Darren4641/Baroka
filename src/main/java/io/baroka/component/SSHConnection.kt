package io.baroka.component

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import io.baroka.constants.Constants.Companion.KEY_NAME
import io.baroka.constants.Constants.Companion.LOCAL_HOST
import io.baroka.constants.Constants.Companion.NO
import io.baroka.constants.Constants.Companion.STRICT_HOST_KEY_CHECKING
import io.baroka.exception.InvalidException
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties

@Component
class SSHConnection (
    private val jsch: JSch,
) {

    fun createSessionWithPem(username: String, host: String, port: Int, pemPath: String) : Session {
        val pemBytes = Files.readAllBytes(Paths.get(pemPath))
        jsch.addIdentity(KEY_NAME, pemBytes, null, null)
        val session = jsch.getSession(username, host, port)

        val config = Properties()
        config.put(STRICT_HOST_KEY_CHECKING, NO)
        session.setConfig(config)

        session.connect()

        return session
    }

    fun createSessionWithPassword(username: String, host: String, port:Int, password: String) : Session {
        val session = jsch.getSession(username, host, port)
        session.setPassword(password)

        val config = Properties()
        config.put(STRICT_HOST_KEY_CHECKING, NO)
        session.setConfig(config)
        session.connect()

        return session
    }

    fun createTunnelSessionWithPassword(destinationUsername: String, localPort: Int, destinationPassword: String) : Session {
        val session: Session

        try {
            session = jsch.getSession(destinationUsername, LOCAL_HOST, localPort)
            session.setPassword(destinationPassword)
            val config = Properties().apply {
                put(STRICT_HOST_KEY_CHECKING, NO)
            }
            session.setConfig(config)
            session.connect()
        } catch (e: JSchException) {
            if (e.message == "connection is closed by foreign host") {
                throw InvalidException("Previous connection is shutting down. Please try again in a moment.\n Try Again!!")
            } else {
                throw e
            }
        } catch (e: Exception) {
            throw e
        }

        return session
    }

    fun createTunnelSessionWithPem(destinationUsername: String, localPort: Int, destinationPemPath: String) : Session {
        val session : Session

        try {
            val pemBytes = Files.readAllBytes(Paths.get(destinationPemPath))
            jsch.addIdentity(KEY_NAME, pemBytes, null, null)

            session = jsch.getSession(destinationUsername, LOCAL_HOST, localPort)
            val config = Properties()
            config.put(STRICT_HOST_KEY_CHECKING, NO)
            session.setConfig(config)
            session.connect()

        } catch (e: JSchException) {
            if (e.message == "connection is closed by foreign host") {
                throw InvalidException("Previous connection is shutting down. Please try again in a moment.\n Try Again!!")
            } else {
                throw e
            }
        }


        return session
    }
}