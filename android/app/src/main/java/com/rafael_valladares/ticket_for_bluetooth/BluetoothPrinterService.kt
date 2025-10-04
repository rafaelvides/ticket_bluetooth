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

    private var socketRed: Socket? = null
    private val SOCKET_URL = "wss://si-ham-api.erpseedcodeone.online/socket"
private var ioSocket: Socket? = null                 // 👈 Socket.IO

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
            socket = device.createInsecureRfcommSocketToServiceRecord(uuid)
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

        socketRed?.on("response-print-by-bluetooth") { args ->
            val data = args.firstOrNull() as? JSONObject ?: return@on
            Log.d("PrinterService", "📩 Ticket recibido: $data")
            Thread { printTicketInBackgroundSocket(data.toString()) }.start()
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

   private fun printTicketInBackgroundSocket(payload: String) {
            Log.d("PrinterService", "📩 Ticket recibido: $payload")

    try {
        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice =
            btAdapter.bondedDevices.firstOrNull { it.address == "66:32:64:9A:65:3F" }
                ?: throw Exception("Impresora no emparejada")

        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        socket = device.createInsecureRfcommSocketToServiceRecord(uuid)
        socket?.connect()
        outputStream = socket?.outputStream

        // 🔹 Parseamos el JSON recibido
        val json = JSONObject(payload)

        val empresa = json.optString("branchName", "EMPRESA DESCONOCIDA")
        val dte = json.optString("generationCode", "SIN-DTE")
        val caja = json.optString("box", "0")
        val fecha = "${json.optString("date")} ${json.optString("time")}"
        val numControl = json.optString("controlNumber", "SIN-CONTROL")
        val selloRecibido = json.optString("selloRecibido", "-")
        val cliente = json.optString("customer", "CONSUMIDOR FINAL")
        val empleado = json.optString("employeeName", "N/A")
        val total = json.optDouble("total", 0.0)

        val productos = mutableListOf<TicketProduct>()
        val detailsArray = json.optJSONArray("details") ?: JSONArray()
        for (i in 0 until detailsArray.length()) {
            val obj = detailsArray.getJSONObject(i)
            productos.add(
                TicketProduct(
                    name = obj.optString("name"),
                    quantity = obj.optInt("quantity"),
                    totalUnit = obj.optDouble("totalUnit")
                )
            )
        }

        // 🔹 Layout igual al tuyo
        val PAGE_WIDTH = 515
        val LEFT_X = 85
        var y = 25

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
        body.append("TEXT 7 0 $LEFT_X $y Num Control DTE: $numControl\n"); y += LINE_H
        body.append("TEXT 7 0 $LEFT_X $y Cliente: $cliente\n"); y += LINE_H
        body.append("TEXT 7 0 $LEFT_X $y Cajero(a): $empleado\n"); y += (LINE_H + 4)

        body.append("LINE 20 $y ${PAGE_WIDTH - 20} $y 2\n")
        y += 12
        body.append("TEXT 7 0 $PRODUCT_X $y Producto\n")
        body.append("TEXT 7 0 $QTY_X $y Cant\n")
        body.append("TEXT 7 0 $TOTAL_X $y Total\n")
        y += 18
        body.append("LINE 20 $y ${PAGE_WIDTH - 20} $y 2\n")
        y += 10

        for (prod in productos) {
            val prodLines = wrapColumnCPCL(prod.name, MAX_PROD_CHARS)
            prodLines.forEachIndexed { idx, line ->
                body.append("TEXT 7 0 $PRODUCT_X ${y + idx * LINE_H} $line\n")
            }
            body.append("TEXT 7 0 $QTY_X $y ${prod.quantity}\n")
            body.append("TEXT 7 0 $TOTAL_X $y ${"%.2f".format(prod.totalUnit)}\n")
            y += (prodLines.size * LINE_H) + 8
        }

        body.append("LINE 20 $y ${PAGE_WIDTH - 20} $y 2\n")
        y += 16
        body.append("TEXT 7 0 $TOTAL_X $y ${"%.2f".format(total)}\n")
        y += 40
        body.append("TEXT 7 0 $LEFT_X $y SR: $selloRecibido\n")
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

        Log.d("BluetoothService", "✅ Ticket impreso con datos del servidor")

    } catch (e: Exception) {
        Log.e("BluetoothService", "Error: ${e.message}", e)
    }finally {
        try { outputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
    }
}

}
