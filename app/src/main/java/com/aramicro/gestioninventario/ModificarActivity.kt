package com.aramicro.gestioninventario

import android.app.AlertDialog
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileWriter

// Clase interna para manejar los datos limpios
data class InventarioItem(var codigo: String, var ean: String, var uds: String, var desc: String)

class ModificarActivity : BaseActivity() {

    private lateinit var listView: ListView
    private lateinit var tvContador: TextView
    private val listaReal = mutableListOf<InventarioItem>() // Lista lógica con objetos
    private val listaItems = mutableListOf<String>() // Lista visual para el ArrayAdapter
    private lateinit var adapter: android.widget.ArrayAdapter<String>
    private lateinit var archivoInventario: File

    // Mapa para relacionar Código Nacional -> Descripción
    private val mapaDescripciones = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_modificar)

        listView = findViewById(R.id.listViewModificar)
        tvContador = findViewById(R.id.tvContadorMod)
        val btnInsertar = findViewById<Button>(R.id.btnInsertarMod)
        val btnBorrarTodo = findViewById<Button>(R.id.btnBorrarTodoMod)

        archivoInventario = File(filesDir, "INVENTARIO.csv")

        adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1, listaItems)
        listView.adapter = adapter

        // Cargamos las descripciones de Nixfarma y luego el inventario
        cargarDescripcionesNixfarma()
        cargarDatos()

        btnInsertar.setOnClickListener {
            mostrarDialogo("", "", "", "", null)
        }
        btnBorrarTodo.setOnClickListener { confirmarBorrarTodo() }

        // Click corto para MODIFICAR
        listView.setOnItemClickListener { _, _, position, _ ->
            val item = listaReal[position]
            mostrarDialogo(item.codigo, item.ean, item.uds, item.desc, position)
        }

        // Click largo para ELIMINAR uno solo
        listView.setOnItemLongClickListener { _, _, position, _ ->
            eliminarElemento(position)
            true
        }
    }

    // --- LECTURA DE NIXFARMA PARA SACAR DESCRIPCIONES ---

    private fun cargarDescripcionesNixfarma() {
        mapaDescripciones.clear()
        try {
            val archivo = File(filesDir, "NIXFARMA.CSV")
            if (archivo.exists()) {
                archivo.readLines().drop(1).forEach { linea ->
                    val lineaLimpia = linea.replace("\"", "").replace("\uFEFF", "")
                    val partes = lineaLimpia.split(";")
                    // Posición 0 es el Código, Posición 1 es la Descripción
                    if (partes.size >= 2) {
                        mapaDescripciones[partes[0].trim()] = partes[1].trim()
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // --- CARGA Y VISUALIZACIÓN DEL INVENTARIO ---

    private fun cargarDatos() {
        listaReal.clear()
        listaItems.clear()
        if (archivoInventario.exists()) {
            try {
                archivoInventario.readLines().forEach { linea ->
                    if (linea.trim().isNotEmpty() && !linea.trim().startsWith("Codigo")) {
                        val partes = linea.split(";")
                        if (partes.size >= 3) {
                            val cod = partes[0].trim()
                            val ean = partes[1].trim()
                            val uds = partes[2].trim()

                            // Buscar la descripción en nuestro mapa
                            val desc = mapaDescripciones[cod] ?: "⚠️ No encontrada en NIXFARMA"

                            listaReal.add(InventarioItem(cod, ean, uds, desc))

                            // Formatear bonito para la lista visual
                            listaItems.add("Cód: $cod\n$desc\nEAN: $ean | Uds: $uds")
                        } else {
                            // Por si acaso el CSV está corrupto
                            listaItems.add(linea.trim())
                        }
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Error al leer INVENTARIO.csv", Toast.LENGTH_SHORT).show()
            }
        }
        adapter.notifyDataSetChanged()
        tvContador.text = "Total registros: ${listaReal.size}"
    }

    // --- GUARDADO EN FICHERO (Usando la lista real para no guardar basura visual) ---

    // --- GUARDADO EN FICHERO ---
    private fun guardarDatos() {
        try {
            FileWriter(archivoInventario).use { writer ->
                writer.write("Codigo;EAN13;Cantidad;Caducidad\n") // <--- CAMBIADO: Añadida columna Caducidad
                listaReal.forEach { item ->
                    // IMPORTANTE: Si tu InventarioItem no tiene caducidad, debes añadírsela.
                    // Por ahora dejaré un campo vacío para no romper tu data class actual,
                    // pero deberías añadir "var caducidad: String" a InventarioItem.
                    writer.write("${item.codigo};${item.ean};${item.uds};\n")
                }
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
        }
    }

    // --- DIÁLOGOS ---

    private fun mostrarDialogo(cod: String, ean: String, uds: String, desc: String, position: Int?) {
        val etCod = EditText(this).apply {
            hint = "Código Nacional"
            setText(cod)
        }
        val etEan = EditText(this).apply {
            hint = "EAN13"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(ean)
        }
        val etUds = EditText(this).apply {
            hint = "Unidades"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(uds)
        }

        // Campo de descripción (solo lectura para informar al usuario)
        val tvDesc = TextView(this).apply {
            text = "Descripción: $desc"
            setPadding(0, 30, 0, 0)
            setTextColor(android.graphics.Color.GRAY)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
            addView(etCod)
            addView(tvDesc)
            addView(etEan)
            addView(etUds)
        }

        val titulo = if (position != null) "Modificar Artículo" else "Insertar Artículo"

        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setView(container)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevoCod = etCod.text.toString().trim()
                val nuevoEan = etEan.text.toString().trim()
                val nuevasUds = etUds.text.toString().trim()

                if (nuevoCod.isNotEmpty() && nuevoEan.isNotEmpty()) {
                    // Buscamos la descripción del nuevo código por si han cambiado el código
                    val nuevaDesc = mapaDescripciones[nuevoCod] ?: "⚠️ No encontrada en NIXFARMA"
                    val textoVisual = "Cód: $nuevoCod\n$nuevaDesc\nEAN: $nuevoEan | Uds: $nuevasUds"

                    if (position != null) {
                        listaReal[position].codigo = nuevoCod
                        listaReal[position].ean = nuevoEan
                        listaReal[position].uds = nuevasUds
                        listaReal[position].desc = nuevaDesc
                        listaItems[position] = textoVisual
                    } else {
                        listaReal.add(InventarioItem(nuevoCod, nuevoEan, nuevasUds, nuevaDesc))
                        listaItems.add(textoVisual)
                    }
                    guardarDatos()
                    adapter.notifyDataSetChanged()
                    tvContador.text = "Total registros: ${listaReal.size}"
                } else {
                    Toast.makeText(this, "Código y EAN son obligatorios", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarElemento(position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Borrar Registro")
            .setMessage("¿Seguro que desea borrar este registro?\n\n${listaItems[position]}")
            .setPositiveButton("Sí") { _, _ ->
                listaReal.removeAt(position)
                listaItems.removeAt(position)
                guardarDatos()
                adapter.notifyDataSetChanged()
                tvContador.text = "Total registros: ${listaReal.size}"
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun confirmarBorrarTodo() {
        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500)
        toneGen.release()

        AlertDialog.Builder(this)
            .setTitle("⚠️ ADVERTENCIA EXTREMA")
            .setMessage("¿Está seguro de borrar TODOS los registros del inventario?\nEsta acción no se puede deshacer.")
            .setPositiveButton("Sí, borrar todo") { _, _ ->
                listaReal.clear()
                listaItems.clear()
                if (archivoInventario.exists()) archivoInventario.delete()
                adapter.notifyDataSetChanged()
                tvContador.text = "Total registros: 0"
                Toast.makeText(this, "Inventario borrado completamente", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}