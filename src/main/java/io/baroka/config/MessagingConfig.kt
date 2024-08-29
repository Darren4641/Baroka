package io.baroka.config

import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties(prefix = "messaging")
data class MessagingConfig (
    var encryptionEnabled: Boolean = true,
    var ackTime: Long = 60000L,
    var ackLimitCount: Int = 3,
    var brokerRoute: List<String> = emptyList()

)