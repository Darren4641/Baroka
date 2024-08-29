package io.baroka.model

import groovy.transform.builder.Builder
import java.io.Serializable

@Builder
data class Message<T> (
    val session: String,
    val messageType: MessageType,
    val remoteDir: String,
    val localPort: Int,
    val data: T
) : Serializable {
    //constructor(session: String, messageType: MessageType, data: T) : this(session, messageType, "", 0,data)
}

enum class MessageType {
    ACK,
    ENTER,
    COMMAND,
    RESULT,
    AUTOCOMPLETE,
    SIGNAL,
    PATH,
    EXIT,
    VI_OPERATION,
    VI,
    VI_CONTENT
}

@Builder
data class VI (
    val operation: String,
    val title: String,
    val content: String,
    val remoteDir: String,
    val isBaroka: Boolean
)
