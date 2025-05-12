package com.example.tefbanesco.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.tefbanesco.intenthandlers.GetVersionHandler
import timber.log.Timber

/**
 * Activity para manejar el intent GET_VERSION de HioPos.
 * 
 * Esta Activity cumple con el requisito de la API v3.5 de HioPos de usar
 * setResult() y finish() para devolver resultados a HioPos.
 */
class GetVersionActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("[YAPPY] GetVersionActivity: onCreate")
        
        try {
            // Usar el handler existente para generar el intent con la versión
            val handler = GetVersionHandler(this)
            val versionIntent = handler.buildVersionIntent()
            
            // Enviar el resultado a HioPos con la versión del módulo
            Timber.i("[YAPPY] GetVersion: Enviando versión")
            setResult(Activity.RESULT_OK, versionIntent)
        } catch (e: Exception) {
            // Capturar cualquier excepción y reportar fallo
            Timber.e(e, "[YAPPY] Error en GetVersion: ${e.message}")
            val errorIntent = Intent()
            errorIntent.putExtra("ErrorMessage", "Error al obtener versión: ${e.message}")
            errorIntent.putExtra("ErrorMessageTitle", "Error de Versión")
            setResult(Activity.RESULT_CANCELED, errorIntent)
        } finally {
            // IMPORTANTE: Siempre llamar a finish() para completar el ciclo
            finish()
        }
    }
}