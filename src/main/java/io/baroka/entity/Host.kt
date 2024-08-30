package io.baroka.entity

import groovy.transform.builder.Builder


data class Host(
    val idx: String,
    val title: String,
    val username: String,
    val serverHost: String,
    val serverPort: Int,
    val password: String,
    val pem: String,
    val tunnelUsername: String? = null,
    val tunnelHost: String? = null,
    val tunnelPort: Int? = null,
    val localPort: Int? = null,
    var tunnelPassword: String? = null,
    val tunnelPem: String? = null
){

}