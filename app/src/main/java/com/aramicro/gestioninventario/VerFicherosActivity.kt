package com.aramicro.gestioninventario

import android.graphics.Color
import android.os.Bundle
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class VerFicherosActivity : BaseActivity() {

    private lateinit var webView: WebView
    private lateinit var tvContador: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Construir la vista por código
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 40, 24, 24)
        }

        // Título
        val tvTitulo = TextView(this).apply {
            text = "📁 Visor de Ficheros"
            textSize = 24f
            setTextColor(Color.parseColor("#2C3E50"))
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 }
        }
        rootLayout.addView(tvTitulo)

        // Grid de Botones (2 filas de 3)
        val layoutBotones1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 } }
        val layoutBotones2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 } }

        layoutBotones1.addView(crearBoton("NIXFARMA", Color.parseColor("#3498DB")) { mostrarArchivo("NIXFARMA") })
        layoutBotones1.addView(crearBoton("AUX 1", Color.parseColor("#2ECC71")) { mostrarArchivo("AUX1") })
        layoutBotones1.addView(crearBoton("AUX 2", Color.parseColor("#2ECC71")) { mostrarArchivo("AUX2") })

        layoutBotones2.addView(crearBoton("INVENTARIO", Color.parseColor("#E67E22")) { mostrarArchivo("INVENTARIO") })
        layoutBotones2.addView(crearBoton("NO NIX", Color.parseColor("#F1C40F")) { mostrarArchivo("NO_NIX") })
        layoutBotones2.addView(crearBoton("DESECHADOS", Color.parseColor("#E74C3C")) { mostrarArchivo("DESECHADOS") })

        rootLayout.addView(layoutBotones1)
        rootLayout.addView(layoutBotones2)

        // Contador
        tvContador = TextView(this).apply {
            text = "Selecciona un fichero para ver su contenido"
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
        }
        rootLayout.addView(tvContador)

        // WebView para la tabla limpia
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setBackgroundColor(Color.TRANSPARENT)
        }
        rootLayout.addView(webView)

        setContentView(rootLayout)
    }

    // --- FUNCIONES AUXILIARES DE UI ---

    private fun crearBoton(texto: String, color: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = texto
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
            layoutParams = LinearLayout.LayoutParams(0, 140, 1f).apply { marginEnd = 6 }
            setOnClickListener { onClick() }
        }
    }

    // --- LÓGICA DE CARGA Y FORMATO HTML ---

    private fun mostrarArchivo(tipo: String) {
        val nombreArchivo = when(tipo) {
            "NIXFARMA" -> "NIXFARMA.CSV"
            "AUX1", "AUX2" -> "$tipo.txt"
            "INVENTARIO" -> "INVENTARIO.csv"
            "NO_NIX" -> "noNix.csv"
            "DESECHADOS" -> "desechados.csv"
            else -> ""
        }

        val archivo = File(filesDir, nombreArchivo)
        if (!archivo.exists()) {
            tvContador.text = "El archivo $nombreArchivo no existe."
            webView.loadDataWithBaseURL(null, "<html><body><h3 style='color:red;text-align:center;margin-top:50px;'>Fichero no encontrado.<br>Ve a 'Cargar Datos' para generarlo.</h3></body></html>", "text/html", "UTF-8", null)
            return
        }

        val lineas = archivo.readLines()
        tvContador.text = "$nombreArchivo: ${lineas.size} líneas"

        val html = when(tipo) {
            "NIXFARMA" -> generarHtmlNixfarma(lineas)
            "AUX1", "AUX2" -> generarHtmlAux(lineas, tipo)
            "INVENTARIO" -> generarHtmlInventario(lineas)
            "NO_NIX" -> generarHtmlNoNix(lineas)
            "DESECHADOS" -> generarHtmlDesechados(lineas)
            else -> ""
        }

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    // --- GENERADORES DE TABLA HTML ---

    private fun generarHtmlNixfarma(lineas: List<String>): String {
        val sb = StringBuilder()
        sb.append(estiloBase())
        sb.append("<table><tr><th>Código</th><th>Descripción</th><th>Caducidad</th><th>PVP</th><th>EAN13</th><th>Stock</th></tr>")

        for (linea in lineas.drop(1)) {
            val limpio = linea.replace("\"", "").replace("\uFEFF", "").trim()
            if (limpio.isEmpty()) continue
            var p = limpio.split(";")
            if (p.size < 8) p = limpio.split("\t") // Inteligente

            if (p.size >= 8) {
                sb.append("<tr><td>${p[0]}</td><td>${p[1]}</td><td>${p[2]}</td><td>${p[3]}</td><td>${p[4]}</td><td>${p[6]}</td></tr>")
            }
        }
        sb.append("</table></body></html>")
        return sb.toString()
    }

    private fun generarHtmlAux(lineas: List<String>, tipo: String): String {
        val prefs = getSharedPreferences("ConfigInventario", MODE_PRIVATE)
        val prefC = prefs.getString("${tipo.lowercase()}_cod", "1;10")?.split(";")
        val iniC = prefC?.get(0)?.toIntOrNull() ?: 1; val finC = prefC?.get(1)?.toIntOrNull() ?: 10
        val prefD = prefs.getString("${tipo.lowercase()}_desc", "11;50")?.split(";")
        val iniD = prefD?.get(0)?.toIntOrNull() ?: 11; val finD = prefD?.get(1)?.toIntOrNull() ?: 50
        val prefE = prefs.getString("${tipo.lowercase()}_ean", "51;64")?.split(";")
        val iniE = prefE?.get(0)?.toIntOrNull() ?: 51; val finE = prefE?.get(1)?.toIntOrNull() ?: 64

        val sb = StringBuilder()
        sb.append(estiloBase())
        sb.append("<table><tr><th>Código</th><th>Descripción</th><th>EAN13</th></tr>")

        for (linea in lineas) {
            try {
                if (linea.length >= finE) {
                    val cod = linea.substring(iniC - 1, finC).trim()
                    val desc = linea.substring(iniD - 1, finD).trim()
                    val ean = linea.substring(iniE - 1, finE).trim()
                    sb.append("<tr><td>$cod</td><td>$desc</td><td>$ean</td></tr>")
                }
            } catch (_: Exception) { }
        }
        sb.append("</table></body></html>")
        return sb.toString()
    }

    private fun generarHtmlInventario(lineas: List<String>): String {
        val sb = StringBuilder()
        sb.append(estiloBase())
        sb.append("<table><tr><th>Código</th><th>EAN13</th><th>Cantidad</th><th>Caducidad</th></tr>")

        for (linea in lineas) {
            val p = linea.split(";")
            if ((p.size >= 3) && !p[0].trim().equals("Codigo", ignoreCase = true)) {
                val cod = p[0].trim()
                val ean = if (p.size > 1) p[1].trim() else ""
                val uds = p[2].trim()
                val cad = if (p.size > 3) p[3].trim() else ""
                sb.append("<tr><td>$cod</td><td>$ean</td><td><b>$uds</b></td><td>$cad</td></tr>")
            }
        }
        sb.append("</table></body></html>")
        return sb.toString()
    }

    private fun generarHtmlNoNix(lineas: List<String>): String {
        val sb = StringBuilder()
        sb.append(estiloBase())
        sb.append("<table><tr><th>Código</th><th>Descripción</th><th>EAN13</th><th>Proveedor</th><th>Uds</th></tr>")

        for (linea in lineas) {
            val p = linea.split(";")
            if (p.size >= 6 && !p[0].trim().equals("Codigo", ignoreCase = true)) {
                sb.append("<tr><td>${p[0]}</td><td>${p[1]}</td><td>${p[2]}</td><td>${p[4]}</td><td><b>${p[5]}</b></td></tr>")
            }
        }
        sb.append("</table></body></html>")
        return sb.toString()
    }

    private fun generarHtmlDesechados(lineas: List<String>): String {
        val sb = StringBuilder()
        sb.append(estiloBase())
        sb.append("<table><tr><th>EAN Descartado</th><th>Uds</th><th>Cód. Último Leído</th><th>Desc. Último Leído</th><th>Fecha</th></tr>")

        for (linea in lineas) {
            val p = linea.split(";")
            if (p.size >= 5 && !p[0].trim().equals("EAN_Descartado", ignoreCase = true)) {
                sb.append("<tr><td>${p[0]}</td><td>${p[1]}</td><td>${p[2]}</td><td>${p[3]}</td><td>${p[4]}</td></tr>")
            }
        }
        sb.append("</table></body></html>")
        return sb.toString()
    }

    // --- ESTILO CSS BASE PARA LAS TABLAS ---
    private fun estiloBase(): String {
        return "<html><head><style>" +
                "body { font-family: Arial, sans-serif; margin: 10px; font-size: 14px; background-color: #FFFFFF; }" +
                "table { width: 100%; border-collapse: collapse; }" +
                "th { background-color: #2C3E50; color: white; padding: 10px; text-align: left; border: 1px solid #ddd; }" +
                "td { padding: 8px; border: 1px solid #ddd; }" +
                "tr:nth-child(even) { background-color: #f9f9f9; }" +
                "tr:hover { background-color: #f1f1f1; }" +
                "</style></head><body>"
    }
}