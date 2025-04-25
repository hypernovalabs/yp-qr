package com.example.yp_qr.intenthandlers

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.yp_qr.ConfigDialog
import com.example.yp_qr.ui.theme.YpqrTheme

class ShowSetupHandler(private val activity: Activity) {

    fun handle() {
        if (activity !is ComponentActivity) {
            return fail("La actividad no es compatible con Jetpack Compose.")
        }

        // Renderiza la UI de configuración usando Compose
        activity.setContent {
            YpqrTheme {
                ConfigDialog(onDismiss = {
                    finishOk() // Finaliza con éxito cuando el usuario cierra el diálogo
                })
            }
        }
    }

    private fun finishOk() {
        val result = Intent("icg.actions.electronicpayment.tefbanesco.SHOW_SETUP_SCREEN")
        activity.setResult(Activity.RESULT_OK, result)
        activity.finish()
    }

    private fun fail(message: String) {
        val result = Intent("icg.actions.electronicpayment.tefbanesco.SHOW_SETUP_SCREEN")
        result.putExtra("ErrorMessage", message)
        activity.setResult(Activity.RESULT_CANCELED, result)
        activity.finish()
    }
}
