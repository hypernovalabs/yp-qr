package com.example.tefbanesco.intenthandlers

import android.app.Activity
import android.content.Intent

class GetVersionHandler(private val activity: Activity) {

    /**
     * Maneja el intent GET_VERSION directamente (para uso con BroadcastReceiver)
     * Esta función se mantiene por compatibilidad, pero se recomienda usar buildVersionIntent()
     * con GetVersionActivity.
     */
    fun handle() {
        val result = buildVersionIntent()
        activity.setResult(Activity.RESULT_OK, result)
        activity.finish()
    }

    /**
     * Construye el Intent con la versión del módulo para HioPos.
     * Este método es utilizado por GetVersionActivity.
     *
     * @return Intent con la versión del módulo Yappy
     */
    fun buildVersionIntent(): Intent {
        return Intent().apply {
            // La versión actual del módulo Yappy
            putExtra("Version", 3) // 🛠️ Ajustar según la versión real del módulo
        }
    }
}
