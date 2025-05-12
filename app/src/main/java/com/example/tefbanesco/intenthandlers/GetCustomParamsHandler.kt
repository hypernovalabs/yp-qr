package com.example.tefbanesco.intenthandlers

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import timber.log.Timber


class GetCustomParamsHandler(private val activity: Activity) {

    /**
     * Maneja el intent GET_CUSTOM_PARAMS directamente (para uso con BroadcastReceiver)
     * Esta función se mantiene por compatibilidad, pero se recomienda usar buildCustomParamsIntent()
     * con GetCustomParamsActivity.
     */
    fun handle() {
        val result = buildCustomParamsIntent()
        activity.setResult(Activity.RESULT_OK, result)
        activity.finish()
    }

    /**
     * Construye el Intent con los parámetros personalizados del módulo para HioPos.
     * Este método es utilizado por GetCustomParamsActivity.
     *
     * @return Intent con el nombre y logo del módulo Yappy
     */
    fun buildCustomParamsIntent(): Intent {
        return Intent().apply {
            // Nombre del módulo para mostrar en HioPos
            putExtra("Name", "YAPPY-QR")

            try {
                // Intentar cargar el logo con varios posibles nombres de recursos
                val logoBytes = getLogoBase64("yappy_logo") ?:
                               getLogoBase64("ic_launcher") ?:
                               getLogoBase64("mipmap/ic_launcher")

                if (logoBytes != null) {
                    putExtra("Logo", logoBytes)
                    Timber.i("[YAPPY] Logo cargado para HioPos (${logoBytes.size} bytes)")
                } else {
                    Timber.w("[YAPPY] No se pudo cargar ningún logo para HioPos")
                }
            } catch (e: Exception) {
                Timber.e(e, "[YAPPY] Error cargando logo para HioPos")
                // Continuamos sin logo si hay algún error
            }

            // Se podría añadir más parámetros personalizados si HioPos los soporta en el futuro
        }
    }

    private fun getLogoBase64(resourceName: String): ByteArray? {
        return try {
            val resId = activity.resources.getIdentifier(resourceName, "drawable", activity.packageName)
            if (resId == 0) {
                Timber.w("[YAPPY] Recurso no encontrado: $resourceName")
                return null
            }

            val inputStream = activity.resources.openRawResource(resId)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            if (bitmap == null) {
                Timber.w("[YAPPY] No se pudo decodificar la imagen: $resourceName")
                return null
            }

            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            Timber.d("[YAPPY] Logo procesado: ${outputStream.size()} bytes")
            outputStream.toByteArray()
        } catch (e: Exception) {
            Timber.e(e, "[YAPPY] Error procesando logo: $resourceName")
            null
        }
    }
}
