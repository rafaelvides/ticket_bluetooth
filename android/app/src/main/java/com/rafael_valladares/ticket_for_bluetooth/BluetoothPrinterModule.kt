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
import java.nio.charset.Charset
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Color
import java.io.ByteArrayOutputStream



import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.rafael_valladares.ticket_for_bluetooth.PrinterDatabaseHelper

private const val CHAR_WIDTH = 7      // ancho aprox por carácter en CPCL
private const val MARGIN_RIGHT = 30   // margen derecho
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
                val LINE_MARGIN = 5

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
                val ambiente = "01"
                val caja = "00000"
                val fecha = "2025-10-04"
                val numControl = "DTE-01-M001P001-0"
                val codGen = "F020C26C-819F-4677-B388-4BB5C676D134"

                val productos = listOf(
                    Triple("Galletas super extra", "1", "1.00"),
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

// val cpclCmd = buildString {
//     append("! 0 200 200 $pageHeight 1\n")
//     append("PAGE-WIDTH $PAGE_WIDTH\n")
//     append(body.toString())
//     append("PRINT\n")
// }


val cpclHeader = buildString {
    append("! 0 200 200 $pageHeight 1\n")
    append("PAGE-WIDTH $PAGE_WIDTH\n")
    append(body.toString()) // todo lo demás excepto el QR
}
outputStream.write(cpclHeader.toByteArray(Charsets.ISO_8859_1))

// escribir los bytes del QR directamente (sin convertir a string)
// outputStream.write(qrBytes)

// cierre final
outputStream.write("PRINT\n".toByteArray(Charsets.ISO_8859_1))
outputStream.flush()

// // Log.d("CPCL_CMD", cpclCmd)
// outputStream.write(cpclCmd.toByteArray(Charset.forName("ISO-8859-1")))
// outputStream.flush()
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

fun formatMoneda(valor: Double): String {
    return "$" + String.format("%.2f", valor)
}

fun calcularNumeroX(texto: String, pageWidth: Int): Int {
    val longitud = texto.length
    return pageWidth - MARGIN_RIGHT - (longitud * CHAR_WIDTH)
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
fun startBackgroundService( 
    model: String,
    name: String,
    ticket: String,
    address_ip: String,
    promise: Promise) {
    try {
        val intent = Intent(reactApplicationContext, BluetoothPrinterService::class.java)
        reactApplicationContext.startService(intent)

          val dbHelper = PrinterDatabaseHelper(reactApplicationContext)
        dbHelper.insertPrinterInfo(
            model = model,
            name = name,
            ticket = ticket,
            address_ip = address_ip,
        )
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

         // 2️⃣ Eliminamos todos los registros de la tabla 'printer'
        val dbHelper = PrinterDatabaseHelper(reactApplicationContext)
        val db = dbHelper.writableDatabase
        val deletedRows = db.delete("printer", null, null)
        db.close()
        promise.resolve("Servicio detenido")
    } catch (e: Exception) {
        promise.reject("ERROR", e.message)
    }
}
@ReactMethod
fun getAllPrinterRecords(promise: Promise) {
    try {
        val dbHelper = PrinterDatabaseHelper(reactApplicationContext)
        val records = dbHelper.getAllPrinterDetails()  // solo una versión existe

        val array = Arguments.createArray()
        for (rec in records) {
            val map = Arguments.createMap()
            map.putInt("id", rec["id"] as Int)
            map.putString("model", rec["model"] as String)
            map.putString("name", rec["name"] as String)
            map.putString("address_ip", rec["address_ip"] as String)
            map.putInt("ticket", rec["ticket"] as Int)
            array.pushMap(map)
        }

        promise.resolve(array)
    } catch (e: Exception) {
        promise.reject("DB_ERROR", e.message, e)
    }
}
@ReactMethod
fun getPrinterFullInfo(promise: Promise) {
    Thread {
        var socket: BluetoothSocket? = null
      val btAdapter = BluetoothAdapter.getDefaultAdapter()
if (btAdapter == null) {
    promise.reject("NO_BLUETOOTH", "Este dispositivo no tiene Bluetooth")
    return@Thread
}

if (!btAdapter.isEnabled) {
    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    reactApplicationContext.startActivity(intent)
    promise.reject("BLUETOOTH_DISABLED", "El Bluetooth está apagado. Se abrió la configuración.")
    return@Thread
}
        try {
            val btAdapter = BluetoothAdapter.getDefaultAdapter()
            if (btAdapter == null || !btAdapter.isEnabled) {
                promise.reject("BLUETOOTH_DISABLED", "Bluetooth no está activo")
                return@Thread
            }

            // 1️⃣ Conectar a la impresora por MAC
            val device: BluetoothDevice =
                btAdapter.bondedDevices.firstOrNull { it.address == "66:32:64:9A:65:3F" }
                    ?: throw Exception("Impresora no emparejada")

            val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            socket = device.createInsecureRfcommSocketToServiceRecord(uuid)
            socket.connect()

            val outputStream = socket.outputStream
            val inputStream = socket.inputStream

            // 2️⃣ Comando para pedir estado de la impresora
            // ESC/POS estándar: DLE EOT n (n = 1 batería, 2 papel, 3 estado general)
            val commands = listOf(
                byteArrayOf(0x10, 0x04, 0x01), // batería
                byteArrayOf(0x10, 0x04, 0x02), // papel
                byteArrayOf(0x10, 0x04, 0x03)  // estado general
            )

            val info = Arguments.createMap()

            for (cmd in commands) {
                outputStream.write(cmd)
                outputStream.flush()
                Thread.sleep(200) // dar tiempo a la impresora de responder

                val buffer = ByteArray(256)
                val read = inputStream.read(buffer)
                if (read > 0) {
                    val resp = buffer.copyOf(read)
                    when (cmd[2].toInt()) {
                        0x01 -> info.putInt("battery", resp[0].toInt() and 0xFF) // valor real
                        0x02 -> info.putBoolean("paper", (resp[0].toInt() and 0x01) != 0)
                        0x03 -> {
                            info.putString("status", "OK") // parsea si quieres más detalle
                            info.putString("model", "MP806L") // si el firmware envía modelo, parsear
                        }
                    }
                }
            }

            // 3️⃣ Conexión
            info.putString("connection", if (socket.isConnected) "Connected" else "Disconnected")

            promise.resolve(info)

        } catch (e: Exception) {
            promise.reject("ERROR_FETCHING_INFO", e.message, e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }.start()
}

@ReactMethod
fun checkBluetoothStatus(promise: Promise) {
    try {
        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        if (btAdapter == null) {
            promise.reject("NO_BLUETOOTH", "Este dispositivo no tiene Bluetooth")
            return
        }

        if (!btAdapter.isEnabled) {
            promise.reject("BLUETOOTH_DISABLED", "El Bluetooth está apagado. Por favor, actívalo.")
            return
        }

        val activity = reactApplicationContext.currentActivity
        if (activity == null) {
            promise.reject("NO_ACTIVITY", "No hay actividad activa para verificar permisos")
            return
        }

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            promise.reject("NO_PERMISSIONS", "Faltan permisos de Bluetooth")
            return
        }

        promise.resolve("✅ Bluetooth disponible, encendido y con permisos")
    } catch (e: Exception) {
        promise.reject("BT_CHECK_ERROR", e.message)
    }
}

    @ReactMethod
    fun requestBluetoothPermissions(promise: Promise) {
        try {
           val activity: Activity? = reactApplicationContext.currentActivity
if (activity == null) {
    promise.reject("NO_ACTIVITY", "No hay actividad activa para abrir el diálogo.")
    return
}
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )
            } else {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
                )
            }

            val missing = permissions.filter {
                ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            }

            if (missing.isEmpty()) {
                // ✅ Ya tiene permisos
                promise.resolve("Permisos de Bluetooth ya otorgados")
            } else {
                // 🔹 Pedir permisos
                ActivityCompat.requestPermissions(activity, missing.toTypedArray(), 1001)
                promise.resolve("Permisos solicitados al usuario")
            }
        } catch (e: Exception) {
            promise.reject("PERMISSION_ERROR", e.message, e)
        }
    }

    // ✅ Abrir configuración Bluetooth del sistema
    @ReactMethod
    fun openBluetoothSettings(promise: Promise) {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            reactApplicationContext.startActivity(intent)
            promise.resolve("Configuración de Bluetooth abierta")
        } catch (e: Exception) {
            promise.reject("SETTINGS_ERROR", e.message, e)
        }
    }

    // ✅ Mostrar diálogo nativo para encender Bluetooth
    @ReactMethod
    fun requestEnableBluetooth(promise: Promise) {
        try {
          val activity: Activity? = reactApplicationContext.currentActivity
if (activity == null) {
    promise.reject("NO_ACTIVITY", "No hay actividad activa para abrir el diálogo.")
    return
}

            val intent = Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivityForResult(intent, 1002)
            promise.resolve("Mostrando diálogo para encender Bluetooth")
        } catch (e: Exception) {
            promise.reject("ENABLE_ERROR", e.message, e)
        }
    }


}
fun generarQR(data: String, size: Int): Bitmap {
    val writer = QRCodeWriter()
    val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}

// Convertir Bitmap a comandos CPCL ("EG" = graphic image)
fun bitmapToCPCL(bitmap: Bitmap, x: Int, y: Int): String {
    val width = bitmap.width
    val height = bitmap.height
    val bytesPerRow = (width + 7) / 8

    val sb = StringBuilder()
    sb.append("EG $x $y $bytesPerRow $height ")
    val imageBytes = ByteArray(bytesPerRow * height)

    var index = 0
    for (py in 0 until height) {
        var bitIndex = 0
        var currentByte = 0
        for (px in 0 until width) {
            val color = bitmap.getPixel(px, py)
            val isBlack = Color.red(color) < 128 && Color.green(color) < 128 && Color.blue(color) < 128
            currentByte = (currentByte shl 1) or if (isBlack) 1 else 0
            bitIndex++
            if (bitIndex == 8) {
                imageBytes[index++] = currentByte.toByte()
                bitIndex = 0
                currentByte = 0
            }
        }
        if (bitIndex > 0) {
            currentByte = currentByte shl (8 - bitIndex)
            imageBytes[index++] = currentByte.toByte()
        }
    }

    sb.append(String(imageBytes, Charsets.ISO_8859_1))
    sb.append("\n")
    return sb.toString()
}
fun bitmapToCPCLBytes(bitmap: Bitmap, x: Int, y: Int): ByteArray {
    val width = bitmap.width
    val height = bitmap.height
    val bytesPerRow = (width + 7) / 8
    val data = ByteArrayOutputStream()

    // encabezado del comando CPCL
    val header = "EG $x $y $bytesPerRow $height "
    data.write(header.toByteArray(Charsets.ISO_8859_1))

    // cuerpo binario
    var byteVal = 0
    var bitCount = 0
    for (row in 0 until height) {
        for (col in 0 until width) {
            val color = bitmap.getPixel(col, row)
            val isBlack = Color.red(color) < 128 && Color.green(color) < 128 && Color.blue(color) < 128
            byteVal = (byteVal shl 1) or if (isBlack) 1 else 0
            bitCount++
            if (bitCount == 8) {
                data.write(byteVal)
                bitCount = 0
                byteVal = 0
            }
        }
        if (bitCount > 0) {
            data.write(byteVal shl (8 - bitCount))
            bitCount = 0
            byteVal = 0
        }
    }

    data.write('\n'.code)
    return data.toByteArray()
}