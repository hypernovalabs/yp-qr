package com.example.tefbanesco.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class TefReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("TefReceiver", "🔔 Intent recibido: $action")

        when (action) {
            "icg.actions.electronicpayment.tefbanesco.GET_VERSION" -> {
                // Aquí devuelves o procesas la versión
                Log.d("TefReceiver", "🛠️ Acción: GET_VERSION")
                // Puedes responder con un broadcast, notificación, o guardar estado
            }
            "icg.actions.electronicpayment.tefbanesco.SHOW_SETUP_SCREEN" -> {
                Log.d("TefReceiver", "⚙️ Mostrar configuración (puedes lanzar actividad si quieres)")
                // Si en algún caso quieres lanzar la interfaz desde aquí:
                // val i = Intent(context, MainActivity::class.java)
                // i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                // context.startActivity(i)
            }
            "icg.actions.electronicpayment.tefbanesco.FINALIZE" -> {
                Log.d("TefReceiver", "🔚 Acción: FINALIZE")
                // Cierra sesión o limpia datos, si es necesario
            }
            else -> {
                Log.w("TefReceiver", "❓ Acción no reconocida")
            }
        }

        // Opcional: Mostrar notificación o toast
        Toast.makeText(context, "Intent recibido: $action", Toast.LENGTH_SHORT).show()
    }
}
