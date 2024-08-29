package io.baroka.entity

import groovy.transform.builder.Builder

@Builder
class Host (
    val idx: String,
    val title: String,
    val username: String,
    val serverHost: String,
    val serverPort: Int,
    val password: String,
    val pem: String,
    val tunnelUsername: String,
    val tunnelHost: String,
    val tunnelPort: Int,
    val localPort: Int,
    val tunnelPassword: String,
    val tunnelPem: String

){

}