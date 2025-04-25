package com.example.yp_qr.intenthandlers

import android.app.Activity
import android.content.Intent

class GetVersionHandler(private val activity: Activity) {

    fun handle() {
        val result = Intent("icg.actions.electronicpayment.tefbanesco.GET_VERSION").apply {
            putExtra("Version", 3) // 🛠️ Ajusta aquí la versión de tu módulo
        }

        activity.setResult(Activity.RESULT_OK, result)
        activity.finish()
    }
}
