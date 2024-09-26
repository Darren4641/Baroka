package io.baroka.entity



class Host(
    val idx: String? = null,
    val title: String? = null,
    val username: String? = null,
    val serverHost: String? = null,
    val serverPort: Int? = null,
    val password: String? = null,
    val pem: String? = null,
    val tunnelUsername: String? = null,
    val tunnelHost: String? = null,
    val tunnelPort: Int? = null,
    val localPort: Int? = null,
    var tunnelPassword: String? = null,
    val tunnelPem: String? = null
){
}