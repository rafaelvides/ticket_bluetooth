package com.rafael_valladares.ticket_for_bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.facebook.react.bridge.*
import java.io.IOException
import java.io.OutputStream
import java.util.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import android.content.Intent

class BluetoothPrinterModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "BluetoothPrinterModule"

     @ReactMethod
    fun printText(payload: String, promise: Promise) {
        Thread {
            var socket: BluetoothSocket? = null
            var outputStream: OutputStream? = null
            try {
                val btAdapter = BluetoothAdapter.getDefaultAdapter()
                if (btAdapter == null || !btAdapter.isEnabled) {
                    promise.reject("BLUETOOTH_DISABLED", "Bluetooth no está activo")
                    return@Thread
                }

                val device: BluetoothDevice =
                    btAdapter.bondedDevices.firstOrNull { it.address == "66:32:64:9A:65:3F" }
                        ?: throw Exception("Impresora no emparejada")

                val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                socket = device.createInsecureRfcommSocketToServiceRecord(uuid) // inseguro
                socket.connect()
                outputStream = socket.outputStream

                // ---------- CONFIGURACIÓN ----------
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

                // Encabezado
                body.append("TEXT 7 0 $LEFT_X $y $empresa\n")
                y += 40
                body.append("LINE 20 $y ${PAGE_WIDTH - 20} $y 2\n")
                y += 20

                // Datos fiscales
                body.append("TEXT 7 0 $LEFT_X $y DTE: $dte\n"); y += LINE_H
                body.append("TEXT 7 0 $LEFT_X $y Caja: $caja\n"); y += LINE_H
                body.append("TEXT 7 0 $LEFT_X $y Fecha: $fecha\n"); y += LINE_H
                body.append("TEXT 7 0 $LEFT_X $y Num Control DTE: $numControl\n"); y += (LINE_H + 4)

                body.append("LINE 20 $y ${PAGE_WIDTH - 20} $y 2\n")
                y += 12

                // Tabla encabezado
                body.append("TEXT 7 0 $PRODUCT_X $y Producto\n")
                body.append("TEXT 7 0 $QTY_X $y Cant\n")
                body.append("TEXT 7 0 $TOTAL_X $y Total\n")
                y += 18
                body.append("LINE 20 $y ${PAGE_WIDTH - 20} $y 2\n")
                y += 10

                // Filas
                for ((nombre, qty, total) in productos) {
                    val prodLines = wrapColumnCPCL(nombre, MAX_PROD_CHARS)
                    prodLines.forEachIndexed { idx, line ->
                        body.append("TEXT 7 0 $PRODUCT_X ${y + idx * LINE_H} $line\n")
                    }
                    body.append("TEXT 7 0 $QTY_X $y $qty\n")
                    body.append("TEXT 7 0 $TOTAL_X $y $total\n")
                    y += (prodLines.size * LINE_H) + 8
                }

                // Línea final y total general
                body.append("LINE 20 $y ${PAGE_WIDTH - 20} $y 2\n")
                y += 16
                body.append("TEXT 7 0 $TOTAL_X $y 5.75\n")
                y += 60

                val pageHeight = max(800, y) // altura segura

                val cpclCmd = buildString {
                    append("! 0 200 200 $pageHeight 1\n")
                    append("PAGE-WIDTH $PAGE_WIDTH\n")
                    append(body.toString())
                    append("PRINT\n")
                }

                Log.d("CPCL_CMD", cpclCmd)
                outputStream.write(cpclCmd.toByteArray(Charsets.UTF_8))
                outputStream.flush()

                promise.resolve("✅ Impresión completada")
            } catch (e: Exception) {
                promise.reject("ERROR", e.message)
                Log.e("BluetoothPrinter", "Error: ${e.message}", e)
            } finally {
                try { outputStream?.close() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}
            }
        }.start()
    }


fun wrapTextCPCL(
    text: String,
    maxChars: Int,
    startX: Int,
    startY: Int,
    lineHeight: Int,
    font: Int
): String {
    val sb = StringBuilder()
    val words = text.split(" ")
    var line = ""
    var y = startY

    for (word in words) {
        if ((line + " " + word).trim().length > maxChars) {
            sb.append("TEXT $font 0 $startX $y $line\n")
            y += lineHeight
            line = word
        } else {
            line = if (line.isEmpty()) word else "$line $word"
        }
    }
    if (line.isNotEmpty()) {
        sb.append("TEXT $font 0 $startX $y $line\n")
    }
    return sb.toString()
}

fun formatProductLine(product: String, qty: String, total: String, startY: Int): String {
    val sb = StringBuilder()
    var y = startY
    val maxProdChars = 20 // ajusta según ancho visible para producto

    // Partir producto si es muy largo
    val words = product.split(" ")
    var line = ""
    for (word in words) {
        if ((line + " " + word).trim().length > maxProdChars) {
            sb.append("TEXT 0 0 30 $y $line\n")
            y += 30
            line = word
        } else {
            line = if (line.isEmpty()) word else "$line $word"
        }
    }
    if (line.isNotEmpty()) {
        sb.append("TEXT 0 0 30 $y $line\n")
    }

    // Cantidad (alineada a ~350 px)
    sb.append("TEXT 0 0 350 $startY $qty\n")

    // Total (alineada a ~430 px)
    sb.append("TEXT 0 0 430 $startY $total\n")

    return sb.toString()
}

private fun wrapColumnCPCL(text: String, maxChars: Int): List<String> {
    val out = mutableListOf<String>()
    val words = text.split(" ")
    var line = ""
    for (w in words) {
        val candidate = (if (line.isEmpty()) w else "$line $w")
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


@ReactMethod
fun startBackgroundService(promise: Promise) {
    try {
        val intent = Intent(reactApplicationContext, BluetoothPrinterService::class.java)
        reactApplicationContext.startService(intent)
        promise.resolve("Servicio iniciado")
    } catch (e: Exception) {
        promise.reject("ERROR", e.message)
    }
}

@ReactMethod
fun stopBackgroundService(promise: Promise) {
    try {
        val intent = Intent(reactApplicationContext, BluetoothPrinterService::class.java)
        reactApplicationContext.stopService(intent)
        promise.resolve("Servicio detenido")
    } catch (e: Exception) {
        promise.reject("ERROR", e.message)
    }
}

}
