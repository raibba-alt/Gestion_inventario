package com.aramicro.gestioninventario

import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

class OpcionesActivity : BaseActivity() {

    private lateinit var rootLayout: LinearLayout
    private lateinit var tvTitulo: TextView
    private lateinit var tvSubTema: TextView
    private lateinit var etMesesCaducidad: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.WHITE)
        }

        tvTitulo = TextView(this).apply {
            text = "⚙️ Opciones"
            textSize = 24f
            setTextColor(Color.parseColor("#B71C1C"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
        }
        rootLayout.addView(tvTitulo)

        tvSubTema = TextView(this).apply {
            text = "Tema de Color:"
            textSize = 18f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        rootLayout.addView(tvSubTema)

        val layoutTemas = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 } }
        layoutTemas.addView(crearBoton("Blanco", Color.LTGRAY) { aplicarTema("blanco", recargarPantalla = true) })
        layoutTemas.addView(crearBoton("Rojo", Color.parseColor("#D32F2F")) { aplicarTema("rojo", recargarPantalla = true) })
        rootLayout.addView(layoutTemas)

        // --- NUEVA OPCIÓN DE CADUCIDAD ---
        val tvSubCaducidad = TextView(this).apply {
            text = "Aviso de Caducidad (meses):"
            textSize = 18f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        rootLayout.addView(tvSubCaducidad)

        val prefs = getSharedPreferences("ConfigInventario", MODE_PRIVATE)

        etMesesCaducidad = EditText(this).apply {
            hint = "Ej: 6"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(prefs.getString("caducidad_meses_aviso", "6"))
            setPadding(30, 20, 30, 20)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 40 }
        }
        rootLayout.addView(etMesesCaducidad)
        // --- FIN OPCIÓN CADUCIDAD ---

        val tvDanger = TextView(this).apply {
            text = "⚠️ ZONA DE PELIGRO"
            textSize = 18f
            setTextColor(Color.parseColor("#B71C1C"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 16 }
        }
        rootLayout.addView(tvDanger)

        val btnBorrar = Button(this).apply {
            text = "BORRAR TODOS LOS FICHEROS Y DATOS"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#D32F2F"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 180)
            setOnClickListener { confirmarBorradoTotal() }
        }
        rootLayout.addView(btnBorrar)

        scrollView.addView(rootLayout)
        setContentView(scrollView)

        aplicarTema(prefs.getString("tema_color", "blanco") ?: "blanco")
    }

    override fun onPause() {
        super.onPause()
        // Guardamos el valor de los meses al salir de la pantalla
        getSharedPreferences("ConfigInventario", MODE_PRIVATE).edit()
            .putString("caducidad_meses_aviso", etMesesCaducidad.text.toString())
            .apply()
    }

    private fun crearBoton(texto: String, colorFondo: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = texto; textSize = 14f; setBackgroundColor(colorFondo); setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, 150, 1f).apply { marginEnd = 12 }
            setOnClickListener { onClick() }
        }
    }

    private fun aplicarTema(tema: String, recargarPantalla: Boolean = false) {
        val colorFondo = if (tema == "rojo") Color.parseColor("#FFCDD2") else Color.WHITE
        val colorTexto = if (tema == "rojo") Color.parseColor("#B71C1C") else Color.BLACK

        rootLayout.setBackgroundColor(colorFondo)
        tvTitulo.setTextColor(colorTexto)
        tvSubTema.setTextColor(colorTexto)

        getSharedPreferences("ConfigInventario", MODE_PRIVATE).edit().putString("tema_color", tema).apply()
        if (recargarPantalla) recreate()
    }

    private fun confirmarBorradoTotal() {
        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500)
        toneGen.release()

        AlertDialog.Builder(this)
            .setTitle("⚠️ ADVERTENCIA EXTREMA")
            .setMessage("¿Está seguro de que desea ELIMINAR todos los ficheros generados?\n\nEsta acción no se puede deshacer.")
            .setPositiveButton("SÍ, BORRAR TODO") { _, _ -> borrarTodo() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun borrarTodo() {
        val archivosABorrar = listOf("INVENTARIO.csv", "noNix.csv", "desechados.csv", "NIXFARMA.CSV", "AUX1.txt", "AUX2.txt")
        var contador = 0
        for (nombre in archivosABorrar) {
            val archivo = java.io.File(filesDir, nombre)
            if (archivo.exists()) { archivo.delete(); contador++ }
        }
        Toast.makeText(this, "Sistema limpiado. $contador archivos eliminados.", Toast.LENGTH_LONG).show()
    }
}