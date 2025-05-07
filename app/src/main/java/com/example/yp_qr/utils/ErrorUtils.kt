package com.example.yappy.utils

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorUtils {

    fun getErrorMessageFromException(e: Exception): String {
        return when (e) {
            is SocketTimeoutException -> "Tiempo de espera agotado. El servidor no respondió. Inténtalo nuevamente."
            is UnknownHostException -> "No tienes conexión a internet. Verifica tu red e intenta otra vez."
            is IOException -> "Problema de comunicación. Revisa tu conexión o contacta soporte."
            else -> "Ocurrió un error inesperado. Inténtalo más tarde."
        }
    }
}
