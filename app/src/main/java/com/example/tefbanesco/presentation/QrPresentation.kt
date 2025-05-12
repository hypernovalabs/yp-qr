package com.example.tefbanesco.presentation

import android.app.Presentation
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlin.math.min

// --- Helpers para encontrar el LifecycleOwner real de un ContextWrapper ---
private tailrec fun Context.findLifecycleOwner(): androidx.lifecycle.LifecycleOwner? = when {
    this is androidx.lifecycle.LifecycleOwner -> this
    this is ContextWrapper                   -> baseContext.findLifecycleOwner()
    else                                     -> null
}

// --- Elige el Display con menor área (o el único) ---
private fun chooseSmallestDisplay(context: Context): Display {
    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val displays = dm.displays
    if (displays.isEmpty()) {
        // Caeremos al display por defecto
        return context.display ?: throw IllegalStateException("No hay pantallas disponibles")
    }
    return displays.minByOrNull { d ->
        val metrics = DisplayMetrics()
        d.getMetrics(metrics)
        (metrics.widthPixels * metrics.heightPixels).toLong()
    }!!
}

// 1) Presentation que se auto-configura para el mejor display
class QrPresentation(
    context: Context,
    private val hash: String
) : Presentation(context, chooseSmallestDisplay(context)) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Encontramos el LifecycleOwner de quien llamó esta Presentation
        val lifecycleOwner = context.findLifecycleOwner()

        val composeView = ComposeView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            setContent {
                // Proveemos explícitamente el LifecycleOwner a Compose
                lifecycleOwner?.let { owner ->
                    CompositionLocalProvider(
                        LocalLifecycleOwner provides owner
                    ) {
                        MaterialTheme {
                            QrFullScreen(hash)
                        }
                    }
                } ?: run {
                    // Fallback si no lo encontramos
                    MaterialTheme {
                        QrFullScreen(hash)
                    }
                }
            }
        }

        setContentView(composeView)
    }
}

// 2) Composable full-screen que pinta sólo el código QR
@Composable
fun QrFullScreen(hash: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(ComposeColor.Black),
        contentAlignment = Alignment.Center
    ) {
        val bmp = remember(hash) { generateQRCode(hash, 800, 800) }
        bmp?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "QR",
                modifier = Modifier.size(600.dp)
            )
        }
    }
}

// 3) Generador de Bitmap QR usando ZXing
fun generateQRCode(text: String, width: Int, height: Int): Bitmap? = try {
    val matrix: BitMatrix =
        MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height)
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
        for (x in 0 until width) for (y in 0 until height) {
            val color = if (matrix.get(x, y)) android.graphics.Color.BLACK
            else android.graphics.Color.WHITE
            bmp.setPixel(x, y, color)
        }
    }
} catch (e: Exception) {
    null
}
