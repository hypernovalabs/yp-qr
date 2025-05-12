package com.example.tefbanesco.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.tefbanesco.intenthandlers.GetCustomParamsHandler
import timber.log.Timber

/**
 * Activity para manejar el intent GET_CUSTOM_PARAMS de HioPos.
 * 
 * Esta Activity cumple con el requisito de la API v3.5 de HioPos de usar
 * setResult() y finish() para devolver resultados a HioPos.
 */
class GetCustomParamsActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("[YAPPY] GetCustomParamsActivity: onCreate")
        
        try {
            // Usar el handler existente para generar el intent con los parámetros personalizados
            val handler = GetCustomParamsHandler(this)
            val paramsIntent = handler.buildCustomParamsIntent()
            
            // Enviar el resultado a HioPos con los parámetros personalizados
            Timber.i("[YAPPY] GetCustomParams: Enviando parámetros")
            setResult(Activity.RESULT_OK, paramsIntent)
        } catch (e: Exception) {
            // Capturar cualquier excepción y reportar fallo
            Timber.e(e, "[YAPPY] Error en GetCustomParams: ${e.message}")
            val errorIntent = Intent()
            errorIntent.putExtra("ErrorMessage", "Error al obtener parámetros personalizados: ${e.message}")
            errorIntent.putExtra("ErrorMessageTitle", "Error de Parámetros")
            setResult(Activity.RESULT_CANCELED, errorIntent)
        } finally {
            // IMPORTANTE: Siempre llamar a finish() para completar el ciclo
            finish()
        }
    }
}