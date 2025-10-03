package com.rafael_valladares.ticket_for_bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.facebook.react.bridge.*
import java.io.IOException
import java.io.OutputStream
import java.util.*

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

                Log.d("BluetoothPrinter", "Conectando a ${device.name} (${device.address})")

                val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()
                outputStream = socket.outputStream

                // 🔹 Inicializar impresora
                outputStream.write(byteArrayOf(0x1B, 0x40)) // ESC @

                // 🔹 Escribir texto simple
                val textToPrint = "$payload\n"
                outputStream.write(textToPrint.toByteArray(charset("CP437"))) // codificación compatible

                // 🔹 Avanzar papel 3 líneas
                outputStream.write(byteArrayOf(0x0A, 0x0A, 0x0A))

                // 🔹 Corte parcial
                outputStream.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00)) // GS V B 0

                outputStream.flush()
                promise.resolve("Impresión completada")
                Log.d("BluetoothPrinter", "✅ Impresión enviada")
            } catch (e: IOException) {
                Log.e("BluetoothPrinter", "IO Error: ${e.message}", e)
                promise.reject("IO_ERROR", e.message)
            } catch (e: Exception) {
                Log.e("BluetoothPrinter", "Error: ${e.message}", e)
                promise.reject("ERROR", e.message)
            } finally {
                try { outputStream?.close() } catch (ignored: Exception) {}
                try { socket?.close() } catch (ignored: Exception) {}
            }
        }.start()
    }
}
