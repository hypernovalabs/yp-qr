//package com.example.yp_qr
//
//import android.graphics.Bitmap
//import android.graphics.Color
//import android.widget.Toast
//import androidx.compose.animation.AnimatedVisibility
//import androidx.compose.animation.core.animateFloatAsState
//import androidx.compose.animation.expandVertically
//import androidx.compose.animation.shrinkVertically
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.background
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.rememberScrollState
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.foundation.verticalScroll
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.*
//import androidx.compose.material.icons.rounded.Close
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.alpha
//import androidx.compose.ui.draw.rotate
//import androidx.compose.ui.graphics.Brush
//import androidx.compose.ui.graphics.asImageBitmap
//import androidx.compose.ui.graphics.Color as ComposeColor
//import androidx.compose.ui.graphics.vector.ImageVector
//import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.text.style.TextOverflow
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.compose.ui.window.Dialog
//import androidx.compose.ui.window.DialogProperties
//import com.example.yp_qr.utils.tefbanescoStatusCodes
//import com.google.zxing.BarcodeFormat
//import com.google.zxing.MultiFormatWriter
//import com.google.zxing.common.BitMatrix
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import org.json.JSONObject
//
//// Paleta de colores personalizada (mantenida igual)
//val OrangeMain = ComposeColor(0xFFFD933C)
//val OrangeSecondary = ComposeColor(0xFFFD8942)
//val BlueMain = ComposeColor(0xFF0056B3)
//val BlueDark = ComposeColor(0xFF024C95)
//val Celeste = ComposeColor(0xFF29B6F6)
//
///**
// * Pantalla mejorada que muestra el resultado de la generación de un código QR,
// * el estado de la transacción y opciones de acción.
// */
//@Composable
//fun QrResultScreen(
//    date: String,
//    transactionId: String,
//    hash: String
//) {
//    val context = LocalContext.current
//    val scope = rememberCoroutineScope()
//
//    var showCancelDialog by remember { mutableStateOf(false) }
//    var loadingCancel by remember { mutableStateOf(false) }
//    var cancelError by remember { mutableStateOf("") }
//
//    var transactionResponse by remember { mutableStateOf("Cargando información...") }
//    var isCheckingStatus by remember { mutableStateOf(false) }
//    var continuePolling by remember { mutableStateOf(true) }
//    var currentStatus by remember { mutableStateOf(tefbanescoStatusCodes.TransactionStatus.PENDING) }
//    var apiStatusCode by remember { mutableStateOf("00") }
//    var showDetailsDialog by remember { mutableStateOf(false) }
//
//    // Estado para el collapsible de información de transacción
//    var infoExpanded by remember { mutableStateOf(false) }
//
//    // Color e ícono según estado
//    val statusColor = when {
//        currentStatus.equals(tefbanescoStatusCodes.TransactionStatus.COMPLETADA, ignoreCase = true) ->
//            BlueMain
//        currentStatus.equals(tefbanescoStatusCodes.TransactionStatus.CANCELADA, ignoreCase = true) ||
//                currentStatus.equals(tefbanescoStatusCodes.TransactionStatus.FALLIDA, ignoreCase = true) ->
//            OrangeMain
//        currentStatus.equals(tefbanescoStatusCodes.TransactionStatus.PENDING, ignoreCase = true) ->
//            OrangeSecondary
//        else -> ComposeColor.Gray
//    }
//    val statusIcon: ImageVector = when {
//        currentStatus.equals(tefbanescoStatusCodes.TransactionStatus.COMPLETADA, ignoreCase = true) ->
//            Icons.Filled.CheckCircle
//        currentStatus.equals(tefbanescoStatusCodes.TransactionStatus.CANCELADA, ignoreCase = true) ||
//                currentStatus.equals(tefbanescoStatusCodes.TransactionStatus.FALLIDA, ignoreCase = true) ->
//            Icons.Filled.Cancel
//        else -> Icons.Filled.Info
//    }
//
//    // Función de polling
//    suspend fun checkTransactionStatus() {
//        isCheckingStatus = true
//        try {
//            val config = LocalStorage.getConfig(context)
//            val token = LocalStorage.getToken(context) ?: ""
//            val response = ApiService.getTransactionStatus(
//                transactionId, token,
//                config["api_key"] ?: "", config["secret_key"] ?: ""
//            )
//            transactionResponse = response
//
//            try {
//                val json = JSONObject(response)
//                if (json.has("status")) {
//                    apiStatusCode = json.getJSONObject("status").optString("code", "UNKNOWN")
//                }
//                val info = tefbanescoStatusCodes.getInfo(apiStatusCode)
//                if (info.isSuccess && json.has("body")) {
//                    currentStatus = json.getJSONObject("body")
//                        .optString("status", tefbanescoStatusCodes.TransactionStatus.PENDING)
//                } else {
//                    currentStatus = tefbanescoStatusCodes.TransactionStatus.PENDING
//                }
//                continuePolling = !tefbanescoStatusCodes.TransactionStatus.isFinalState(currentStatus)
//            } catch (e: Exception) {
//                e.printStackTrace()
//                currentStatus = tefbanescoStatusCodes.TransactionStatus.PENDING
//                continuePolling = true
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            Toast.makeText(context, "Error de conexión. Reintentando...", Toast.LENGTH_SHORT).show()
//        } finally {
//            isCheckingStatus = false
//        }
//    }
//
//    LaunchedEffect(transactionId) {
//        checkTransactionStatus()
//        while (continuePolling) {
//            delay(3000L)
//            checkTransactionStatus()
//        }
//    }
//
//    val animatedAlpha by animateFloatAsState(
//        targetValue = if (loadingCancel) 0.95f else 1f,
//        label = "backgroundAlpha"
//    )
//
//    Box(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(
//                Brush.verticalGradient(listOf(Celeste, BlueDark), startY = 0f, endY = 1500f)
//            )
//            .alpha(animatedAlpha)
//    ) {
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(16.dp)
//                .verticalScroll(rememberScrollState()),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            HeaderSection()
//            Spacer(modifier = Modifier.height(16.dp))
//
//            // QR más grande
//            QrCodeWithBorder(hash = hash, size = 300, statusColor = statusColor)
//            Spacer(modifier = Modifier.height(20.dp))
//
//            StatusCard(
//                currentStatus, statusColor,
//                isCheckingStatus, statusIcon
//            )
//            Spacer(modifier = Modifier.height(24.dp))
//
//            // Sección desplegable de información
//            CollapsibleTransactionInfo(
//                date = date,
//                transactionId = transactionId,
//                isExpanded = infoExpanded,
//                onToggle = { infoExpanded = !infoExpanded }
//            )
//            Spacer(modifier = Modifier.height(24.dp))
//
//            // Botones en horizontal
//            HorizontalActionsRow(
//                currentStatus = currentStatus,
//                onCancelClick = { showCancelDialog = true },
//                onDetailsClick = { showDetailsDialog = true }
//            )
//        }
//
//        if (showDetailsDialog) {
//            TransactionDetailsDialog(
//                date, transactionId, hash,
//                apiStatusCode, currentStatus, transactionResponse
//            ) { showDetailsDialog = false }
//        }
//
//        if (showCancelDialog) {
//            CancelConfirmationDialog(
//                onDismiss = { showCancelDialog = false },
//                onConfirm = {
//                    showCancelDialog = false
//                    loadingCancel = true
//                    scope.launch {
//                        try {
//                            val config = LocalStorage.getConfig(context)
//                            val token = LocalStorage.getToken(context) ?: ""
//                            continuePolling = false
//                            ApiService.cancelTransaction(
//                                transactionId, token,
//                                config["api_key"] ?: "", config["secret_key"] ?: ""
//                            )
//                            checkTransactionStatus()
//                            Toast.makeText(context, "Transacción cancelada correctamente", Toast.LENGTH_SHORT).show()
//                        } catch (e: Exception) {
//                            e.printStackTrace()
//                            Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
//                            continuePolling = true
//                        } finally {
//                            loadingCancel = false
//                        }
//                    }
//                }
//            )
//        }
//
//        if (loadingCancel) {
//            LoadingOverlay("Cancelando transacción...")
//        }
//    }
//}
//
//@Composable
//fun HeaderSection() {
//    Box(
//        modifier = Modifier
//            .fillMaxWidth()
//            .height(100.dp),
//        contentAlignment = Alignment.Center
//    ) {
//        Image(
//            painter = painterResource(id = R.drawable.yappy_logo),
//            contentDescription = "Logo yappy",
//            modifier = Modifier
//                .fillMaxWidth(0.6f)
//                .fillMaxHeight(),
//            contentScale = ContentScale.Fit
//        )
//    }
//}
//
//@Composable
//fun StatusCard(
//    currentStatus: String,
//    statusColor: ComposeColor,
//    isCheckingStatus: Boolean,
//    statusIcon: ImageVector
//) {
//    Card(
//        modifier = Modifier
//            .fillMaxWidth(0.9f)
//            .padding(vertical = 4.dp),
//        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.15f)),
//        shape = RoundedCornerShape(16.dp),
//        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
//    ) {
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(vertical = 16.dp, horizontal = 20.dp),
//            verticalAlignment = Alignment.CenterVertically,
//            horizontalArrangement = Arrangement.Center
//        ) {
//            if (isCheckingStatus) {
//                CircularProgressIndicator(
//                    modifier = Modifier.size(26.dp),
//                    strokeWidth = 2.5.dp,
//                    color = statusColor
//                )
//                Spacer(modifier = Modifier.width(14.dp))
//            } else {
//                Icon(
//                    imageVector = statusIcon,
//                    contentDescription = null,
//                    tint = statusColor,
//                    modifier = Modifier.size(28.dp)
//                )
//                Spacer(modifier = Modifier.width(14.dp))
//            }
//            val display = when {
//                currentStatus.equals(tefbanescoStatusCodes.TransactionStatus.PENDING, true) ->
//                    "Transacción pendiente"
//                currentStatus.equals(tefbanescoStatusCodes.TransactionStatus.COMPLETADA, true) ->
//                    "Transacción completada"
//                currentStatus.equals(tefbanescoStatusCodes.TransactionStatus.CANCELADA, true) ->
//                    "Transacción cancelada"
//                currentStatus.equals(tefbanescoStatusCodes.TransactionStatus.FALLIDA, true) ->
//                    "Transacción fallida"
//                else ->
//                    "Estado: ${tefbanescoStatusCodes.TransactionStatus.getDescription(currentStatus)}"
//            }
//            Text(
//                text = display,
//                style = MaterialTheme.typography.bodyLarge.copy(
//                    fontWeight = FontWeight.SemiBold,
//                    letterSpacing = 0.4.sp,
//                    fontSize = 16.sp
//                ),
//                color = statusColor
//            )
//        }
//    }
//}
//
//@Composable
//fun QrCodeWithBorder(hash: String, size: Int, statusColor: ComposeColor) {
//    Card(
//        modifier = Modifier
//            .size((size + 40).dp)
//            .padding(4.dp),
//        colors = CardDefaults.cardColors(containerColor = ComposeColor.White),
//        shape = RoundedCornerShape(28.dp),
//        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
//    ) {
//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(16.dp)
//                .background(
//                    brush = Brush.radialGradient(
//                        colors = listOf(statusColor.copy(alpha = 0.08f), ComposeColor.White)
//                    )
//                ),
//            contentAlignment = Alignment.Center
//        ) {
//            QrCode(hash = hash, size = size)
//        }
//    }
//}
//
//@Composable
//fun QrCode(hash: String, size: Int) {
//    val qrBitmap = remember(hash) { generateQRCode(hash, size, size) }
//    if (qrBitmap != null) {
//        Image(
//            bitmap = qrBitmap.asImageBitmap(),
//            contentDescription = "QR Code",
//            modifier = Modifier.size(size.dp)
//        )
//    } else {
//        Card(
//            modifier = Modifier
//                .size(size.dp)
//                .padding(8.dp),
//            colors = CardDefaults.cardColors(containerColor = OrangeMain.copy(alpha = 0.2f))
//        ) {
//            Box(
//                modifier = Modifier.fillMaxSize(),
//                contentAlignment = Alignment.Center
//            ) {
//                Text(
//                    text = "Error generando el código QR",
//                    style = MaterialTheme.typography.bodyMedium,
//                    color = OrangeMain,
//                    textAlign = TextAlign.Center,
//                    modifier = Modifier.padding(8.dp)
//                )
//            }
//        }
//    }
//}
//
///**
// * Componente colapsable para la información de la transacción
// */
//@Composable
//fun CollapsibleTransactionInfo(
//    date: String,
//    transactionId: String,
//    isExpanded: Boolean,
//    onToggle: () -> Unit
//) {
//    Card(
//        modifier = Modifier.fillMaxWidth(0.9f),
//        colors = CardDefaults.cardColors(containerColor = ComposeColor.White),
//        shape = RoundedCornerShape(16.dp),
//        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
//    ) {
//        Column(modifier = Modifier.fillMaxWidth()) {
//            // Cabecera con toggle
//            Row(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .clickable(onClick = onToggle)
//                    .padding(16.dp),
//                verticalAlignment = Alignment.CenterVertically,
//                horizontalArrangement = Arrangement.SpaceBetween
//            ) {
//                Row(verticalAlignment = Alignment.CenterVertically) {
//                    Icon(
//                        imageVector = Icons.Filled.Info,
//                        contentDescription = null,
//                        tint = BlueMain,
//                        modifier = Modifier.size(20.dp)
//                    )
//                    Spacer(modifier = Modifier.width(8.dp))
//                    Text(
//                        text = "Información de la Transacción",
//                        style = MaterialTheme.typography.titleMedium.copy(
//                            fontWeight = FontWeight.Bold,
//                            color = BlueMain
//                        )
//                    )
//                }
//                Icon(
//                    imageVector = Icons.Filled.KeyboardArrowDown,
//                    contentDescription = if (isExpanded) "Contraer" else "Expandir",
//                    tint = BlueDark,
//                    modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
//                )
//            }
//
//            // Contenido colapsable
//            AnimatedVisibility(
//                visible = isExpanded,
//                enter = expandVertically(),
//                exit = shrinkVertically()
//            ) {
//                Column(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(horizontal = 16.dp, vertical = 8.dp),
//                    verticalArrangement = Arrangement.spacedBy(12.dp)
//                ) {
//                    Divider(color = Celeste.copy(alpha = 0.3f))
//                    Row(verticalAlignment = Alignment.CenterVertically) {
//                        Text(
//                            text = "Fecha:",
//                            modifier = Modifier.width(80.dp),
//                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
//                            color = BlueDark.copy(alpha = 0.8f)
//                        )
//                        Text(text = date, style = MaterialTheme.typography.bodyMedium, color = BlueMain)
//                    }
//                    Row(verticalAlignment = Alignment.CenterVertically) {
//                        Text(
//                            text = "ID:",
//                            modifier = Modifier.width(80.dp),
//                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
//                            color = BlueDark.copy(alpha = 0.8f)
//                        )
//                        Text(
//                            text = transactionId,
//                            style = MaterialTheme.typography.bodyMedium,
//                            color = BlueMain,
//                            maxLines = 1,
//                            overflow = TextOverflow.Ellipsis
//                        )
//                    }
//                    Spacer(modifier = Modifier.height(4.dp))
//                }
//            }
//        }
//    }
//}
//
///**
// * Botones en posición horizontal
// */
//@Composable
//fun HorizontalActionsRow(
//    currentStatus: String,
//    onCancelClick: () -> Unit,
//    onDetailsClick: () -> Unit
//) {
//    Row(
//        modifier = Modifier
//            .fillMaxWidth(0.9f)
//            .padding(vertical = 8.dp),
//        horizontalArrangement = Arrangement.SpaceEvenly,
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        if (currentStatus.equals(tefbanescoStatusCodes.TransactionStatus.PENDING, true)) {
//            Button(
//                onClick = onCancelClick,
//                modifier = Modifier
//                    .weight(1f)
//                    .height(50.dp)
//                    .padding(end = 8.dp),
//                colors = ButtonDefaults.buttonColors(
//                    containerColor = OrangeMain,
//                    contentColor = ComposeColor.White
//                ),
//                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
//                shape = RoundedCornerShape(12.dp)
//            ) {
//                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
//                Spacer(modifier = Modifier.width(6.dp))
//                Text(
//                    "Cancelar",
//                    style = MaterialTheme.typography.labelLarge.copy(
//                        fontWeight = FontWeight.Bold,
//                        fontSize = 14.sp
//                    )
//                )
//            }
//
//            Button(
//                onClick = onDetailsClick,
//                modifier = Modifier
//                    .weight(1f)
//                    .height(50.dp)
//                    .padding(start = 8.dp),
//                colors = ButtonDefaults.buttonColors(
//                    containerColor = BlueMain,
//                    contentColor = ComposeColor.White
//                ),
//                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
//                shape = RoundedCornerShape(12.dp)
//            ) {
//                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
//                Spacer(modifier = Modifier.width(6.dp))
//                Text(
//                    "Detalles",
//                    style = MaterialTheme.typography.labelLarge.copy(
//                        fontWeight = FontWeight.Bold,
//                        fontSize = 14.sp
//                    )
//                )
//            }
//        } else {
//            if (currentStatus.equals(tefbanescoStatusCodes.TransactionStatus.CANCELADA, true)) {
//                Text(
//                    "La transacción ha sido cancelada",
//                    style = MaterialTheme.typography.bodyMedium.copy(
//                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
//                    ),
//                    color = OrangeMain,
//                    modifier = Modifier.padding(end = 16.dp)
//                )
//            }
//
//            Button(
//                onClick = onDetailsClick,
//                modifier = Modifier
//                    .widthIn(min = 140.dp)
//                    .height(50.dp),
//                colors = ButtonDefaults.buttonColors(
//                    containerColor = BlueMain,
//                    contentColor = ComposeColor.White
//                ),
//                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
//                shape = RoundedCornerShape(12.dp)
//            ) {
//                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
//                Spacer(modifier = Modifier.width(8.dp))
//                Text(
//                    "Ver Detalles",
//                    style = MaterialTheme.typography.labelLarge.copy(
//                        fontWeight = FontWeight.Bold,
//                        fontSize = 14.sp
//                    )
//                )
//            }
//        }
//    }
//}
//
//// Los demás componentes se mantienen igual
//@Composable
//fun InfoRow(
//    icon: String,
//    label: String,
//    value: String,
//    highlightValue: Boolean = false,
//    maxLines: Int = 1
//) {
//    Row(
//        modifier = Modifier.fillMaxWidth(),
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        Row(
//            modifier = Modifier.width(110.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Text(
//                text = icon,
//                style = MaterialTheme.typography.bodyLarge,
//                modifier = Modifier.padding(end = 4.dp)
//            )
//            Text(
//                text = label,
//                style = MaterialTheme.typography.bodyMedium,
//                fontWeight = FontWeight.Medium,
//                color = BlueDark.copy(alpha = 0.8f)
//            )
//        }
//        Text(
//            text = value,
//            style = MaterialTheme.typography.bodyMedium.copy(
//                fontWeight = if (highlightValue) FontWeight.SemiBold else FontWeight.Normal
//            ),
//            color = if (highlightValue) BlueMain else BlueDark.copy(alpha = 0.7f),
//            modifier = Modifier.fillMaxWidth(),
//            maxLines = maxLines,
//            overflow = TextOverflow.Ellipsis
//        )
//    }
//}
//
//@Composable
//fun TransactionDetailsDialog(
//    date: String,
//    transactionId: String,
//    hash: String,
//    apiStatusCode: String,
//    currentStatus: String,
//    transactionResponse: String,
//    onDismiss: () -> Unit
//) {
//    Dialog(
//        onDismissRequest = onDismiss,
//        properties = DialogProperties(
//            dismissOnBackPress = true,
//            dismissOnClickOutside = true,
//            usePlatformDefaultWidth = false
//        )
//    ) {
//        Card(
//            modifier = Modifier
//                .fillMaxWidth(0.95f)
//                .fillMaxHeight(0.8f),
//            colors = CardDefaults.cardColors(containerColor = ComposeColor.White),
//            shape = RoundedCornerShape(20.dp),
//            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
//        ) {
//            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.SpaceBetween
//                ) {
//                    Text(
//                        "Detalles de la transacción",
//                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
//                        color = BlueMain
//                    )
//                    IconButton(
//                        onClick = onDismiss,
//                        modifier = Modifier.size(36.dp).background(Celeste.copy(alpha = 0.2f), CircleShape)
//                    ) {
//                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = BlueDark)
//                    }
//                }
//                Spacer(modifier = Modifier.height(16.dp))
//                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
//                    Card(
//                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
//                        colors = CardDefaults.cardColors(containerColor = Celeste.copy(alpha = 0.1f)),
//                        shape = RoundedCornerShape(12.dp)
//                    ) {
//                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
//                            InfoRow("📅", "Fecha:", date, highlightValue = true)
//                            InfoRow("🆔", "Transacción:", transactionId, highlightValue = true)
//                            InfoRow("🔐", "Hash:", hash, maxLines = 3)
//                            Spacer(modifier = Modifier.height(4.dp))
//                            Divider(color = Celeste.copy(alpha = 0.3f))
//                            Spacer(modifier = Modifier.height(4.dp))
//                            InfoRow("🔑", "API Code:", apiStatusCode, highlightValue = true)
//                            InfoRow("📊", "Estado:", currentStatus, highlightValue = true)
//                            if (apiStatusCode.isNotEmpty()) {
//                                val info = tefbanescoStatusCodes.getInfo(apiStatusCode)
//                                InfoRow("ℹ️", "Significado:", info.description)
//                            }
//                        }
//                    }
//                    Text(
//                        "Respuesta completa del endpoint:",
//                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
//                        color = BlueMain,
//                        modifier = Modifier.padding(vertical = 8.dp)
//                    )
//                    Card(
//                        modifier = Modifier.fillMaxWidth(),
//                        colors = CardDefaults.cardColors(containerColor = BlueDark.copy(alpha = 0.05f)),
//                        shape = RoundedCornerShape(8.dp)
//                    ) {
//                        val formatted = try {
//                            JSONObject(transactionResponse).toString(4)
//                        } catch (e: Exception) {
//                            transactionResponse
//                        }
//                        Text(
//                            text = formatted,
//                            style = MaterialTheme.typography.bodySmall.copy(
//                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
//                                letterSpacing = 0.2.sp
//                            ),
//                            modifier = Modifier.padding(16.dp)
//                        )
//                    }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun CancelConfirmationDialog(
//    onDismiss: () -> Unit,
//    onConfirm: () -> Unit
//) {
//    AlertDialog(
//        onDismissRequest = onDismiss,
//        title = {
//            Text(
//                "Confirmar Cancelación",
//                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
//                color = BlueMain
//            )
//        },
//        text = {
//            Text(
//                "¿Estás seguro que deseas cancelar esta transacción? Esta acción no se puede deshacer.",
//                style = MaterialTheme.typography.bodyMedium
//            )
//        },
//        confirmButton = {
//            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = OrangeMain)) {
//                Text("Sí, cancelar")
//            }
//        },
//        dismissButton = {
//            OutlinedButton(onClick = onDismiss, colors = ButtonDefaults.outlinedButtonColors(contentColor = BlueMain)) {
//                Text("No, mantener activa")
//            }
//        },
//        containerColor = ComposeColor.White,
//        shape = RoundedCornerShape(16.dp)
//    )
//}
//
//@Composable
//fun LoadingOverlay(message: String) {
//    Box(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(BlueDark.copy(alpha = 0.5f)),
//        contentAlignment = Alignment.Center
//    ) {
//        Card(
//            modifier = Modifier.width(300.dp),
//            colors = CardDefaults.cardColors(containerColor = ComposeColor.White),
//            shape = RoundedCornerShape(20.dp),
//            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
//        ) {
//            Column(
//                modifier = Modifier.padding(28.dp),
//                horizontalAlignment = Alignment.CenterHorizontally,
//                verticalArrangement = Arrangement.spacedBy(20.dp)
//            ) {
//                CircularProgressIndicator(
//                    modifier = Modifier.size(54.dp),
//                    strokeWidth = 4.dp
//                )
//                Text(
//                    text = message,
//                    style = MaterialTheme.typography.bodyLarge.copy(
//                        fontWeight = FontWeight.Medium,
//                        fontSize = 16.sp
//                    ),
//                    textAlign = TextAlign.Center
//                )
//            }
//        }
//    }
//}
//
///**
// * Genera un Bitmap con un QR code a partir de un texto.
// */
//fun generateQRCode(text: String, width: Int, height: Int): Bitmap? {
//    return try {
//        val matrix: BitMatrix = MultiFormatWriter().encode(
//            text, BarcodeFormat.QR_CODE, width, height
//        )
//        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        for (x in 0 until width) {
//            for (y in 0 until height) {
//                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
//            }
//        }
//        bmp
//    } catch (e: Exception) {
//        e.printStackTrace()
//        null
//    }
//}
//---------------------------------
package com.example.yp_qr

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

// Paleta de colores profesional y sobria
val PrimaryColor = ComposeColor(0xFF1E88E5)       // Azul principal
val PrimaryDark = ComposeColor(0xFF1565C0)         // Azul oscuro
val AccentColor = ComposeColor(0xFF42A5F5)         // Celeste de apoyo
val ErrorColor = ComposeColor(0xFFFF7043)          // Naranja para errores
val LightBackground = ComposeColor(0xFFF1F8FF)     // Fondo claro para tarjetas

@Composable
fun QrResultScreen(
    date: String,
    transactionId: String,
    hash: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(AccentColor, PrimaryDark), startY = 0f, endY = 1500f)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeaderSection()
            Spacer(modifier = Modifier.height(32.dp))

            QrCodeWithBorder(hash = hash, size = 340, statusColor = PrimaryColor)
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Escanea este código para realizar tu pago",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = ComposeColor.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
fun HeaderSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.yappy_logo),
            contentDescription = "Logo Yappy",
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .fillMaxHeight(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun QrCodeWithBorder(hash: String, size: Int, statusColor: ComposeColor) {
    Card(
        modifier = Modifier
            .size((size + 40).dp)
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = LightBackground),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(statusColor.copy(alpha = 0.1f), LightBackground)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            QrCode(hash = hash, size = size)
        }
    }
}

@Composable
fun QrCode(hash: String, size: Int) {
    val qrBitmap = remember(hash) { generateQRCode(hash, size, size) }
    if (qrBitmap != null) {
        Image(
            bitmap = qrBitmap.asImageBitmap(),
            contentDescription = "QR Code",
            modifier = Modifier.size(size.dp)
        )
    } else {
        Card(
            modifier = Modifier
                .size(size.dp)
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Error al generar el código",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorColor,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

fun generateQRCode(text: String, width: Int, height: Int): Bitmap? {
    return try {
        val matrix: BitMatrix = MultiFormatWriter().encode(
            text, BarcodeFormat.QR_CODE, width, height
        )
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

//-----------
//package com.example.yp_qr
//
//import android.graphics.Bitmap
//import android.graphics.Color
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.graphics.Brush
//import androidx.compose.ui.graphics.asImageBitmap
//import androidx.compose.ui.graphics.Color as ComposeColor
//import androidx.compose.ui.layout.ContentScale
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import com.google.zxing.BarcodeFormat
//import com.google.zxing.MultiFormatWriter
//import com.google.zxing.common.BitMatrix
//
//val PrimaryColor = ComposeColor(0xFFF8FAFC)
//val PrimaryDark = ComposeColor(0xFF024C95)
//val AccentColor = ComposeColor(0xFF29B6F6)
//val ErrorColor = ComposeColor(0xFFFF7043)
//val LightBackground = ComposeColor(0xFF0056B3)
//val Orange = ComposeColor(0xFFFD933C)
//
//@Composable
//fun QrResultScreen(
//    date: String,
//    transactionId: String,
//    hash: String
//) {
//    Box(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(LightBackground)
//            .padding(24.dp)
//    ) {
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(16.dp),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            HeaderSection()
//            Spacer(modifier = Modifier.height(20.dp))
//
//            QrCodeWithBorder(hash = hash, size = 320)
//
//            Spacer(modifier = Modifier.height(24.dp))
//
//            Text(
//                text = "¡Paga fácil escaneando con Yappy!",
//                fontSize = 20.sp,
//                fontWeight = FontWeight.Bold,
//                color = PrimaryColor,
//                textAlign = TextAlign.Center,
//                modifier = Modifier.padding(horizontal = 32.dp)
//            )
//
//            Spacer(modifier = Modifier.height(8.dp))
//
//            Text(
//                text = "Sucursal N1 - Caja 3",
//                fontSize = 14.sp,
//                fontWeight = FontWeight.Medium,
//                color = PrimaryDark.copy(alpha = 0.7f),
//                textAlign = TextAlign.Center
//            )
//        }
//
//        // Esquinas decorativas
//        Box(
//            modifier = Modifier
//                .align(Alignment.TopStart)
//                .size(48.dp)
//                .background(PrimaryColor.copy(alpha = 0.2f), shape = CircleShape)
//        )
//        Box(
//            modifier = Modifier
//                .align(Alignment.BottomEnd)
//                .size(56.dp)
//                .background(Orange.copy(alpha = 0.25f), shape = CircleShape)
//        )
//    }
//}
//
//@Composable
//fun HeaderSection() {
//    Box(
//        modifier = Modifier
//            .fillMaxWidth()
//            .height(100.dp),
//        contentAlignment = Alignment.Center
//    ) {
//        Image(
//            painter = painterResource(id = R.drawable.yappy_logo),
//            contentDescription = "Logo Yappy",
//            modifier = Modifier
//                .fillMaxWidth(0.45f)
//                .aspectRatio(2f),
//            contentScale = ContentScale.Fit
//        )
//    }
//}
//
//@Composable
//fun QrCodeWithBorder(hash: String, size: Int) {
//    Card(
//        modifier = Modifier
//            .size((size + 40).dp),
//        colors = CardDefaults.cardColors(containerColor = ComposeColor.White),
//        shape = RoundedCornerShape(20.dp),
//        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
//    ) {
//        Box(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(16.dp),
//            contentAlignment = Alignment.Center
//        ) {
//            QrCode(hash = hash, size = size)
//        }
//    }
//}
//
//@Composable
//fun QrCode(hash: String, size: Int) {
//    val qrBitmap = remember(hash) { generateQRCode(hash, size, size) }
//    if (qrBitmap != null) {
//        Image(
//            bitmap = qrBitmap.asImageBitmap(),
//            contentDescription = "QR Code",
//            modifier = Modifier.size(size.dp)
//        )
//    } else {
//        Card(
//            modifier = Modifier
//                .size(size.dp)
//                .padding(8.dp),
//            colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(alpha = 0.1f))
//        ) {
//            Box(
//                modifier = Modifier.fillMaxSize(),
//                contentAlignment = Alignment.Center
//            ) {
//                Text(
//                    text = "Error al generar el código",
//                    style = MaterialTheme.typography.bodyMedium,
//                    color = ErrorColor,
//                    modifier = Modifier.padding(8.dp)
//                )
//            }
//        }
//    }
//}
//
//fun generateQRCode(text: String, width: Int, height: Int): Bitmap? {
//    return try {
//        val matrix: BitMatrix = MultiFormatWriter().encode(
//            text, BarcodeFormat.QR_CODE, width, height
//        )
//        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        for (x in 0 until width) {
//            for (y in 0 until height) {
//                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
//            }
//        }
//        bmp
//    } catch (e: Exception) {
//        e.printStackTrace()
//        null
//    }
//}
