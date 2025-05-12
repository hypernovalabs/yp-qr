package com.example.tefbanesco.intenthandlers

import android.app.Activity
import android.content.Intent

class GetBehaviorHandler(private val activity: Activity) {

    /**
     * Maneja el intent GET_BEHAVIOR directamente (para uso con BroadcastReceiver)
     * Esta función se mantiene por compatibilidad, pero se recomienda usar buildBehaviorIntent()
     * con GetBehaviorActivity.
     */
    fun handle() {
        val result = buildBehaviorIntent()
        activity.setResult(Activity.RESULT_OK, result)
        activity.finish()
    }

    /**
     * Construye el Intent con el comportamiento del módulo para HioPos.
     * Este método es utilizado por GetBehaviorActivity.
     *
     * @return Intent con las capacidades del módulo Yappy
     */
    fun buildBehaviorIntent(): Intent {
        return Intent().apply {
            // Capacidades del módulo Yappy
            putExtra("SupportsTransactionVoid", false)
            putExtra("SupportsTransactionQuery", false)
            putExtra("SupportsNegativeSales", false)
            putExtra("SupportsPartialRefund", false)
            putExtra("SupportsBatchClose", false)
            putExtra("SupportsTipAdjustment", false)
            putExtra("OnlyCreditForTipAdjustment", true)
            putExtra("SupportsCredit", true)
            putExtra("SupportsDebit", true)
            putExtra("SupportsEBTFoodstamp", false)
            putExtra("HasCustomParams", true) // Para mostrar nombre/logo personalizado

            // API v3.5 - Soporte para DocumentPath
            putExtra("OnlyUseDocumentPath", false) // Cambiar a true si implementas la lectura de DocumentPath
        }
    }
}
