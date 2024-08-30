package io.baroka.exception.handler

import io.baroka.exception.payload.ExceptionMsg
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler
    fun <T : RuntimeException> InvalidErrorHandling(error: T) : ResponseEntity<ExceptionMsg> {
        error.printStackTrace()
            log.error("[Error] {}", error.message)
        return ResponseEntity(
            ExceptionMsg(
                msg = error.message ?: "Unknown error",
                code = -1,
                success = false
            ),
            HttpStatus.BAD_REQUEST
        )
    }
}