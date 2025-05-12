package com.example.tefbanesco.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.tefbanesco.intenthandlers.GetBehaviorHandler
import timber.log.Timber

/**
 * Activity para manejar el intent GET_BEHAVIOR de HioPos.
 * 
 * Esta Activity cumple con el requisito de la API v3.5 de HioPos de usar
 * setResult() y finish() para devolver resultados a HioPos.
 */
class GetBehaviorActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("[YAPPY] GetBehaviorActivity: onCreate")

        try {
            // Usar el handler existente para generar el intent de comportamiento
            val handler = GetBehaviorHandler(this)
            val behaviorIntent = handler.buildBehaviorIntent()

            // Guardar el valor de OnlyUseDocumentPath para que TransactionHandler lo use
            saveBehaviorSettings(behaviorIntent)

            // Enviar el resultado a HioPos con las capacidades del módulo
            Timber.i("[YAPPY] GetBehavior: Enviando comportamiento")
            setResult(Activity.RESULT_OK, behaviorIntent)
        } catch (e: Exception) {
            // Capturar cualquier excepción y reportar fallo
            Timber.e(e, "[YAPPY] Error en GetBehavior")
            val errorIntent = Intent()
            errorIntent.putExtra("ErrorMessage", "Error al obtener comportamiento: ${e.message}")
            errorIntent.putExtra("ErrorMessageTitle", "Error de Comportamiento")
            setResult(Activity.RESULT_CANCELED, errorIntent)
        } finally {
            // IMPORTANTE: Siempre llamar a finish() para completar el ciclo
            finish()
        }
    }

    /**
     * Guarda la configuración de comportamiento para que otros componentes puedan acceder a ella.
     * Es importante persistir estos valores ya que la Activity se destruye después de finish().
     */
    private fun saveBehaviorSettings(behaviorIntent: Intent) {
        try {
            val extras = behaviorIntent.extras ?: return

            // Guardar en SharedPreferences para que TransactionHandler pueda acceder
            val prefs = getSharedPreferences("HioPosBehaviorPrefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()

            // Guardar OnlyUseDocumentPath
            val onlyUseDocumentPath = extras.getBoolean("OnlyUseDocumentPath", false)
            editor.putBoolean("OnlyUseDocumentPath", onlyUseDocumentPath)

            // Guardar otras capacidades relevantes para usar en otros lugares
            extras.keySet().forEach { key ->
                when (val value = extras.get(key)) {
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    // Añadir otros tipos según sea necesario
                }
            }

            editor.apply()

            Timber.d("[YAPPY] Configuración de comportamiento guardada. OnlyUseDocumentPath: $onlyUseDocumentPath")
        } catch (e: Exception) {
            Timber.e(e, "[YAPPY] Error al guardar configuración de comportamiento")
        }
    }
}