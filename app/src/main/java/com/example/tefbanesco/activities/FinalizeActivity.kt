package com.example.tefbanesco.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.tefbanesco.intenthandlers.FinalizeHandler
import timber.log.Timber
//import
/**
 * Activity para manejar el intent FINALIZE de HioPos.
 * 
 * Esta Activity cumple con el requisito de la API v3.5 de HioPos de usar
 * setResult() y finish() para devolver resultados a HioPos.
 */
class FinalizeActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("[YAPPY] FinalizeActivity: onCreate")
        
        try {
            // Usar el handler existente para liberar recursos
            val handler = FinalizeHandler(this)
            val success = handler.finalizeResources()
            
            // Enviar el resultado a HioPos
            if (success) {
                Timber.i("[YAPPY] Finalize: Recursos liberados correctamente")
                setResult(Activity.RESULT_OK)
            } else {
                Timber.w("[YAPPY] Finalize: Error al liberar recursos")
                val errorIntent = Intent()
                errorIntent.putExtra("ErrorMessage", "Error al liberar recursos")
                errorIntent.putExtra("ErrorMessageTitle", "Error de Finalización")
                setResult(Activity.RESULT_CANCELED, errorIntent)
            }
        } catch (e: Exception) {
            // Capturar cualquier excepción y reportar fallo
            Timber.e(e, "[YAPPY] Error en Finalize: ${e.message}")
            val errorIntent = Intent()
            errorIntent.putExtra("ErrorMessage", "Error al finalizar: ${e.message}")
            errorIntent.putExtra("ErrorMessageTitle", "Error de Finalización")
            setResult(Activity.RESULT_CANCELED, errorIntent)
        } finally {
            // IMPORTANTE: Siempre llamar a finish() para completar el ciclo
            finish()
        }
    }
}