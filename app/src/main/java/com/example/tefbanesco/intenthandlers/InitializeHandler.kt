package com.example.tefbanesco.intenthandlers

import android.app.Activity
import android.content.Intent
import com.example.tefbanesco.storage.LocalStorage
import com.example.tefbanesco.errors.ErrorHandler // ✅ Importamos ErrorHandler
import kotlinx.coroutines.runBlocking
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber

class InitializeHandler(private val activity: Activity) {

    /**
     * Maneja el intent INITIALIZE directamente (para uso con BroadcastReceiver)
     * Esta función se mantiene por compatibilidad, pero se recomienda usar processParameters()
     * con InitializeActivity.
     */
    fun handle() {
        val parametersXml = activity.intent.getStringExtra("Parameters")

        if (parametersXml.isNullOrBlank()) {
            // No finalizar si no vienen parámetros válidos
            ErrorHandler.showConfigurationError(activity) {
                val result = Intent("icg.actions.electronicpayment.tefbanesco.INITIALIZE")
                result.putExtra("ErrorMessage", "No se recibieron parámetros")
                activity.setResult(Activity.RESULT_CANCELED, result)
                // NO hacemos finish aquí
            }
            return
        }

        try {
            val configMap = parseXml(parametersXml)
            runBlocking {
                LocalStorage.saveConfig(
                    context = activity,
                    endpoint = configMap["endpoint"] ?: "",
                    apiKey = configMap["api_key"] ?: "",
                    secretKey = configMap["secret_key"] ?: "",
                    deviceId = configMap["device_id"] ?: "",
                    deviceName = configMap["device_name"] ?: "",
                    deviceUser = configMap["device_user"] ?: "",
                    groupId = configMap["group_id"] ?: ""
                )
            }

            val result = Intent("icg.actions.electronicpayment.tefbanesco.INITIALIZE")
            activity.setResult(Activity.RESULT_OK, result)
            activity.finish() // ✅ Ahora sí cerramos solo si todo salió bien

        } catch (e: Exception) {
            fail("Error al parsear configuración: ${e.message}")
        }
    }

    /**
     * Procesa los parámetros de inicialización recibidos de HioPos.
     * Este método es utilizado por InitializeActivity.
     *
     * @return true si los parámetros se procesaron correctamente, false en caso contrario
     */
    fun processParameters(): Boolean {
        val parametersXml = activity.intent.getStringExtra("Parameters")

        // Verificar si hay parámetros
        if (parametersXml.isNullOrBlank()) {
            Timber.w("[YAPPY] No se recibieron parámetros de inicialización")
            return false
        }

        return try {
            // Parsear XML y guardar configuración
            val configMap = parseXml(parametersXml)
            Timber.d("[YAPPY] Parámetros recibidos: ${configMap.keys}")

            runBlocking {
                LocalStorage.saveConfig(
                    context = activity,
                    endpoint = configMap["endpoint"] ?: "",
                    apiKey = configMap["api_key"] ?: "",
                    secretKey = configMap["secret_key"] ?: "",
                    deviceId = configMap["device_id"] ?: "",
                    deviceName = configMap["device_name"] ?: "",
                    deviceUser = configMap["device_user"] ?: "",
                    groupId = configMap["group_id"] ?: ""
                )
            }

            Timber.i("[YAPPY] Parámetros procesados y guardados correctamente")
            true
        } catch (e: Exception) {
            Timber.e(e, "[YAPPY] Error procesando parámetros de inicialización")
            false
        }
    }

    private fun fail(message: String) {
        ErrorHandler.showConfigurationError(activity) {
            val result = Intent("icg.actions.electronicpayment.tefbanesco.INITIALIZE")
            result.putExtra("ErrorMessage", message)
            activity.setResult(Activity.RESULT_CANCELED, result)
            activity.finish()
        }
    }

    private fun parseXml(xml: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())

        var key: String? = null
        var value: String? = null

        var eventType = parser.eventType
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    if (parser.name == "Param") {
                        key = parser.getAttributeValue(null, "Key")
                    }
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> {
                    value = parser.text
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    if (parser.name == "Param" && key != null && value != null) {
                        result[key] = value
                        key = null
                        value = null
                    }
                }
            }
            eventType = parser.next()
        }
        return result
    }
}
