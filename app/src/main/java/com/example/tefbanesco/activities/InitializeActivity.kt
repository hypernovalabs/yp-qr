package com.example.tefbanesco.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.tefbanesco.intenthandlers.InitializeHandler
import timber.log.Timber

/**
 * Activity para manejar el intent INITIALIZE de HioPos.
 * 
 * Esta Activity cumple con el requisito de la API v3.5 de HioPos de usar
 * setResult() y finish() para devolver resultados a HioPos.
 */
class InitializeActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("[YAPPY] InitializeActivity: onCreate")
        
        // Procesar el intent usando la lógica existente en InitializeHandler
        val handler = InitializeHandler(this)
        
        try {
            // Intentar procesar parámetros
            val success = handler.processParameters()
            
            if (success) {
                // Informar éxito a HioPos
                Timber.i("[YAPPY] Initialize: Éxito")
                setResult(Activity.RESULT_OK)
            } else {
                // Informar fallo a HioPos
                Timber.w("[YAPPY] Initialize: Fallo")
                val errorIntent = Intent()
                errorIntent.putExtra("ErrorMessage", "No se pudieron procesar los parámetros de inicialización")
                errorIntent.putExtra("ErrorMessageTitle", "Error de Inicialización")
                setResult(Activity.RESULT_CANCELED, errorIntent)
            }
        } catch (e: Exception) {
            // Capturar cualquier excepción y reportar fallo
            Timber.e(e, "[YAPPY] Error en Initialize")
            val errorIntent = Intent()
            errorIntent.putExtra("ErrorMessage", "Error: ${e.message}")
            errorIntent.putExtra("ErrorMessageTitle", "Error de Inicialización")
            setResult(Activity.RESULT_CANCELED, errorIntent)
        } finally {
            // IMPORTANTE: Siempre llamar a finish() para completar el ciclo
            finish()
        }
    }
}