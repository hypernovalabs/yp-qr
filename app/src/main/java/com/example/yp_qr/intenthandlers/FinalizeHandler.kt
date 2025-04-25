package com.example.yp_qr.intenthandlers

import android.app.Activity
import android.content.Intent

class FinalizeHandler(private val activity: Activity) {

    fun handle() {
        // Aquí podrías liberar recursos o cerrar conexiones si tuvieras alguna
        // En este ejemplo, simplemente devolvemos éxito

        val result = Intent("icg.actions.electronicpayment.tefbanesco.FINALIZE")
        activity.setResult(Activity.RESULT_OK, result)
        activity.finish()
    }
}
