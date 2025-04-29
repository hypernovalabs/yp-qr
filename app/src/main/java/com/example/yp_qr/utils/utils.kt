package com.example.yp_qr.utils

/**
 * Clase para representar la información de un código de estado de tefbanesco.
 *
 * @param code Código de estado (formato YP-XXXX)
 * @param name Nombre identificativo del código (según documentación)
 * @param description Descripción detallada del significado del código
 * @param isSuccess Indica si el código representa una operación exitosa
 * @param category Categoría a la que pertenece el código (puede haber solapamiento)
 */
data class StatusCodeInfo(
    val code: String,
    val name: String, // Nombre exacto según la imagen/documentación
    val description: String,
    val isSuccess: Boolean = false,
    val category: String
)

/**
 * Clase que contiene todos los códigos de estado de la API tefbanesco
 * y funciones útiles para su gestión, basados en la documentación visual proporcionada.
 */
object tefbanescoStatusCodes {
    // Lista completa de todos los códigos según la imagen de referencia.
    // NOTA: La estructura Map usa el 'code' como clave única. Códigos como YP-0000 y YP-0200
    // aparecen en múltiples categorías en la imagen, pero aquí solo se puede almacenar una entrada.
    // La función getInfo devolverá la primera definición encontrada (generalmente la de Sesión).
    val CODES = mapOf(
        // --- Servicios de sesión ---
        "YP-0000" to StatusCodeInfo(
            code = "YP-0000",
            name = "SUCCESS", // También listado para Transacciones y QRs en la imagen
            description = "Se ha realizado la ejecución del servicio correctamente",
            isSuccess = true,
            category = "Servicios de sesión"
        ),
        "YP-0006" to StatusCodeInfo(
            code = "YP-0006",
            name = "FAIL_OPEN",
            description = "Error al Abrir la caja",
            category = "Servicios de sesión"
        ),
        "YP-0200" to StatusCodeInfo(
            code = "YP-0200",
            name = "FAIL_VALIDATE_COMMERCE", // También listado para Transacciones en la imagen
            description = "Error, ha ocurrido un error en procesar los datos. Contacte al administrador",
            category = "Servicios de sesión"
        ),
        "YP-0401" to StatusCodeInfo(
            code = "YP-0401",
            name = "FAIL_DATA_ERROR", // Nombre idéntico a YP-0010 pero en diferente categoría
            description = "Error al Abrir la caja", // Descripción diferente a YP-0010
            category = "Servicios de sesión"
        ),
        "YP-0008" to StatusCodeInfo(
            code = "YP-0008",
            name = "FAIL_MISSED_HEADERS",
            description = "Error, cabeceras obligatorias faltantes en la peticion",
            category = "Servicios de sesión"
        ),
        "YP-0009" to StatusCodeInfo(
            code = "YP-0009",
            name = "FAIL_MISSED_DATA",
            description = "Error, uno o mas campos obligatorios faltantes en el cuerpo de la peticion",
            category = "Servicios de sesión"
        ),
        "YP-0021" to StatusCodeInfo(
            code = "YP-0021",
            name = "FAIL_OPEN_GENERAL_ERROR",
            description = "Error en data: Hay un conflicto de datos",
            category = "Servicios de sesión"
        ),

        // --- Servicios de Transacciones ---
        // YP-0000 y YP-0200 también aplican aquí según la imagen, pero el Map ya los tiene bajo Sesión.

        "YP-0014" to StatusCodeInfo(
            code = "YP-0014",
            name = "FAIL_SETTLEMENT_TRANSACTION", // CORREGIDO: Settlement en lugar de Setlement
            description = "Error, la reversa no puede ser procesada porque ya se liquido.",
            category = "Servicios de Transacciones"
        ),
        "YP-0016" to StatusCodeInfo(
            code = "YP-0016",
            name = "FAIL_ALREADY_REVERSED_TRANSACTION",
            description = "La transaccion que se intenta reversar posee un estado reversado",
            category = "Servicios de Transacciones"
        ),
        "YP-0013" to StatusCodeInfo(
            code = "YP-0013",
            name = "FAIL_REVERSE_ERROR",
            description = "Error, ha ocurrido un error en procesar los datos. Contacte al administrador",
            category = "Servicios de Transacciones"
        ),

        // --- Servicios de QRs ---
        // YP-0000 también aplica aquí según la imagen, pero el Map ya lo tiene bajo Sesión.

        "YP-0010" to StatusCodeInfo(
            code = "YP-0010",
            name = "FAIL_DATA_ERROR", // Nombre idéntico a YP-0401 pero en diferente categoría
            description = "Error, uno o mas campos del cuerpo de la peticion no cumplen con los valores enumerados", // Descripción diferente a YP-0401
            category = "Servicios de QRs"
        ),
        "YP-0405" to StatusCodeInfo(
            code = "YP-0405",
            name = "AMOUNT_NOT_MATCH",
            description = "El desglose de la transaccion no coincide con el total",
            category = "Servicios de QRs"
        ),

        // --- Errores Generales de Plataforma (Comunes) ---
        "YP-0002" to StatusCodeInfo(
            code = "YP-0002",
            name = "FAIL_ERROR",
            description = "Error, ha ocurrido un error en procesar los datos. Contacte al administrador",
            category = "Errores Generales de Plataforma"
        ),
        "YP-9999" to StatusCodeInfo(
            code = "YP-9999",
            name = "TIMEOUT_ERROR",
            description = "Error, ha ocurrido un error en procesar los datos. Contacte al administrador",
            category = "Errores Generales de Plataforma"
        )
    )

    /**
     * Obtiene la información detallada de un código de estado.
     * Debido a la estructura Map, si un código existe en múltiples categorías (ej. YP-0000),
     * devolverá la primera definición encontrada.
     * Si el código no existe, devuelve información por defecto.
     *
     * @param code Código a buscar (formato YP-XXXX)
     * @return StatusCodeInfo con la información del código encontrado o uno por defecto.
     */
    fun getInfo(code: String): StatusCodeInfo {
        return CODES[code] ?: StatusCodeInfo(
            code = code,
            name = "UNKNOWN",
            description = "Código de estado desconocido: $code",
            category = "Desconocido"
        )
    }

    /**
     * Indica si un código representa una operación exitosa (basado en la definición encontrada).
     *
     * @param code Código a verificar
     * @return true si el código indica éxito, false en caso contrario
     */
    fun isSuccess(code: String): Boolean {
        // Devuelve true solo si isSuccess está explícitamente marcado como true en la definición encontrada.
        return CODES[code]?.isSuccess ?: false // Más seguro devolver false si no se encuentra o no está definido
    }

    /**
     * Constantes y utilidades relacionadas con los *estados del ciclo de vida* de una transacción.
     * Estos no son los códigos de respuesta de la API mostrados en la imagen.
     */
    object TransactionStatus {
        const val PENDING = "PENDING"
        const val COMPLETADA = "COMPLETADA" // Corresponde a un estado final exitoso
        const val CANCELADA = "CANCELADA" // Corresponde a un estado final no exitoso
        const val FALLIDA = "FALLIDA"     // Corresponde a un estado final no exitoso
        const val REVERSADA = "REVERSADA" // Corresponde a un estado final no exitoso (anulación)
        const val LIQUIDADA = "LIQUIDADA" // Corresponde a un estado final procesado (post-completada)
        const val DESCONOCIDO = "DESCONOCIDO"

        /**
         * Verifica si un estado de transacción es considerado un estado final
         * (es decir, la transacción no cambiará más de estado).
         *
         * @param status Estado a verificar
         * @return true si es un estado final, false en caso contrario
         */
        fun isFinalState(status: String): Boolean {
            // Se compara ignorando mayúsculas/minúsculas para robustez
            return status.equals(COMPLETADA, ignoreCase = true) ||
                    status.equals(CANCELADA, ignoreCase = true) ||
                    status.equals(FALLIDA, ignoreCase = true) ||
                    status.equals(REVERSADA, ignoreCase = true) ||
                    status.equals(LIQUIDADA, ignoreCase = true)
        }

        /**
         * Obtiene una descripción amigable de un estado de transacción para mostrar al usuario.
         *
         * @param status Estado a describir
         * @return Descripción amigable del estado
         */
        fun getDescription(status: String): String {
            // Se usa uppercase() para asegurar la comparación correcta en el when
            return when (status.uppercase()) {
                PENDING -> "En proceso"
                COMPLETADA -> "Completada"
                CANCELADA -> "Cancelada"
                FALLIDA -> "Fallida"
                REVERSADA -> "Reversada"
                LIQUIDADA -> "Liquidada"
                // Se incluye DESCONOCIDO explícitamente para claridad
                DESCONOCIDO -> "Estado desconocido"
                else -> "Estado desconocido ($status)" // Devuelve el estado original si no coincide
            }
        }
    }
}