package io.baroka.component

import org.springframework.stereotype.Component

@Component
class ShellComponent (
    val sshConnection: SSHConnection
) {

    fun aa() {
        sshConnection.createSessionWithPassword(host = "aa", username = "aa", port = 1, password = "aa")
    }
}