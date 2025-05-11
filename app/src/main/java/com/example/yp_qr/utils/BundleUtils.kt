// Archivo: com/example/tefbanesco/utils/BundleUtils.kt
// O com/example/yappy/utils/BundleUtils.kt

package com.example.tefbanesco.utils // O el paquete de tu proyecto

import android.os.Bundle

/**
 * Devuelve una representación en String de todos los pares clave=valor de este Bundle,
 * en el formato Bundle[key1=val1, key2=val2, …].
 * Si el Bundle es null, devuelve "Bundle[null]".
 */
fun Bundle?.toReadableString(): String {
    if (this == null) return "Bundle[null]"
    if (keySet().isEmpty()) return "Bundle[empty]"

    return keySet().joinToString(prefix = "Bundle[", postfix = "]") { key ->
        "$key=${get(key)}"
    }
}