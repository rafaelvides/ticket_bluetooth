package com.rafael_valladares.ticket_for_bluetooth

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrinterDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "printer_info.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_NAME = "printer"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_MODEL = "model"
        private const val COLUMN_TICKET = "ticket"
        private const val COLUMN_DATE = "date"
        private const val COLUMN_ADDRESS_IP = "address_ip"
        private const val COLUMN_SOCKET_URL = "socketUrl"
        private const val COLUMN_DOC = "doc"
        private const val COLUMN_ENVIROMENT = "enviroment"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT,
                $COLUMN_MODEL TEXT,
                $COLUMN_TICKET TEXT,
                $COLUMN_DATE TEXT,
                $COLUMN_ADDRESS_IP TEXT,
                $COLUMN_SOCKET_URL TEXT,
                $COLUMN_DOC TEXT,
                $COLUMN_ENVIROMENT TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertPrinterInfo(
    model: String,
    name: String,
    ticket: String,
    address_ip: String,
    socketUrl: String,
    doc: String,
    enviroment: String
    ): Long {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_MODEL, model)
            put(COLUMN_TICKET, ticket)
            put(COLUMN_NAME, name)
            put(COLUMN_ADDRESS_IP, address_ip)
            put(COLUMN_SOCKET_URL, socketUrl)
            put(COLUMN_DOC, doc)
            put(COLUMN_ENVIROMENT, enviroment)
            put(COLUMN_DATE, currentDate) // ← fecha legible
        }
        return db.insert(TABLE_NAME, null, values)
    }
    
fun existsPrinterByDoc(data: String): Boolean {
    val db = readableDatabase
    var exists = false

    try {
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM printer WHERE doc = ?",
            arrayOf(data)
        )

        cursor.use {
            if (it.moveToFirst()) {
                exists = it.getInt(0) > 0
            }
        }
    } catch (e: Exception) {
        Log.e("PrinterDB", "❌ Error verificando campo doc: ${e.message}")
    } finally {
        db.close()
    }

    return exists
}

fun deletePrinterById(id: Int): Boolean {
    val db = writableDatabase
    var deleted = false

    try {
        val rows = db.delete("printer", "id = ?", arrayOf(id.toString()))
        deleted = rows > 0
        if (deleted) {
            Log.d("PrinterDB", "🗑️ Registro eliminado correctamente (id=$id)")
        } else {
            Log.w("PrinterDB", "⚠️ No se encontró registro con id=$id para eliminar")
        }
    } catch (e: Exception) {
        Log.e("PrinterDB", "❌ Error al eliminar registro: ${e.message}", e)
    } finally {
        db.close()
    }

    return deleted
}

fun getSocketUrl(): String {
    val db = readableDatabase
    var url = "wss://facturacion-testmt-api.erpseedcodeone.online/sales-gateway" // 🔹 valor por defecto

    try {
        val cursor = db.rawQuery("SELECT socketUrl FROM printer ORDER BY id DESC LIMIT 1", null)
        cursor.use {
            if (it.moveToFirst()) {
                url = it.getString(0)
            }
        }
        Log.d("PrinterDB", "🌐 URL cargada desde la BD: $url")
    } catch (e: Exception) {
        Log.e("PrinterDB", "❌ Error al obtener la URL del socket: ${e.message}")
    } finally {
        db.close()
    }

    return url
}


    fun getAllPrinters(): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME", null)
        cursor.use {
            while (it.moveToNext()) {
                val item = mapOf(
                    "id" to it.getInt(0),
                    "model" to it.getString(1),
                    "ticket" to it.getString(2),
                    "address_ip" to it.getString(3),
                    "name" to it.getString(4),
                    "date" to it.getString(5),
                    "socketUrl" to it.getString(6),
                    "doc" to it.getString(7),
                    "enviroment" to it.getString(8),
                )
                list.add(item)
            }
        }
        return list
    }

    fun getLastPrinter(): Map<String, Any>? {
    val db = readableDatabase
    val cursor = db.rawQuery("SELECT * FROM printer ORDER BY id DESC LIMIT 1", null)
    var result: Map<String, Any>? = null

    cursor.use {
        if (it.moveToFirst()) {
            result = mapOf(
                "id" to it.getInt(it.getColumnIndexOrThrow("id")),
                "model" to it.getString(it.getColumnIndexOrThrow("model")),
                "name" to it.getString(it.getColumnIndexOrThrow("name")),
                "address_ip" to it.getString(it.getColumnIndexOrThrow("address_ip")),
                "ticket" to it.getString(it.getColumnIndexOrThrow("ticket")),
                "date" to it.getString(it.getColumnIndexOrThrow("date")),
                "socketUrl" to it.getString(it.getColumnIndexOrThrow("socketUrl")),
                "doc" to it.getString(it.getColumnIndexOrThrow("doc")),
                "enviroment" to it.getString(it.getColumnIndexOrThrow("enviroment")),
            )
        }
    }
    db.close()
    return result
}

   fun getAllPrinterDetails(): List<Map<String, Any>> {
        val list = mutableListOf<Map<String, Any>>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME ORDER BY id DESC", null)

        cursor.use {
            while (it.moveToNext()) {
                val item = mutableMapOf<String, Any>()
                item["id"] = it.getInt(it.getColumnIndexOrThrow("id"))
                item["model"] = it.getString(it.getColumnIndexOrThrow("model"))
                item["name"] = it.getString(it.getColumnIndexOrThrow("name"))
                item["date"] = it.getString(it.getColumnIndexOrThrow("date"))
                item["address_ip"] = it.getString(it.getColumnIndexOrThrow("address_ip"))
                item["ticket"] = it.getString(it.getColumnIndexOrThrow("ticket")).toIntOrNull() ?: 0
                item["socketUrl"] = it.getString(it.getColumnIndexOrThrow("socketUrl"))
                item["doc"] = it.getString(it.getColumnIndexOrThrow("doc"))
                item["enviroment"] = it.getString(it.getColumnIndexOrThrow("enviroment"))
                list.add(item)
            }
        }

        db.close()
        return list
    }


fun incrementTicket(id: Int): Int {
    val db = writableDatabase
    var newValue = 0

    db.beginTransaction()
    try {
        // 🔹 Leer valor actual
        val cursor = db.rawQuery("SELECT ticket FROM printer WHERE id = ?", arrayOf(id.toString()))
        var currentValue = 0
        cursor.use {
            if (it.moveToFirst()) {
                currentValue = it.getString(0)?.toIntOrNull() ?: 0
            }
        }

        // 🔹 Incrementar
        newValue = currentValue + 1
        val updateValues = ContentValues().apply {
            put("ticket", newValue.toString())
        }

        // 🔹 Actualizar registro
        val rows = db.update("printer", updateValues, "id = ?", arrayOf(id.toString()))
        Log.d("PrinterDB", "🎟️ Filas afectadas: $rows — Nuevo ticket: $newValue")

        // 🔹 Confirmar
        db.setTransactionSuccessful()
    } catch (e: Exception) {
        Log.e("PrinterDB", "❌ Error incrementando ticket: ${e.message}")
    } finally {
        db.endTransaction()
        db.close()
    }

    return newValue
}
fun updateRegister(
    id: Int,
    name: String,
    model: String,
    ticket: String,
    address_ip: String,
    sockect_url: String,
    doc: String,
    enviroment: String
): Int {
    val db = writableDatabase
    var rowsUpdated = 0

    try {
        db.beginTransaction() // ✅ paréntesis necesarios

        val updateValues = ContentValues().apply {
            put("name", name)
            put("model", model)
            put("ticket", ticket)
            put("address_ip", address_ip)
            put("socketUrl", sockect_url) // 👈 exactamente como en tu tabla
            put("doc", doc)
            put("enviroment", enviroment)
        }

        rowsUpdated = db.update(
            "printer",
            updateValues,
            "id = ?",
            arrayOf(id.toString())
        )

        if (rowsUpdated > 0) {
            Log.d("PrinterDB", "✅ Registro actualizado correctamente (id=$id)")
        } else {
            Log.w("PrinterDB", "⚠️ No se encontró registro con id=$id para actualizar")
        }

        db.setTransactionSuccessful()
    } catch (e: Exception) {
        Log.e("PrinterDB", "❌ Error al actualizar registro: ${e.message}", e)
    } finally {
        try {
            db.endTransaction() // ✅ sin parámetros
        } catch (_: Exception) { }

        db.close()
    }

    return rowsUpdated // ✅ retorno obligatorio
}

}
