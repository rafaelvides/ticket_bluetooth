package com.rafael_valladares.ticket_for_bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import android.content.Intent
import okhttp3.*  // necesitas agregar OkHttp en tu gradle
import okio.ByteString
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException
import org.json.JSONArray
import kotlinx.coroutines.*
import android.content.pm.PackageManager  // 👈 AÑADE ESTA LÍNEA
import java.nio.charset.Charset
import com.rafael_valladares.ticket_for_bluetooth.PrinterDatabaseHelper

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

    private var socketRed: Socket? = null
    private val SOCKET_URL = "wss://si-ham-api.erpseedcodeone.online/socket"
private var ioSocket: Socket? = null                 // 👈 Socket.IO

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("PrinterService", "🚫 Falta permiso BLUETOOTH_CONNECT")
            return
        }
    }
        startForeground(1, buildNotification("Servicio de impresión activo"))
                startSocket()  // 👈 aquí arrancamos el socket de red

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread {  }.start()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth Printer",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Impresión A Través de Bluetooth")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // ícono BT
            .setOngoing(true)
            .build()
    }

    private fun printTicketInBackground() {
        try {
            val btAdapter = BluetoothAdapter.getDefaultAdapter()
            val device: BluetoothDevice =
                btAdapter.bondedDevices.firstOrNull { it.address == "66:32:64:9A:65:3F" }
                    ?: throw Exception("Impresora no emparejada")

            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            socket = device.createRfcommSocketToServiceRecord(uuid)

            socket?.connect()
            outputStream = socket?.outputStream

            // 👇 Ticket igual al de tu módulo
            val PAGE_WIDTH = 515
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



      private fun startSocket() {
    if (socketRed?.connected() == true) return
    try {
        val opts = IO.Options().apply {
            reconnection = true
            reconnectionAttempts = Int.MAX_VALUE
            reconnectionDelay = 1000
            reconnectionDelayMax = 12000
            transports = arrayOf("websocket")
            query = "transmitterId=52" // 👈 ajusta con tu valor real
        }

        socketRed = IO.socket("ws://192.168.1.64:3000/sales-gateway", opts)

        socketRed?.on(Socket.EVENT_CONNECT) {
            Log.d("PrinterService", "✅ Socket conectado")
        }

        // socketRed?.on("response-print-by-bluetooth") { args ->
        //     val data = args.firstOrNull() as? JSONObject ?: return@on
        //     Log.d("PrinterService", "📩 Ticket recibido: $data")
        //     Thread { printTicketInBackgroundSocket(data.toString()) }.start()
        // }

      socketRed?.on("response-print-by-bluetooth") { args ->
    val data = args.firstOrNull() as? JSONObject ?: return@on
    Log.d("PrinterService", "📩 Ticket recibido: $data")
    serviceScope.launch {
        printTicketOnce(data.toString())
    }
}

        socketRed?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e("PrinterService", "❌ Error de conexión: ${args.joinToString()}")
        }

        socketRed?.on(Socket.EVENT_DISCONNECT) {
            Log.w("PrinterService", "⚠️ Socket desconectado")
        }

        socketRed?.connect()
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
                val dbHelper = PrinterDatabaseHelper(applicationContext)
                    val printerInfo = dbHelper.getLastPrinter()
            ?: throw Exception("No hay información de impresora guardada")

        val address = printerInfo["address_ip"] as? String
            ?: throw Exception("La impresora no tiene dirección registrada")

        val currentTicket = printerInfo["ticket"] as Int
        val printerId = printerInfo["id"] as Int

        Log.d("PrinterService", "🟢 Iniciando impresión...")
        val btAdapter = BluetoothAdapter.getDefaultAdapter()

        val device = btAdapter.bondedDevices.firstOrNull {
            it.address == address
        } ?: throw Exception("Impresora no emparejada")

        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // 🔹 crear SIEMPRE un socket nuevo
        localSocket = device.createInsecureRfcommSocketToServiceRecord(uuid)
        delay(100) // evita conflicto con canal anterior
        localSocket.connect()
        Log.d("PrinterService", "✅ Conectado al Bluetooth")

        localOutput = localSocket.outputStream
        if (localOutput == null) throw Exception("No se pudo abrir flujo de salida")

         val json = JSONObject(payload)
        // val productos1 = mutableListOf<TicketProduct>()
        // val detailsArray = json.optJSONArray("details") ?: JSONArray()
        // for (i in 0 until detailsArray.length()) {
        //     val obj = detailsArray.getJSONObject(i)
        //     productos1.add(
        //         TicketProduct(
        //             name = obj.optString("name"),
        //             quantity = obj.optInt("quantity"),
        //             totalUnit = obj.optDouble("totalUnit")
        //         )
        //     )
        // }
                // Log.d("PrinterService", "📩 Ticket recibido: $empresa1, $productos1")

        // tu ticket aquí
           val PAGE_WIDTH = 515
                val LEFT_X = 85
                var y = 25

                val empresa = json.optString("branchName", "EMPRESA DESCONOCIDA")
                val dte = json.optString("typeDte", "SIN-DTE")
                val ambiente = "01"
                val caja = json.optString("box", "0")
                val fecha = "${json.optString("date")} ${json.optString("time")}"
                val fechaQR = "${json.optString("date")}"
                val numControl = json.optString("controlNumber", "SIN-CONTROL")
                val codGen = json.optString("selloRecibido", "-")
                val cliente = json.optString("customer", "CONSUMIDOR FINAL")
                val empleado = json.optString("employeeName", "N/A")
                val total = json.optDouble("total", 0.0)

                // val productos = listOf(
                //     Triple("Galletas super extra largas con nombre enorme que no cabe", "1", "1.00"),
                //     Triple("Coca Cola 1.5L", "2", "2.50"),
                //     Triple("Pan frances", "5", "2.25"),
                //     Triple("Pan frances", "5", "2.25"),
                // )

val detailsArray = json.getJSONArray("details")

val productos = mutableListOf<Triple<String, String, String>>()

for (i in 0 until detailsArray.length()) {
    val item = detailsArray.getJSONObject(i)
    val nombre = item.getString("name")
    val cantidad = item.getInt("quantity").toString()
    val totalUnit = item.getDouble("totalUnit")
    val totalStr = String.format("%.2f", totalUnit) // 🔸 formatear a 2 decimales

    productos.add(Triple(nombre, cantidad, totalStr))
}


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
                // body.append("TEXT 7 0 $LEFT_X $y Cliente: $cliente\n"); y += (LINE_H + 4)
                // body.append("TEXT 7 0 $LEFT_X $y Empleado: $empleado\n"); y += (LINE_H + 4)

                val textoCliente = "Cliente: $cliente"
val lineasCliente = wrapTextCPCL(textoCliente, 32) // Ajusta 32 al ancho real de tu papel
for (linea in lineasCliente) {
    body.append("TEXT 7 0 $LEFT_X $y $linea\n")
    y += LINE_H
}
y += 4 // espacio extra entre secciones

// 🔹 Empleado
val textoEmpleado = "Empleado: $empleado"
val lineasEmpleado = wrapTextCPCL(textoEmpleado, 32)
for (linea in lineasEmpleado) {
    body.append("TEXT 7 0 $LEFT_X $y $linea\n")
    y += LINE_H
}
y += 4


                val textoNumControl = "Num Control DTE: $numControl"
val lineasNumControl = wrapTextCPCL(textoNumControl, 32) // 🔹 ajusta 32 según ancho del papel
for (linea in lineasNumControl) {
    body.append("TEXT 7 0 $LEFT_X $y $linea\n")
    y += LINE_H
}

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
val subTotal = json.optDouble("subTotal", 0.0)

val vuelto = json.optDouble("vuelto", 0.0)

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
body.append("MA,https://admin.factura.gob.sv/consultaPublica?ambiente=$ambiente&codGen=$codGen&fechaEmi=$fechaQR\n")
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

// cierre final
// outputStream?.write("PRINT\n".toByteArray(Charsets.ISO_8859_1))
// outputStream?.flush()

// outputStream?.write(cpclCmd.toByteArray(Charset.forName("ISO-8859-1")))
// outputStream?.flush()
        localOutput.write(cpclCmd.toByteArray(Charsets.ISO_8859_1))
        localOutput.flush()
        Log.d("PrinterService", "🖨️ Ticket enviado correctamente")
        val newTicket = dbHelper.incrementTicket(printerId)

    } catch (e: Exception) {
        Log.e("PrinterService", "❌ Error al imprimir: ${e.message}", e)
    } finally {
        // 🔹 cerrar SIEMPRE el socket local (no el global)
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
