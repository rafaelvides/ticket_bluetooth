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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper

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
    fun printText(promise: Promise) {
        Thread {
            var socket: BluetoothSocket? = null
            var outputStream: OutputStream? = null

            try {
                val address = "66:32:64:9A:65:3F" // MAC de tu impresora
                Log.d("PrinterService", "🟢 Iniciando impresión...")

                val btAdapter = BluetoothAdapter.getDefaultAdapter()
                btAdapter.cancelDiscovery()

                val device = btAdapter.bondedDevices.firstOrNull { it.address == address }
                    ?: throw Exception("Impresora no emparejada")

                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                socket = device.createInsecureRfcommSocketToServiceRecord(uuid)

                Thread.sleep(300) // Espera pequeña antes de conectar

                try {
                    socket.connect()
                    Log.d("PrinterService", "✅ Conectado correctamente a ${device.name}")
                } catch (e: Exception) {
                    Log.w("PrinterService", "⚠️ Error al conectar, intentando fallback...", e)
                    val fallbackSocket = device.javaClass
                        .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                        .invoke(device, 1) as BluetoothSocket
                    fallbackSocket.connect()
                    socket = fallbackSocket
                    Log.d("PrinterService", "✅ Conexión establecida por fallback (canal 1)")
                }

                outputStream = socket.outputStream
                if (outputStream == null) throw Exception("No se pudo abrir flujo de salida")

                // ===========================================================
                // 🧾 Construcción del ticket CPCL
                // ===========================================================
                val PAGE_WIDTH = 515
                val LEFT_X = 85
                var y = 25
                val LINE_H = 28
                val empresa = "EMPRESA DESCONOCIDA"
                val dte = "SIN-DTE"
                val ambiente = "01"
                val caja = "0"
                val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val fechaQR = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                val numControl = "SIN-CONTROL"
                val codGen = "-"
                val cliente = "CONSUMIDOR FINAL"
                val empleado = "N/A"
                val total = 0.0

                val productos = listOf(
                    Triple("Galletas super extra", "1", "1.00"),
                    Triple("Coca Cola 1.5L", "2", "2.50"),
                    Triple("Pan frances", "5", "2.25")
                )

                val PRODUCT_X = LEFT_X
                val QTY_X = 370
                val TOTAL_X = 455
                val MAX_PROD_CHARS = 24
                val ESPACIO_ENTRE_PRODUCTOS = 16
                val BORDER_MARGIN_LEFT = 80
                val BORDER_MARGIN_RIGHT = -250

                val body = StringBuilder()

                // ===========================================================
                // ENCABEZADO
                // ===========================================================
                body.append("TEXT 7 0 $LEFT_X $y $empresa\n")
                y += 40
                body.append("LINE $BORDER_MARGIN_LEFT $y ${PAGE_WIDTH - BORDER_MARGIN_RIGHT} $y 2\n")
                y += 20

                body.append("TEXT 7 0 $LEFT_X $y DTE: $dte\n"); y += LINE_H
                body.append("TEXT 7 0 $LEFT_X $y Caja: $caja\n"); y += LINE_H
                body.append("TEXT 7 0 $LEFT_X $y Fecha: $fecha\n"); y += LINE_H

                // Cliente
                val lineasCliente = wrapTextCPCL("Cliente: $cliente", 32)
                for (linea in lineasCliente) {
                    body.append("TEXT 7 0 $LEFT_X $y $linea\n")
                    y += LINE_H
                }
                y += 4

                // Empleado
                val lineasEmpleado = wrapTextCPCL("Empleado: $empleado", 32)
                for (linea in lineasEmpleado) {
                    body.append("TEXT 7 0 $LEFT_X $y $linea\n")
                    y += LINE_H
                }
                y += 4

                // Num control
                val lineasNumControl = wrapTextCPCL("Num Control DTE: $numControl", 32)
                for (linea in lineasNumControl) {
                    body.append("TEXT 7 0 $LEFT_X $y $linea\n")
                    y += LINE_H
                }

                body.append("LINE $BORDER_MARGIN_LEFT $y ${PAGE_WIDTH - BORDER_MARGIN_RIGHT} $y 2\n")
                y += 20

                // ===========================================================
                // TABLA DE PRODUCTOS
                // ===========================================================
                body.append("TEXT 7 0 $PRODUCT_X $y Producto\n")
                body.append("TEXT 7 0 $QTY_X $y Cant\n")
                body.append("TEXT 7 0 $TOTAL_X $y Total\n")
                y += 42
                body.append("LINE $BORDER_MARGIN_LEFT $y ${PAGE_WIDTH - BORDER_MARGIN_RIGHT} $y 2\n")
                y += 17

                for ((nombre, qty, totalP) in productos) {
                    val prodLines = wrapColumnCPCL(nombre, MAX_PROD_CHARS)
                    prodLines.forEachIndexed { idx, line ->
                        body.append("TEXT 7 0 $PRODUCT_X ${y + idx * LINE_H} $line\n")
                    }
                    body.append("TEXT 7 0 $QTY_X $y $qty\n")
                    body.append("TEXT 7 0 $TOTAL_X $y $totalP\n")
                    y += (prodLines.size * LINE_H) + ESPACIO_ENTRE_PRODUCTOS
                }

                // ===========================================================
                // TOTALES
                // ===========================================================
                val subTotal = 0.0
                val vuelto = 0.0
                body.append("LINE $BORDER_MARGIN_LEFT $y ${PAGE_WIDTH - BORDER_MARGIN_RIGHT} $y 2\n")
                y += 16

                val subtotalTexto = formatMoneda(subTotal)
                val totalTexto = formatMoneda(total)
                val vueltoTexto = formatMoneda(vuelto)
                val xSubtotal = calcularNumeroX(subtotalTexto, PAGE_WIDTH)
                val xTotal = calcularNumeroX(totalTexto, PAGE_WIDTH)
                val xVuelto = calcularNumeroX(vueltoTexto, PAGE_WIDTH)

                body.append("TEXT 7 0 $LEFT_X $y Subtotal:\n")
                body.append("TEXT 7 0 $xSubtotal $y $subtotalTexto\n")
                y += LINE_H

                body.append("TEXT 7 0 $LEFT_X $y Total:\n")
                body.append("TEXT 7 0 $xTotal $y $totalTexto\n")
                y += LINE_H

                body.append("TEXT 7 0 $LEFT_X $y Vuelto:\n")
                body.append("TEXT 7 0 $xVuelto $y $vueltoTexto\n")
                y += 45

                // ===========================================================
                // QR CODE
                // ===========================================================
                body.append("SETMAG 1 1\n")
                body.append("TONE 3\n")

             val qrSize = 205

// 🔹 Escala del QR: controla el tamaño real impreso (1–6)
// U 4 → mediano, U 5 → grande
val qrScale = 5

// 🔹 Desplazamiento de compensación (depende del ancho del papel)
val adjust = when (PAGE_WIDTH) {
    in 550..600 -> 10     // impresora de 80 mm
    in 400..520 -> -5     // impresora de 58 mm
    else -> 0
}

// 🔹 Cálculo del centro real del QR
val qrX = ((PAGE_WIDTH - qrSize) / 2) + 40
                Log.d("PrinterService", "qr")

// 🔹 Impresión del QR (modo CPCL nativo)
body.append("B QR $qrX $y M 0 U $qrScale\n")
body.append("MA,https://admin.factura.gob.sv/consultaPublica?ambiente=$ambiente&codGen=$codGen&fechaEmi=$fechaQR\n")
body.append("ENDQR\n")

// 🔹 Espacio inferior antes del texto
y += (qrSize * (qrScale / 5.0)).toInt() + 29

                val text1 = "Consulta tu DTE escaneando el QR"
                val text2 = "Powered by SeedCodeSV"
                val text1X = 105
                val text2X = (PAGE_WIDTH / 2) - (text2.length * CHAR_WIDTH / 2)
                body.append("TEXT 7 0 $text1X $y $text1\n")
                y += 25
                body.append("TEXT 7 0 $text2X $y $text2\n")
                y += 30

                val pageHeight = y + 20
                val cpclCmd = buildString {
                    append("! 0 200 200 $pageHeight 1\n")
                    append("PAGE-WIDTH $PAGE_WIDTH\n")
                    append(body.toString())
                    append("PRINT\n")
                }

                // ===========================================================
                // ENVÍO A IMPRESORA
                // ===========================================================
                outputStream.write(cpclCmd.toByteArray(Charsets.ISO_8859_1))
                outputStream.flush()
                Log.d("PrinterService", "🖨️ Ticket enviado correctamente")
                promise.resolve("Impresión completada")

            } catch (e: Exception) {
                Log.e("PrinterService", "❌ Error al imprimir: ${e.message}", e)
                promise.reject("PRINT_ERROR", e.message, e)
            } finally {
                try { outputStream?.flush() } catch (_: Exception) {}
                try { outputStream?.close() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}
                Log.d("PrinterService", "🔻 Socket cerrado correctamente")
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


private var bluetoothStateReceiver: BroadcastReceiver? = null

@ReactMethod
fun startBluetoothStateListener() {
    val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
    bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_ON) {
                sendEvent("BluetoothStateChanged", true)
            } else if (state == BluetoothAdapter.STATE_OFF) {
                sendEvent("BluetoothStateChanged", false)
            }
        }
    }
    reactApplicationContext.registerReceiver(bluetoothStateReceiver, filter)
}

@ReactMethod
fun stopBluetoothStateListener() {
   bluetoothStateReceiver?.let { receiver ->
    reactApplicationContext.unregisterReceiver(receiver)
}
    bluetoothStateReceiver = null
}

private fun sendEvent(eventName: String, data: Boolean) {
    reactApplicationContext
        .getJSModule(com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit(eventName, data)
}


@ReactMethod
fun startBackgroundService(model: String, name: String, ticket: String, address_ip: String, promise: Promise) {
    try {
        val intent = Intent(reactApplicationContext, BluetoothPrinterService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            reactApplicationContext.startForegroundService(intent) // ✅ obligatorio desde Android 8
        } else {
            reactApplicationContext.startService(intent)
        }

        val dbHelper = PrinterDatabaseHelper(reactApplicationContext)
        if (!dbHelper.existsPrinterToday()) {
            dbHelper.insertPrinterInfo(model, name, ticket, address_ip)
        }
        promise.resolve("Servicio en segundo plano iniciado correctamente")
    } catch (e: Exception) {
        promise.reject("SERVICE_ERROR", e.message)
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
            map.putString("date", rec["date"] as String)
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
        try {
            val btAdapter = BluetoothAdapter.getDefaultAdapter()
            if (btAdapter == null) {
                promise.reject("NO_BLUETOOTH", "Este dispositivo no tiene Bluetooth")
                return@Thread
            }

            if (!btAdapter.isEnabled) {
                // Abrir configuración de Bluetooth en UI thread
                Handler(Looper.getMainLooper()).post {
                    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    reactApplicationContext.startActivity(intent)
                }
                promise.reject("BLUETOOTH_DISABLED", "El Bluetooth está apagado. Se abrió la configuración.")
                return@Thread
            }

            // Buscar la impresora emparejada por MAC
            val device: BluetoothDevice =
                btAdapter.bondedDevices.firstOrNull { it.address == "66:32:64:9A:65:3F" }
                    ?: throw Exception("Impresora no emparejada")

            val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            // Conexión segura
            socket = device.createRfcommSocketToServiceRecord(uuid)
            socket.connect()

            val outputStream = socket.outputStream
            val inputStream = socket.inputStream

            val commands = listOf(
                byteArrayOf(0x10, 0x04, 0x01), // batería
                byteArrayOf(0x10, 0x04, 0x02), // papel
                byteArrayOf(0x10, 0x04, 0x03)  // estado general
            )

            val info = Arguments.createMap()

            for (cmd in commands) {
                outputStream.write(cmd)
                outputStream.flush()

                // Leer respuesta con timeout seguro
                val buffer = ByteArray(256)
                val start = System.currentTimeMillis()
                var read = 0
                while (System.currentTimeMillis() - start < 2000 && read <= 0) { // 2 segundos max
                    val available = inputStream.available()
                    if (available > 0) {
                        read = inputStream.read(buffer, 0, available)
                    } else {
                        Thread.sleep(50)
                    }
                }

                if (read > 0) {
                    val resp = buffer.copyOf(read)
                    when (cmd[2].toInt()) {
                        0x01 -> info.putInt("battery", resp[0].toInt() and 0xFF)
                        0x02 -> info.putBoolean("paper", (resp[0].toInt() and 0x01) != 0)
                        0x03 -> {
                            info.putString("status", "OK")
                            info.putString("model", "MP806L")
                        }
                    }
                } else {
                    // Si no hay respuesta, marcar como desconectado
                    when (cmd[2].toInt()) {
                        0x01 -> info.putInt("battery", -1)
                        0x02 -> info.putBoolean("paper", false)
                        0x03 -> {
                            info.putString("status", "No response")
                            info.putString("model", "Unknown")
                        }
                    }
                }
            }

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
fun isServiceRunning(promise: Promise) {
    try {
        val context = reactApplicationContext
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val services = activityManager.getRunningServices(Int.MAX_VALUE)

        val isRunning = services.any { serviceInfo ->
            serviceInfo.service.className == "com.rafael_valladares.ticket_for_bluetooth.BluetoothPrinterService"
        }

        promise.resolve(isRunning)
    } catch (e: Exception) {
        promise.reject("SERVICE_CHECK_ERROR", e.message)
    }
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
        val activity = reactApplicationContext.currentActivity

        if (activity == null) {
            promise.reject("NO_ACTIVITY", "No hay actividad activa para abrir el diálogo.")
            return
        }

        // ✅ Verificar permisos nuevos desde Android 12 (API 31)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE
            )

            val missingPermissions = permissions.filter {
                ActivityCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
            }

            if (missingPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(activity, missingPermissions.toTypedArray(), 1001)
                promise.reject(
                    "PERMISSION_REQUIRED",
                    "Se necesitan permisos BLUETOOTH_CONNECT y BLUETOOTH_SCAN."
                )
                return
            }
        }

        // ✅ Intent para encender Bluetooth (solo si la Activity está activa)
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        activity.runOnUiThread {
            activity.startActivityForResult(intent, 1002)
        }

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