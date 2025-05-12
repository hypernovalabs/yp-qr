package com.example.tefbanesco.intenthandlers

import android.app.Activity
import android.content.Intent
import timber.log.Timber

class FinalizeHandler(private val activity: Activity) {

    fun handle() {
        val result = Intent("icg.actions.electronicpayment.tefbanesco.FINALIZE")
        activity.setResult(Activity.RESULT_OK, result)
        activity.finish()
    }

    /**
     * Libera los recursos utilizados por el módulo Yappy.
     * @return true si los recursos se liberaron correctamente, false en caso contrario
     */
    fun finalizeResources(): Boolean {
        return try {
            // Código para liberar recursos:
            // 1. Cerrar sesiones de Yappy si hay alguna activa
            // 2. Limpiar token temporal si está almacenado
            // 3. Liberar cualquier otra referencia o recurso

            // Por ejemplo, limpiar el token de dispositivo guardado:
            val context = activity.applicationContext
            com.example.tefbanesco.storage.LocalStorage.saveToken(context, "")

            true // Retornamos true para indicar éxito
        } catch (e: Exception) {
            Timber.e(e, "[YAPPY] Error liberando recursos en finalizeResources()")
            false // Retornamos false en caso de error
        }
    }
}
