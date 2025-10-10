package com.rafael_valladares.ticket_for_bluetooth

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.URISyntaxException
import java.net.Socket as JavaSocket
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import java.nio.charset.Charset
private const val CHAR_WIDTH = 7      // ancho aprox por carácter en CPCL
private const val MARGIN_RIGHT = 30   // margen derecho

data class TicketProduct(
    val name: String,
    val quantity: Int,
    val totalUnit: Double
)

class BluetoothPrinterService : Service() {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val CHANNEL_ID = "bluetooth_printer_channel"
    private var btSocket: BluetoothSocket? = null        // 👈 Bluetooth impresora
private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    private var socketRed: Socket? = null
    private val SOCKET_URL = "wss://si-ham-api.erpseedcodeone.online/socket"
private var ioSocket: Socket? = null                 // 👈 Socket.IO

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("PrinterService", "🟢 onCreate iniciado")

        // ✅ Mantener CPU despierto
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BluetoothPrinter::Lock")
        wakeLock?.acquire()

        // ✅ Crear canal de notificación
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Bluetooth Printer Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Servicio persistente para impresión Bluetooth"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        // ✅ Notificación permanente
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Servicio de impresión activo")
            .setContentText("Bluetooth Printer funcionando en segundo plano")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        // ✅ Iniciar foreground service correctamente según versión
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(1, notification)
        }

        // ✅ Iniciar tus procesos de red y Bluetooth
        serviceScope.launch {
            try {
                Log.d("PrinterService", "⚡ Iniciando conexión Socket.IO y Bluetooth...")
                startSockets()
            } catch (e: Exception) {
                Log.e("PrinterService", "❌ Error iniciando sockets: ${e.message}")
            }
        }
    }


 
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // tu lógica actual
        return START_STICKY
    }


 private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Bluetooth Printer",
            NotificationManager.IMPORTANCE_DEFAULT // 👈 cambia de LOW a DEFAULT
        )
        channel.description = "Canal usado por el servicio de impresión Bluetooth"
        channel.setSound(null, null) // opcional: silencioso
        channel.enableVibration(false)
        channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Impresión Bluetooth En Proceso...")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // ícono BT
            .setOnlyAlertOnce(true)
            .setOngoing(true)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
             .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
    .setSilent(true)
            .build()
    }
override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)
    val restartService = Intent(applicationContext, BluetoothPrinterService::class.java)
    val restartPendingIntent = PendingIntent.getService(
        this, 1, restartService,
        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
    )
    val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarm.set(
        AlarmManager.ELAPSED_REALTIME_WAKEUP,
        SystemClock.elapsedRealtime() + 1000,
        restartPendingIntent
    )
    Log.d("PrinterService", "♻️ Servicio reprogramado al cerrar la app")
}


    private fun printTicketInBackground() {
        try {

              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("PrinterService", "🚫 Falta permiso BLUETOOTH_CONNECT")
            return
        }
    }
            val btAdapter = BluetoothAdapter.getDefaultAdapter()
            val device: BluetoothDevice =
                btAdapter.bondedDevices.firstOrNull { it.address == "66:32:64:9A:65:3F" }
                    ?: throw Exception("Impresora no emparejada")

            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            socket = device.createRfcommSocketToServiceRecord(uuid)

            socket?.connect()
            outputStream = socket?.outputStream

            // 👇 Ticket igual al de tu módulo
            val PAGE_WIDTH = 415
            val LEFT_X = 85
            var y = 25

            val empresa = "MI EMPRESA S.A. DE C.V."
            val dte = "00000000000000000000"
            val caja = "00000"
            val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val numControl = "000000000000000"

            val productos = listOf(
                Triple("Galletas super extra largas con nombre enorme que no cabe", "1", "1.00"),
                Triple("Coca Cola 1.5L", "2", "2.50"),
                Triple("Pan francés", "5", "2.25")
            )

            val PRODUCT_X = LEFT_X
            val QTY_X = 370
            val TOTAL_X = 455
            val LINE_H = 28
            val MAX_PROD_CHARS = 24

            val body = StringBuilder()
            body.append("TEXT 7 0 $LEFT_X $y $empresa\n")
            y += 40
            body.append("LINE 20 $y ${PAGE_WIDTH - 20} $y 2\n")
            y += 20
            body.append("TEXT 7 0 $LEFT_X $y DTE: $dte\n"); y += LINE_H
            body.append("TEXT 7 0 $LEFT_X $y Caja: $caja\n"); y += LINE_H
            body.append("TEXT 7 0 $LEFT_X $y Fecha: $fecha\n"); y += LINE_H
            body.append("TEXT 7 0 $LEFT_X $y Num Control DTE: $numControl\n"); y += (LINE_H + 4)

            body.append("LINE 20 $y ${PAGE_WIDTH - 20} $y 2\n")
            y += 12
            body.append("TEXT 7 0 $PRODUCT_X $y Producto\n")
            body.append("TEXT 7 0 $QTY_X $y Cant\n")
            body.append("TEXT 7 0 $TOTAL_X $y Total\n")
            y += 18
            body.append("LINE 20 $y ${PAGE_WIDTH - 20} $y 2\n")
            y += 10

            for ((nombre, qty, total) in productos) {
                val prodLines = wrapColumnCPCL(nombre, MAX_PROD_CHARS)
                prodLines.forEachIndexed { idx, line ->
                    body.append("TEXT 7 0 $PRODUCT_X ${y + idx * LINE_H} $line\n")
                }
                body.append("TEXT 7 0 $QTY_X $y $qty\n")
                body.append("TEXT 7 0 $TOTAL_X $y $total\n")
                y += (prodLines.size * LINE_H) + 8
            }

            body.append("LINE 20 $y ${PAGE_WIDTH - 20} $y 2\n")
            y += 16
            body.append("TEXT 7 0 $TOTAL_X $y 5.75\n")
            y += 60

            val pageHeight = max(800, y)
            val cpclCmd = buildString {
                append("! 0 200 200 $pageHeight 1\n")
                append("PAGE-WIDTH $PAGE_WIDTH\n")
                append(body.toString())
                append("PRINT\n")
            }

            outputStream?.write(cpclCmd.toByteArray(Charsets.UTF_8))
            outputStream?.flush()

            Log.d("BluetoothService", "✅ Ticket impreso en Foreground Service")

        } catch (e: Exception) {
            Log.e("BluetoothService", "Error: ${e.message}", e)
        }
    }
override fun onDestroy() {
    super.onDestroy()
    try { outputStream?.close() } catch (_: Exception) {}
    try { btSocket?.close() } catch (_: Exception) {}
            try { wakeLock?.release() } catch (_: Exception) {}
    try { ioSocket?.disconnect() } catch (_: Exception) {}

    socketRed?.disconnect()
        socketRed?.close()
}

    private fun wrapColumnCPCL(text: String, maxChars: Int): List<String> {
        val out = mutableListOf<String>()
        val words = text.split(" ")
        var line = ""
        for (w in words) {
            val candidate = if (line.isEmpty()) w else "$line $w"
            if (candidate.length > maxChars) {
                if (line.isNotEmpty()) out.add(line)
                line = w
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) out.add(line)
        return out
    }



 private fun startSockets() {
        try {
            val opts = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 2000
                transports = arrayOf("websocket")
                query = "transmitterId=52"
            }

            ioSocket = IO.socket("wss://facturacion-testmt-api.erpseedcodeone.online/sales-gateway", opts)

            ioSocket?.on(Socket.EVENT_CONNECT) {
                Log.d("PrinterService", "✅ Socket conectado")
            }

            ioSocket?.on("response-print-by-bluetooth") { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                Log.d("PrinterService", "📩 Ticket recibido: $data")
                serviceScope.launch {
                    printTicketOnce(data.toString())
                }
            }

            ioSocket?.on(Socket.EVENT_DISCONNECT) {
                Log.w("PrinterService", "⚠️ Socket desconectado")
            }

            ioSocket?.connect()
        } catch (e: URISyntaxException) {
            Log.e("PrinterService", "URI error", e)
        }
    }
private suspend fun closeSafe() {
    try { outputStream?.flush() } catch (_: Exception) {}
    try { outputStream?.close() } catch (_: Exception) {}
    try { socket?.close() } catch (_: Exception) {}
    outputStream = null
    socket = null
    delay(100) // 🔹 pequeño retardo para liberar el canal Bluetooth
}


private fun printTicketInBackgroundSocket(payload: String) {
     serviceScope.launch {
        try {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("PrinterService", "🚫 Falta permiso BLUETOOTH_CONNECT")
            return@launch
        }
    }
                Log.d("PrinterService", "entro la peticion")

                 closeSafe()
                Log.d("PrinterService", "limpio las cosas")

            try {
                val btAdapter = BluetoothAdapter.getDefaultAdapter()
                // if (btAdapter == null || !btAdapter.isEnabled) {
                //     promise.reject("BLUETOOTH_DISABLED", "Bluetooth no está activo")
                //     return@Thread
                // }
                                Log.d("PrinterService", "paso ascar")

                val LINE_MARGIN = 5

                val device: BluetoothDevice =
                    btAdapter.bondedDevices.firstOrNull { it.address == "66:32:64:9A:65:3F" }
                        ?: throw Exception("Impresora no emparejada")

                val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                socket = device.createInsecureRfcommSocketToServiceRecord(uuid) // inseguro
                delay(200)
                Log.d("PrinterService", "connections blout")

socket?.connect()
                Log.d("PrinterService", "coneccion con el bluetooth")

                outputStream = socket?.outputStream
                Log.d("PrinterService", "y esto ")

                // ---------- CONFIGURACIÓN ----------
                val PAGE_WIDTH = 515
                val LEFT_X = 85
                var y = 25

                val empresa = "MI EMPRESA S.A. DE C.V."
                val dte = "00000000000000000000"
                val ambiente = "01"
                val caja = "00000"
                val fecha = "2025-10-03"
                val numControl = "DTE-01-M001P001-000000000000004"
                val codGen = "F020C26C-819F-4677-B388-4BB5C676D134"

                val productos = listOf(
                    Triple("Galletas super extra largas con nombre enorme que no cabe", "1", "1.00"),
                    Triple("Coca Cola 1.5L", "2", "2.50"),
                    Triple("Pan frances", "5", "2.25"),
                    Triple("Pan frances", "5", "2.25"),
                )

                val PRODUCT_X = LEFT_X
                val QTY_X = 370
                val TOTAL_X = 455
                val LINE_H = 28
                val MAX_PROD_CHARS = 24
                val RIGHT_X = PAGE_WIDTH - LEFT_X
val AJUSTE = 2
val MAX_PROD_CHARS_REAL = MAX_PROD_CHARS - AJUSTE
val ESPACIO_ENTRE_PRODUCTOS = 16
val BORDER_MARGIN_LEFT = 80     // empieza más adentro (antes 30)
val BORDER_MARGIN_RIGHT = -250 
                val body = StringBuilder()

                // Encabezado
                body.append("TEXT 7 0 $LEFT_X $y $empresa\n")
                y += 40
                // body.append("LINE 20 $y ${PAGE_WIDTH} $y 2\n")
                body.append("LINE $BORDER_MARGIN_LEFT $y ${PAGE_WIDTH - BORDER_MARGIN_RIGHT} $y 2\n")

                y += 20

                // Datos fiscales
                body.append("TEXT 7 0 $LEFT_X $y DTE: $dte\n"); y += LINE_H
                body.append("TEXT 7 0 $LEFT_X $y Caja: $caja\n"); y += LINE_H
                body.append("TEXT 7 0 $LEFT_X $y Fecha: $fecha\n"); y += LINE_H
                body.append("TEXT 7 0 $LEFT_X $y Num Control DTE: $numControl\n"); y += (LINE_H + 4)

body.append("LINE $BORDER_MARGIN_LEFT $y ${PAGE_WIDTH - BORDER_MARGIN_RIGHT} $y 2\n")
                // body.append("LINE 20 $y ${PAGE_WIDTH - 20} $y 2\n")
                y += 20

                // Tabla encabezado
                body.append("TEXT 7 0 $PRODUCT_X $y Producto\n")
                body.append("TEXT 7 0 $QTY_X $y Cant\n")
                body.append("TEXT 7 0 $TOTAL_X $y Total\n")
                y += 42

body.append("LINE $BORDER_MARGIN_LEFT $y ${PAGE_WIDTH - BORDER_MARGIN_RIGHT} $y 2\n")
// body.append("LINE ${LEFT_X - LINE_MARGIN - 10} $y ${RIGHT_X + LINE_MARGIN + 80} $y 2\n")
// body.append("LINE ${LEFT_X - LINE_MARGIN - 10} $y ${RIGHT_X + LINE_MARGIN + 80} $y 2\n")

                // body.append("LINE 0 $y ${PAGE_WIDTH - 20} $y 2\n")
                y += 17
                Log.d("PrinterService", "antes45")

                // Filas
             for ((nombre, qty, total) in productos) {
    // wrap con límite ajustado para que no invada la columna QTY
    val prodLines = wrapColumnCPCL(nombre, MAX_PROD_CHARS_REAL)
    
    prodLines.forEachIndexed { idx, line ->
        body.append("TEXT 7 0 $PRODUCT_X ${y + idx * LINE_H} $line\n")
    }

    // cantidad y total en la primera línea del producto
    body.append("TEXT 7 0 $QTY_X $y $qty\n")
    body.append("TEXT 7 0 $TOTAL_X $y $total\n")

    // subimos Y según cuántas líneas ocupó el nombre
    // y += (prodLines.size * LINE_H) + 8
        y += (prodLines.size * LINE_H) + ESPACIO_ENTRE_PRODUCTOS

}
                Log.d("PrinterService", "3dasdasd")

                // // Línea final y total general
                // body.append("LINE 20 $y ${PAGE_WIDTH - 20} $y 2\n")
                // y += 16
                // body.append("TEXT 7 0 $TOTAL_X $y 5.75\n")
                // y += 60
val subTotal = 5.00
val total = 5.75
val vuelto = 0.25

// =====================
// Constantes de alineación
// =====================
// val CHAR_WIDTH = 7      // ancho aprox por carácter en la fuente CPCL 7
// val MARGIN_RIGHT = 20   // margen derecho

// =====================
// Línea separadora
// =====================
body.append("LINE $BORDER_MARGIN_LEFT $y ${PAGE_WIDTH - BORDER_MARGIN_RIGHT} $y 2\n")
y += 16
val TOTAL_X_ADJ = PAGE_WIDTH - 200 // 150px antes del borde derecho, ajustable

// =====================
// Función para alinear números
// =====================
fun calcularNumeroX(numero: String, pageWidth: Int): Int {
    val longitud = numero.length
    return pageWidth - MARGIN_RIGHT - (longitud * CHAR_WIDTH)
}

// =====================
// Subtotal
// =====================
val subtotalTexto = formatMoneda(subTotal)
val xSubtotal = calcularNumeroX(subtotalTexto, PAGE_WIDTH)
body.append("TEXT 7 0 $LEFT_X $y Subtotal:\n")
body.append("TEXT 7 0 $xSubtotal $y $subtotalTexto\n")
y += LINE_H

// =====================
// Total
// =====================
val totalTexto = formatMoneda(total)
val xTotal = calcularNumeroX(totalTexto, PAGE_WIDTH)
body.append("TEXT 7 0 $LEFT_X $y Total:\n")
body.append("TEXT 7 0 $xTotal $y $totalTexto\n")
y += LINE_H

// =====================
// Vuelto
// =====================
val vueltoTexto = formatMoneda(vuelto)
val xVuelto = calcularNumeroX(vueltoTexto, PAGE_WIDTH)
body.append("TEXT 7 0 $LEFT_X $y Vuelto:\n")
body.append("TEXT 7 0 $xVuelto $y $vueltoTexto\n")
y += 45

// =====================
// Código QR centrado
// =====================


// val qrSize = 210
// val qrX = (PAGE_WIDTH - qrSize) / 2
// // val qrData = "https://admin.factura.gob.sv/consultaPublica?ambiente=$ambiente&codGen=$numControl&fechaEmi=$fecha"

// // val qrBitmap = generarQR(qrData, qrSize)
// // val qrBytes = bitmapToCPCLBytes(qrBitmap, qrX, y) // <-- nueva función (ver abajo)
// // y += qrSize + 25

// // val qrX = (PAGE_WIDTH - 210) / 2
// body.append("B QR $qrX $y M 2 U 4\n")
// body.append("MA,https://admin.factura.gob.sv/consultaPublica?ambiente=$ambiente&codGen=$codGen&fechaEmi=$fecha\n")
// body.append("ENDQR\n")
//  y += qrSize + 17


val qrSize = 210

// 🔹 Escala del QR: controla el tamaño real impreso (1–6)
// U 4 → mediano, U 5 → grande
val qrScale = 4

// 🔹 Desplazamiento de compensación (depende del ancho del papel)
val adjust = when (PAGE_WIDTH) {
    in 550..600 -> 10     // impresora de 80 mm
    in 400..520 -> -5     // impresora de 58 mm
    else -> 0
}

// 🔹 Cálculo del centro real del QR
val qrX = ((PAGE_WIDTH - qrSize) / 2) + 60
                Log.d("PrinterService", "qr")

// 🔹 Impresión del QR (modo CPCL nativo)
body.append("B QR $qrX $y M 2 U $qrScale\n")
body.append("MA,https://admin.factura.gob.sv/consultaPublica?ambiente=$ambiente&codGen=$codGen&fechaEmi=$fecha\n")
body.append("ENDQR\n")

// 🔹 Espacio inferior antes del texto
y += (qrSize * (qrScale / 5.0)).toInt() + 25


// y += 15
val text1 = "Escanea el codigo QR para validar tu DTE"
val text2 = "Powered by SeedCodeSV"
val text1X = (PAGE_WIDTH / 2) - (text1.length * CHAR_WIDTH / 2)
val text2X = (PAGE_WIDTH / 2) - (text2.length * CHAR_WIDTH / 2)
body.append("TEXT 7 0 $text1X $y $text1\n")
y += 25
body.append("TEXT 7 0 $text2X $y $text2\n")
y += 30
// =====================
// Texto debajo del QR centrado
// =====================
// val text = "Powered by SeedCodeSV"
// val textX = (PAGE_WIDTH / 2) - (text.length * CHAR_WIDTH / 2)
// body.append("TEXT 7 0 $textX $y $text\n")
// y += 30

val pageHeight = y + 20

val cpclCmd = buildString {
    append("! 0 200 200 $pageHeight 1\n")
    append("PAGE-WIDTH $PAGE_WIDTH\n")
    append(body.toString())
    append("PRINT\n")
}

                Log.d("PrinterService", "antes")

// val cpclHeader = buildString {
//     append("! 0 200 200 $pageHeight 1\n")
//     append("PAGE-WIDTH $PAGE_WIDTH\n")
//     append(body.toString()) // todo lo demás excepto el QR
// }
// outputStream?.write(cpclHeader.toByteArray(Charsets.ISO_8859_1))

// escribir los bytes del QR directamente (sin convertir a string)
// outputStream.write(qrBytes)

outputStream?.write("PRINT\n".toByteArray(Charsets.ISO_8859_1))
outputStream?.flush()

outputStream?.write(cpclCmd.toByteArray(Charset.forName("ISO-8859-1")))
outputStream?.flush()
                Log.d("PrinterService", "se mando el ticket")
         } catch (e: Exception) {
    Log.e("BluetoothPrinter", "Error: ${e.message}", e)

} finally {
  closeSafe()
}
          } catch (e: Exception) {
            Log.e("BluetoothPrinterService", "Error impresión: ${e.message}", e)
        }
    }
}
fun formatMoneda(valor: Double): String {
    return "$" + String.format("%.2f", valor)
}


private suspend fun printTicketOnce(payload: String) {
    var localSocket: BluetoothSocket? = null
    var localOutput: OutputStream? = null

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e("PrinterService", "🚫 Falta permiso BLUETOOTH_CONNECT")
                return
            }
        }

        // =========================================
        // 🔹 Recuperar impresora desde la base local
        // =========================================
        val dbHelper = PrinterDatabaseHelper(applicationContext)
        val printerInfo = dbHelper.getLastPrinter()
            ?: throw Exception("No hay información de impresora guardada")

        val address = printerInfo["address_ip"] as? String
            ?: throw Exception("La impresora no tiene dirección registrada")

        val printerId = printerInfo["id"] as Int
        Log.d("PrinterService", "🟢 Iniciando impresión hacia $address")

        // =========================================
        // 🔹 Adaptador Bluetooth
        // =========================================
        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        btAdapter.cancelDiscovery()

        val device = btAdapter.bondedDevices.firstOrNull { it.address == address }
            ?: throw Exception("Impresora no emparejada")

        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // =========================================
        // 🔹 Intentar conexión con retry y fallback
        // =========================================
        suspend fun tryConnectWithRetry(maxRetries: Int = 3): BluetoothSocket {
            var attempt = 0
            var lastError: Exception? = null
            while (attempt < maxRetries) {
                try {
                    delay(300)
                    btAdapter.cancelDiscovery()
                    val tmpSocket = device.createInsecureRfcommSocketToServiceRecord(uuid)
                    tmpSocket.connect()
                    Log.d("PrinterService", "✅ Conectado a ${device.name} (intento ${attempt + 1})")
                    return tmpSocket
                } catch (e: Exception) {
                    Log.w("PrinterService", "⚠️ Falló intento ${attempt + 1}: ${e.message}")
                    lastError = e
                    try { localSocket?.close() } catch (_: Exception) {}
                    delay(800)
                }
                attempt++
            }

            // 🔁 Fallback canal 1
            Log.w("PrinterService", "🔁 Intentando fallback manual (canal 1)...")
            return try {
                val fallbackSocket = device.javaClass
                    .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
                fallbackSocket.connect()
                Log.d("PrinterService", "✅ Conectado por fallback canal 1")
                fallbackSocket
            } catch (e2: Exception) {
                throw IOException("❌ No se pudo conectar tras $maxRetries intentos", e2)
            }
        }

        localSocket = tryConnectWithRetry(maxRetries = 3)
        localOutput = localSocket.outputStream

        // =========================================
        // 🧾 Construcción del ticket
        // =========================================
        val json = JSONObject(payload)
        val PAGE_WIDTH = 558
        val LEFT_X = 85
        val tableBody = StringBuilder()
        val OFFSET_X = 10 //mover a la derecha todo el texto entre mas sea mas es el espacio que deja al principio
        val TABLE_OFFSET_X = 10  // <- mueve toda la tabla a la derecha
        var y = 25
        val LINE_H = 28

        fun calcularNumeroXC(texto: String, paddingRight: Int = 40): Int {
    val charWidth = 14
    val textWidth = texto.length * charWidth
    return PAGE_WIDTH - textWidth - paddingRight
}


        val empresa = json.optString("branchName", "EMPRESA DESCONOCIDA")
        val dte = json.optString("typeDte", "SIN-DTE")
        val ambiente = "01"
        val caja = json.optString("box", "0")
        val fecha = "${json.optString("date")} ${json.optString("time")}"
        val fechaQR = json.optString("date")
        val numControl = json.optString("controlNumber", "SIN-CONTROL")
        val codGen = json.optString("generationCode", "-")
        val cliente = json.optString("customer", "CONSUMIDOR FINAL")
        val empleado = json.optString("employeeName", "N/A")
        val total = json.optDouble("total", 0.0)
        val subTotal = json.optDouble("subTotal", 0.0)
        val vuelto = json.optDouble("vuelto", 0.0)

        val detailsArray = json.getJSONArray("details")
        val productos = mutableListOf<Triple<String, String, String>>()
        for (i in 0 until detailsArray.length()) {
            val item = detailsArray.getJSONObject(i)
            val nombre = item.getString("name")
            val cantidad = item.getInt("quantity").toString()
            val totalUnit = item.getDouble("totalUnit")
            productos.add(Triple(nombre, cantidad, String.format("%.2f", totalUnit)))
        }

      val PRODUCT_X = LEFT_X + TABLE_OFFSET_X
      val PRODUCT_MAX_WIDTH = 260   // ancho máximo para texto de producto
val QTY_X = PRODUCT_X + PRODUCT_MAX_WIDTH + 20  // empieza después del producto
val TOTAL_X = QTY_X + 80  

        val MAX_PROD_CHARS = 17 //cuantos caracteres para el nombre del producto
        val ESPACIO_ENTRE_PRODUCTOS = 16
        val BORDER_MARGIN_LEFT = 80
        val BORDER_MARGIN_RIGHT = -250
        val body = StringBuilder()

        // =========================================
        // 🔹 Encabezado
        // =========================================
        body.append("TEXT 7 0 ${LEFT_X + OFFSET_X} $y $empresa\n"); y += 40
        body.append("LINE $BORDER_MARGIN_LEFT $y ${PAGE_WIDTH - BORDER_MARGIN_RIGHT} $y 2\n"); y += 20
        body.append("TEXT 7 0 ${LEFT_X + OFFSET_X} $y DTE: $dte\n"); y += LINE_H
        body.append("TEXT 7 0 ${LEFT_X + OFFSET_X} $y Caja: $caja\n"); y += LINE_H
        body.append("TEXT 7 0 ${LEFT_X + OFFSET_X} $y Fecha: $fecha\n"); y += LINE_H

        for (linea in wrapTextCPCL("Cliente: $cliente", 32)) {
            body.append("TEXT 7 0 ${LEFT_X + OFFSET_X} $y $linea\n"); y += LINE_H
        }
        for (linea in wrapTextCPCL("Empleado: $empleado", 32)) {
            body.append("TEXT 7 0 ${LEFT_X + OFFSET_X} $y $linea\n"); y += LINE_H
        }
        for (linea in wrapTextCPCL("Num Control DTE: $numControl", 32)) {
            body.append("TEXT 7 0 ${LEFT_X + OFFSET_X} $y $linea\n"); y += LINE_H
        }

        body.append("LINE $BORDER_MARGIN_LEFT $y ${PAGE_WIDTH - BORDER_MARGIN_RIGHT} $y 2\n")
        y += 20

        // =========================================
        // 🔹 Tabla productos
        // =========================================
    val subtotalTexto = formatMoneda(subTotal)
        val totalTexto = formatMoneda(total)
        val vueltoTexto = formatMoneda(vuelto)
        //nueva tabla 
        body.append("TEXT 7 0 $PRODUCT_X $y Producto\n")
        body.append("TEXT 7 0 $QTY_X $y Cant\n")
        body.append("TEXT 7 0 $TOTAL_X $y Total\n")
        y += 42
        body.append("LINE $BORDER_MARGIN_LEFT $y ${PAGE_WIDTH - BORDER_MARGIN_RIGHT} $y 2\n")
        y += 17

      fun wrapColumnCPCL(text: String, maxChars: Int): List<String> {
    val lines = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        val end = minOf(start + maxChars, text.length)
        lines.add(text.substring(start, end))
        start += maxChars
    }
    return lines
}
      for ((nombre, qty, totalU) in productos) {
    val prodLines = wrapColumnCPCL(nombre, MAX_PROD_CHARS)
    prodLines.forEachIndexed { idx, line ->
        body.append("TEXT 7 0 $PRODUCT_X ${y + idx * LINE_H} $line\n")
    }
    body.append("TEXT 7 0 $QTY_X $y $qty\n")
    body.append("TEXT 7 0 $TOTAL_X $y $totalU\n")
    y += (prodLines.size * LINE_H) + ESPACIO_ENTRE_PRODUCTOS
}

        body.append("LINE $BORDER_MARGIN_LEFT $y ${PAGE_WIDTH - BORDER_MARGIN_RIGHT} $y 2\n")
        y += 16
        
body.append(tableBody.toString())
body.append("TEXT 7 0 ${LEFT_X + OFFSET_X} $y Subtotal:\n")
body.append("TEXT 7 0 ${calcularNumeroXC(subtotalTexto)} $y $subtotalTexto\n")
y += LINE_H

body.append("TEXT 7 0 ${LEFT_X + OFFSET_X} $y Total:\n")
body.append("TEXT 7 0 ${calcularNumeroXC(totalTexto)} $y $totalTexto\n")
y += LINE_H

body.append("TEXT 7 0 ${LEFT_X + OFFSET_X} $y Vuelto:\n")
body.append("TEXT 7 0 ${calcularNumeroXC(vueltoTexto)} $y $vueltoTexto\n")
y += 45

        //========================================================
//         tableBody.append("TEXT 7 0 $PRODUCT_X $y Producto\n")
// tableBody.append("TEXT 7 0 $QTY_X $y Cant\n")
// tableBody.append("TEXT 7 0 $TOTAL_X $y Total\n")
// y += 42
// tableBody.append("LINE $BORDER_MARGIN_LEFT $y ${PAGE_WIDTH - BORDER_MARGIN_RIGHT} $y 2\n")
// y += 17

// for ((nombre, qty, totalU) in productos) {
//     val prodLines = wrapColumnCPCL(nombre, MAX_PROD_CHARS)
//     prodLines.forEachIndexed { idx, line ->
//         tableBody.append("TEXT 7 0 $PRODUCT_X ${y + idx * LINE_H} $line\n")
//     }
//     tableBody.append("TEXT 7 0 $QTY_X $y $qty\n")
//     tableBody.append("TEXT 7 0 $TOTAL_X $y $totalU\n")
//     y += (prodLines.size * LINE_H) + ESPACIO_ENTRE_PRODUCTOS
// }
//         //========================================================

//         // =========================================
//         // 🔹 Totales
//         // =========================================


//         fun calcularNumeroX(texto: String): Int {
//             val longitud = texto.length
//             return PAGE_WIDTH - MARGIN_RIGHT - (longitud * CHAR_WIDTH)
//         }
    
//         body.append("TEXT 7 0 ${LEFT_X + OFFSET_X} $y Subtotal:\n")
//         body.append("TEXT 7 0 ${calcularNumeroX(subtotalTexto)} $y $subtotalTexto\n")
//         y += LINE_H
//         body.append("TEXT 7 0${LEFT_X + OFFSET_X} $y Total:\n")
//         body.append("TEXT 7 0 ${calcularNumeroX(totalTexto)} $y $totalTexto\n")
//         y += LINE_H
//         body.append("TEXT 7 0 ${LEFT_X + OFFSET_X} $y Vuelto:\n")
//         body.append("TEXT 7 0 ${calcularNumeroX(vueltoTexto)} $y $vueltoTexto\n")
//         y += 45

        // =========================================
        // 🔹 QR
        // =========================================
        body.append("SETMAG 1\n")
        body.append("TONE 3\n")
        val qrSize = 205
        val qrScale = 5
        val qrX = ((PAGE_WIDTH - qrSize) / 2) + OFFSET_X + 13
        body.append("B QR $qrX $y M 2 U $qrScale\n")
        body.append("MA,https://admin.factura.gob.sv/consultaPublica?ambiente=$ambiente&codGen=$codGen&fechaEmi=$fechaQR\n")
        body.append("ENDQR\n")
        y += (qrSize * (qrScale / 5.0)).toInt() + 25

        val text1 = "Consulta tu DTE escaneando el QR"
        val text2 = "Powered by SeedCodeSV"
        body.append("TEXT 7 0 105 $y $text1\n"); y += 25
        val text2X = (PAGE_WIDTH / 2) - (text2.length * CHAR_WIDTH / 2)
        body.append("TEXT 7 0 $text2X $y $text2\n"); y += 30

        val pageHeight = y + 20
        val cpclCmd = buildString {
            append("! 0 200 200 $pageHeight 1\n")
            append("PAGE-WIDTH $PAGE_WIDTH\n")
            append(body.toString())
            append("PRINT\n")
        }

        // =========================================
        // 🔹 Envío a impresora
        // =========================================
        localOutput.write(cpclCmd.toByteArray(Charsets.ISO_8859_1))
        localOutput.flush()
        Log.d("PrinterService", "🖨️ Ticket enviado correctamente")
val before = printerInfo["ticket"]
val after = dbHelper.incrementTicket(printerId)
Log.d("PrinterService", "🎫 Ticket antes: $before → después: $after")
        // dbHelper.incrementTicket(printerId)

    } catch (e: Exception) {
        Log.e("PrinterService", "❌ Error al imprimir: ${e.message}", e)
    } finally {
        try { localOutput?.flush() } catch (_: Exception) {}
        try { localOutput?.close() } catch (_: Exception) {}
        try { localSocket?.close() } catch (_: Exception) {}
        Log.d("PrinterService", "🔻 Socket cerrado correctamente")
    }
}

private fun wrapTextCPCL(text: String, maxChars: Int): List<String> {
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var currentLine = ""

    for (word in words) {
        val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
        if (candidate.length > maxChars) {
            lines.add(currentLine)
            currentLine = word
        } else {
            currentLine = candidate
        }
    }
    if (currentLine.isNotEmpty()) lines.add(currentLine)
    return lines
}
}
