package io.baroka.exception.payload

import groovy.transform.builder.Builder
import java.io.Serializable

@Builder
data class ExceptionMsg (
    val msg: String,
    val code: Int,
    val success: Boolean,
    val errors: List<FieldErrorDetail>
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }

    constructor(msg: String, code: Int, success: Boolean) : this(msg, code, success, emptyList())

}

data class FieldErrorDetail(
    var field: String,
    var messag: String
)