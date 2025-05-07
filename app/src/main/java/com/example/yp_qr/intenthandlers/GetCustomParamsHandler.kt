package com.example.yappy.intenthandlers

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64


class GetCustomParamsHandler(private val activity: Activity) {

    fun handle() {
        val result = Intent("icg.actions.electronicpayment.yappy.GET_CUSTOM_PARAMS").apply {
            putExtra("Name", "YAPPY-QR")

            // Cargar logo desde recursos (res/drawable) y convertirlo a Base64
            val logoBytes = getLogoBase64("yappy_logo.png") // sin extensión
            if (logoBytes != null) {
                putExtra("Logo", logoBytes)
            }
        }

        activity.setResult(Activity.RESULT_OK, result)
        activity.finish()
    }

    private fun getLogoBase64(resourceName: String): ByteArray? {
        return try {
            val resId = activity.resources.getIdentifier(resourceName, "drawable", activity.packageName)
            val inputStream = activity.resources.openRawResource(resId)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
