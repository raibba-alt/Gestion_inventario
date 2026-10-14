package com.aramicro.gestioninventario

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#D32F2F"))
            setPadding(24, 16, 16, 16)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val tituloToolbar = TextView(this).apply {
            text = "  Gestión de Inventario"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnOpciones = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_more)
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { mostrarMenuOpciones(it) }
        }

        toolbar.addView(tituloToolbar)
        toolbar.addView(btnOpciones)
        rootLayout.addView(toolbar)

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 80, 60, 40)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        contentLayout.addView(crearBoton("Lectura", Color.parseColor("#D32F2F")) {
            startActivity(android.content.Intent(this, LecturaActivity::class.java))
        })
        contentLayout.addView(crearBoton("Modificar", Color.parseColor("#D32F2F")) {
            startActivity(android.content.Intent(this, ModificarActivity::class.java))
        })
        contentLayout.addView(crearBoton("Cargar Datos", Color.parseColor("#D32F2F")) {
            startActivity(android.content.Intent(this, CargarActivity::class.java))
        })
        contentLayout.addView(crearBoton("Ver Ficheros", Color.parseColor("#D32F2F")) {
            startActivity(android.content.Intent(this, VerFicherosActivity::class.java))
        })

        val espacio = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 60)
        }
        contentLayout.addView(espacio)

        contentLayout.addView(crearBoton("Salir", Color.parseColor("#E0E0E0"), Color.parseColor("#B71C1C")) {
            finish()
        })

        rootLayout.addView(contentLayout)
        setContentView(rootLayout)
    }

    private fun crearBoton(texto: String, colorFondo: Int, colorTexto: Int = Color.WHITE, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = texto
            textSize = 18f
            setBackgroundColor(colorFondo)
            setTextColor(colorTexto)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 30
            }
            setOnClickListener { onClick() }
        }
    }

    private fun mostrarMenuOpciones(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Opciones")

        popup.setOnMenuItemClickListener { item: MenuItem ->
            if (item.title.toString() == "Opciones") {
                startActivity(android.content.Intent(this, OpcionesActivity::class.java))
            }
            true
        }
        popup.show()
    }
}