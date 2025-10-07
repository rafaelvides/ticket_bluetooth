package com.rafael_valladares.ticket_for_bluetooth

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

class PrinterDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "printer_info.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "printer"
        private const val COLUMN_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_MODEL = "model"
        private const val COLUMN_TICKET = "ticket"
        private const val COLUMN_ADDRESS_IP = "address_ip"

    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT,
                $COLUMN_MODEL TEXT,
                $COLUMN_TICKET TEXT,
                $COLUMN_ADDRESS_IP TEXT
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
        ticket: String, 
        address_ip: String,
        name: String
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_MODEL, model)
            put(COLUMN_TICKET, ticket)
            put(COLUMN_TICKET, name)
            put(COLUMN_ADDRESS_IP, address_ip)

        }
        return db.insert(TABLE_NAME, null, values)
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
                    "ticket" to it.getString(4),
                    "address_ip" to it.getString(5),
                    "name" to it.getString(6),
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
                item["address_ip"] = it.getString(it.getColumnIndexOrThrow("address_ip"))
                item["ticket"] = it.getString(it.getColumnIndexOrThrow("ticket")).toIntOrNull() ?: 0
                list.add(item)
            }
        }

        db.close()
        return list
    }


fun incrementTicket(id: Int): Int {
    val db = writableDatabase

    // Obtener valor actual del ticket como string y convertir a Int
    val cursor = db.rawQuery("SELECT ticket FROM printer WHERE id = ?", arrayOf(id.toString()))
    var currentValue = 0
    cursor.use {
        if (it.moveToFirst()) {
            currentValue = it.getString(0).toIntOrNull() ?: 0
        }
    }

    val newValue = currentValue + 1
    val updateValues = ContentValues().apply {
        put("ticket", newValue.toString()) // almacenamos como texto
    }
    db.update("printer", updateValues, "id = ?", arrayOf(id.toString()))

    db.close()
    return newValue
}
}
