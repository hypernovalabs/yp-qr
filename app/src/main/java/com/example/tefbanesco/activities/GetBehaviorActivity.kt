package com.example.tefbanesco.activities

import android.app.Activity
import android.content.Context // Asegúrate de que esta importación esté presente
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
 * El Intent de resultado DEBE incluir la acción original.
 */
class GetBehaviorActivity : ComponentActivity() {

    companion object {
        // Define la acción específica para evitar errores de tipeo y facilitar el mantenimiento
        // Reemplaza <apk_name> con el nombre real de tu paquete como está definido para HioPos.
        const val ACTION_GET_BEHAVIOR = "icg.actions.electronicpayment.tefbanesco.GET_BEHAVIOR"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("[YAPPY] GetBehaviorActivity: onCreate. Intent action: ${intent.action}")

        // Verificar que la acción del intent sea la esperada (opcional pero buena práctica)
        if (intent.action != ACTION_GET_BEHAVIOR) {
            Timber.e("[YAPPY] GetBehaviorActivity: Acción de intent incorrecta: ${intent.action}")
            val errorIntent = Intent(ACTION_GET_BEHAVIOR) // Siempre incluir la acción esperada
            errorIntent.putExtra("ErrorMessage", "Acción de intent no válida para GetBehaviorActivity")
            errorIntent.putExtra("ErrorMessageTitle", "Error Interno del Módulo")
            setResult(Activity.RESULT_CANCELED, errorIntent)
            finish()
            return
        }

        try {
            // Usar el handler existente para generar el intent con los datos de comportamiento
            val handler = GetBehaviorHandler(this)
            val behaviorDataIntent = handler.buildBehaviorIntent() // Este intent solo contiene los extras

            // Crear el intent de resultado CON LA ACCIÓN CORRECTA
            val resultIntent = Intent(ACTION_GET_BEHAVIOR)
            // Copiar todos los extras del behaviorDataIntent al resultIntent
            if (behaviorDataIntent.extras != null) {
                resultIntent.putExtras(behaviorDataIntent.extras!!)
            }

            // Guardar la configuración de comportamiento relevante para uso futuro
            // (como OnlyUseDocumentPath)
            saveBehaviorSettings(resultIntent) // Pasamos el resultIntent que ya tiene los extras

            // Enviar el resultado a HioPos con las capacidades del módulo
            Timber.i("[YAPPY] GetBehavior: Enviando comportamiento: ${resultIntent.extras?.keySet()}")
            setResult(Activity.RESULT_OK, resultIntent)

        } catch (e: Exception) {
            // Capturar cualquier excepción y reportar fallo
            Timber.e(e, "[YAPPY] Error en GetBehavior")
            val errorIntent = Intent(ACTION_GET_BEHAVIOR) // Siempre incluir la acción esperada
            errorIntent.putExtra("ErrorMessage", "Error al obtener comportamiento: ${e.message}")
            errorIntent.putExtra("ErrorMessageTitle", "Error de Comportamiento")
            setResult(Activity.RESULT_CANCELED, errorIntent)
        } finally {
            // IMPORTANTE: Siempre llamar a finish() para completar el ciclo
            finish()
        }
    }

    /**
     * Guarda la configuración de comportamiento (extras del intent de resultado)
     * para que otros componentes puedan acceder a ella.
     * Es importante persistir estos valores ya que la Activity se destruye después de finish().
     */
    private fun saveBehaviorSettings(intentWithBehaviorExtras: Intent) {
        try {
            val extras = intentWithBehaviorExtras.extras ?: run {
                Timber.w("[YAPPY] No hay extras en behaviorIntent para guardar en SharedPreferences.")
                return
            }

            // Guardar en SharedPreferences para que TransactionHandler pueda acceder
            val prefs = this.getSharedPreferences("HioPosBehaviorPrefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()

            // Limpiar preferencias anteriores para evitar valores obsoletos (opcional pero recomendado)
            // editor.clear()

            Timber.d("[YAPPY] Guardando configuraciones de comportamiento en SharedPreferences:")
            extras.keySet().forEach { key ->
                when (val value = extras.get(key)) {
                    is Boolean -> {
                        editor.putBoolean(key, value)
                        Timber.d("[YAPPY] -> $key : $value (Boolean)")
                    }
                    is String -> {
                        editor.putString(key, value)
                        Timber.d("[YAPPY] -> $key : $value (String)")
                    }
                    is Int -> {
                        editor.putInt(key, value)
                        Timber.d("[YAPPY] -> $key : $value (Int)")
                    }
                    // Añadir otros tipos según sea necesario (Long, Float, etc.)
                    else -> Timber.w("[YAPPY] Tipo no manejado para la clave '$key' en SharedPreferences: ${value?.javaClass?.name}")
                }
            }

            editor.apply()

            val onlyUseDocumentPath = prefs.getBoolean("OnlyUseDocumentPath", false) // Leer de nuevo para confirmar
            Timber.i("[YAPPY] Configuración de comportamiento guardada. OnlyUseDocumentPath ahora es: $onlyUseDocumentPath")

        } catch (e: Exception) {
            Timber.e(e, "[YAPPY] Error al guardar configuración de comportamiento")
        }
    }
}