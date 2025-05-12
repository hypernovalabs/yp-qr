package com.example.tefbanesco.errors

import android.app.Activity
import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import com.example.tefbanesco.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object ErrorHandler {

    fun showErrorDialog(
        activity: Activity,
        title: String,
        message: String,
        positiveButtonText: String = "OK",
        negativeButtonText: String? = null,
        onPositiveClick: (() -> Unit)? = null,
        onNegativeClick: (() -> Unit)? = null
    ) {
        activity.runOnUiThread {
            val themedContext: Context = ContextThemeWrapper(activity, R.style.AppTheme)

            val builder = MaterialAlertDialogBuilder(themedContext)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(positiveButtonText) { dialog, _ ->
                    dialog.dismiss()
                    onPositiveClick?.invoke()
                }

            if (negativeButtonText != null) {
                builder.setNegativeButton(negativeButtonText) { dialog, _ ->
                    dialog.dismiss()
                    onNegativeClick?.invoke()
                }
            }

            builder.show()
        }
    }

    fun showConfigurationError(activity: Activity, onDismiss: (() -> Unit)? = null) {
        showErrorDialog(
            activity = activity,
            title = "Error de Configuración",
            message = "El módulo no está configurado correctamente. Por favor configure primero las credenciales.",
            positiveButtonText = "OK",
            onPositiveClick = { onDismiss?.invoke() }
        )
    }

    fun showNetworkError(
        activity: Activity,
        message: String,
        onDismiss: (() -> Unit)? = null,
        onRetry: (() -> Unit)? = null
    ) {
        showErrorDialog(
            activity = activity,
            title = "Error de Red",
            message = message,
            positiveButtonText = if (onRetry != null) "Reintentar" else "OK",
            negativeButtonText = if (onRetry != null) "Cancelar" else null,
            onPositiveClick = { onRetry?.invoke() ?: onDismiss?.invoke() },
            onNegativeClick = { onDismiss?.invoke() }
        )
    }
}
