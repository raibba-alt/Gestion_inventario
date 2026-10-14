package com.aramicro.gestioninventario

import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.io.File

// --- DATA CLASSES ---
data class ArticuloFarmacia(
    val codigo: String, val descripcion: String, val caducidad: String,
    val pvp: String, val ean13: String, val ubicacion: String,
    val stock: String, val precioCosto: String
)

data class ArticuloAux(
    val codigo: String, val descripcion: String, val ean13: String
)

class LecturaActivity : BaseActivity() {

    // --- ELEMENTOS DE UI ---
    private lateinit var etEanInput: EditText
    private lateinit var tvStatusUnidades: TextView
    private lateinit var tvResultado: TextView
    private lateinit var etCodigoManual: EditText
    private lateinit var tvContadorTotal: TextView
    private lateinit var imm: InputMethodManager

    // --- VARIABLES DE ESTADO ---
    private var pendingUnits = 0
    private var waitingForEan = false
    private var lastRejectedEan: String? = null
    private var lastRejectedUds: Int = 0
    private var esTecladoPantalla = false

    // --- HEURÍSTICA DEL TIEMPO ---
    private var tiempoPrimeraLetra: Long = 0

    // --- ÚLTIMO LEÍDO CORRECTAMENTE ---
    private var ultimoCodLeidoOk: String = ""
    private var ultimoDescLeidoOk: String = ""

    // --- BASES DE DATOS EN MEMORIA ---
    private var farmaciaData = mutableListOf<ArticuloFarmacia>()
    private var aux1Data = mutableListOf<ArticuloAux>()
    private var aux2Data = mutableListOf<ArticuloAux>()

    private val inventarioEnMemoria = mutableMapOf<String, Int>()
    private val inventarioEans = mutableMapOf<String, String>()
    private val inventarioFechas = mutableMapOf<String, String>()
    private val contadorAuxSesion = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lectura)

        imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

        etEanInput = findViewById(R.id.etEanInput)
        tvStatusUnidades = findViewById(R.id.tvStatusUnidades)
        tvResultado = findViewById(R.id.tvResultado)
        etCodigoManual = findViewById(R.id.etCodigoManual)
        tvContadorTotal = findViewById(R.id.tvContadorTotal)

        val btnProcesar = findViewById<Button>(R.id.btnProcesar)
        val btnLimpiar = findViewById<Button>(R.id.btnLimpiar)
        val btnGuardarManual = findViewById<Button>(R.id.btnGuardarManual)

        cargarCSVFarmacia()
        cargarAuxiliar("AUX1")
        cargarAuxiliar("AUX2")
        cargarInventarioEnMemoria()
        actualizarContadorTotal()

        // 1. CRONÓMETRO: Anotamos cuándo se escribió la primera letra
        etEanInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (count > 0 && s?.length == 1) {
                    tiempoPrimeraLetra = System.currentTimeMillis()
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // 2. LISTENER UNIVERSAL: Captura el Intro del teclado Y el Intro del escáner
        etEanInput.setOnEditorActionListener { _, _, _ ->
            // Calculamos cuánto tardó en escribirse
            val tiempoTotal = System.currentTimeMillis() - tiempoPrimeraLetra

            // Si tardó más de 150ms, es un humano escribiendo. Si fue más rápido, es el escáner.
            esTecladoPantalla = tiempoTotal > 150

            // Ejecutamos la acción en el siguiente ciclo del procesador para no perder el foco
            etEanInput.post {
                procesarEntrada()
                etEanInput.requestFocus()
            }

            true // IMPORTANTE: Devolver true consume el evento y evita que se lie el foco
        }

        btnProcesar.setOnClickListener {
            esTecladoPantalla = true
            procesarEntrada()
            etEanInput.requestFocus()
        }

        btnLimpiar.setOnClickListener {
            imm.hideSoftInputFromWindow(etEanInput.windowToken, 0)
            etEanInput.text.clear()
            tvResultado.text = "Esperando escaneo..."
            tvResultado.setBackgroundColor(Color.WHITE)
            tvStatusUnidades.visibility = TextView.GONE
            pendingUnits = 0
            waitingForEan = false
            lastRejectedEan = null
            etCodigoManual.text.clear()
        }

        btnGuardarManual.setOnClickListener {
            if (lastRejectedEan != null) {
                val codigo = etCodigoManual.text.toString().trim()
                if (codigo.isNotEmpty()) {
                    guardarEnInventario(codigo, lastRejectedEan!!, lastRejectedUds)
                    tvResultado.text = "✅ MANUAL GUARDADO\nCódigo: $codigo\nLeído: $lastRejectedEan"
                    tvResultado.setBackgroundColor(Color.parseColor("#D4EDDA"))
                    etCodigoManual.text.clear()
                    lastRejectedEan = null
                } else {
                    Toast.makeText(this, "Introduzca un código", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- LÓGICA PRINCIPAL DE LECTURA ---

    private fun procesarEntrada() {
        val raw = etEanInput.text.toString().trim().replace("\n", "").replace("\r", "")
        if (raw.isEmpty()) return

        // Solo ocultamos el teclado si fue escrito por un humano
        if (esTecladoPantalla) {
            imm.hideSoftInputFromWindow(etEanInput.windowToken, 0)
        }

        etEanInput.text.clear()
        etEanInput.requestFocus()

        val valLong = raw.toLongOrNull()

        // 1. ¿Es un número menor a 1000? -> Unidades pendientes
        if (valLong != null && valLong < 1000) {
            // Si fue el escáner y es de 1 solo dígito, lo ignoramos (ruido)
            if (!esTecladoPantalla && valLong < 10) {
                return
            }

            pendingUnits = valLong.toInt()
            waitingForEan = true
            tvStatusUnidades.text = "⏳ Unidades pendientes: $pendingUnits — Ahora escanee el código"
            tvStatusUnidades.visibility = TextView.VISIBLE
            return
        }

        // 2. ¿Es un número largo? -> EAN13, EAN8 o Código Nacional
        if (valLong != null) {
            val uds = if (waitingForEan) pendingUnits else 1
            pendingUnits = 0
            waitingForEan = false
            tvStatusUnidades.visibility = TextView.GONE
            buscarEan(raw, uds, "")
            return
        }

        // 3. ¿Tiene letras o es muy largo? -> DataMatrix
        if (raw.length > 14 || !raw.all { it.isDigit() }) {
            val codigoEscaneado = extraerEanDeDatamatrix(raw)
            val fechaCaducidad = extraerFechaDeDatamatrix(raw)

            if (codigoEscaneado.isNotEmpty() && codigoEscaneado.all { it.isDigit() }) {
                val uds = if (waitingForEan) pendingUnits else 1
                pendingUnits = 0
                waitingForEan = false
                tvStatusUnidades.visibility = TextView.GONE
                buscarEan(codigoEscaneado, uds, fechaCaducidad)
                return
            } else {
                Toast.makeText(this, "Formato DataMatrix no reconocido", Toast.LENGTH_SHORT).show()
                return
            }
        }

        Toast.makeText(this, "Código ignorado", Toast.LENGTH_SHORT).show()
    }

    // --- CARGA DE DATOS ---

    private fun cargarCSVFarmacia() {
        try {
            val archivo = File(filesDir, "NIXFARMA.CSV")
            if (archivo.exists()) {
                val lineas = archivo.readLines()
                for (linea in lineas.drop(1)) {
                    val lineaLimpia = linea.replace("\"", "").replace("\uFEFF", "").trim()
                    if (lineaLimpia.isEmpty()) continue

                    var partes = lineaLimpia.split(";")
                    if (partes.size < 8) partes = lineaLimpia.split("\t")

                    if (partes.size >= 8) {
                        farmaciaData.add(ArticuloFarmacia(
                            partes[0].trim(), partes[1].trim(), partes[2].trim(),
                            partes[3].trim(), partes[4].trim(), partes[5].trim(),
                            partes[6].trim(), partes[7].trim()
                        ))
                    }
                }
                Toast.makeText(this, "CSV Cargado: ${farmaciaData.size} artículos", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "ERROR: No existe NIXFARMA.CSV", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun cargarAuxiliar(tipo: String) {
        try {
            val prefs = getSharedPreferences("ConfigInventario", MODE_PRIVATE)
            val prefIni = prefs.getString("${tipo.lowercase()}_ean", "51;64")?.split(";")
            val iniE = prefIni?.get(0)?.toIntOrNull() ?: 51
            val finE = prefIni?.get(1)?.toIntOrNull() ?: 64

            val prefCod = prefs.getString("${tipo.lowercase()}_cod", "1;10")?.split(";")
            val iniC = prefCod?.get(0)?.toIntOrNull() ?: 1
            val finC = prefCod?.get(1)?.toIntOrNull() ?: 10

            val prefDesc = prefs.getString("${tipo.lowercase()}_desc", "11;50")?.split(";")
            val iniD = prefDesc?.get(0)?.toIntOrNull() ?: 11
            val finD = prefDesc?.get(1)?.toIntOrNull() ?: 50

            val archivo = File(filesDir, "$tipo.txt")
            if (archivo.exists()) {
                val data = mutableListOf<ArticuloAux>()
                archivo.readLines().forEach { linea ->
                    if (linea.length >= finE) {
                        data.add(ArticuloAux(
                            linea.substring(iniC - 1, finC).trim(),
                            linea.substring(iniD - 1, finD).trim(),
                            linea.substring(iniE - 1, finE).trim()
                        ))
                    }
                }
                if (tipo == "AUX1") aux1Data = data else aux2Data = data
                Toast.makeText(this, "$tipo cargado: ${data.size} regs", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun cargarInventarioEnMemoria() {
        try {
            val archivo = File(filesDir, "INVENTARIO.csv")
            if (archivo.exists()) {
                archivo.readLines().forEach { linea ->
                    val partes = linea.split(";")
                    if (!partes[0].trim().equals("Codigo", ignoreCase = true)) {
                        val cod = partes[0].trim()
                        val uds = if (partes.size >= 3) partes[2].trim().toIntOrNull() ?: 0 else 0
                        val ean = if (partes.size >= 2) partes[1].trim() else ""
                        val fecha = if (partes.size >= 4) partes[3].trim() else ""

                        inventarioEnMemoria[cod] = (inventarioEnMemoria[cod] ?: 0) + uds
                        if (ean.isNotEmpty()) inventarioEans[cod] = ean
                        if (fecha.isNotEmpty()) inventarioFechas[cod] = fecha
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // --- BÚSQUEDA Y LÓGICA DE NEGOCIO ---

    private fun normalizarCodigo(cod: String): String {
        var limpio = cod.trim()
        if (limpio.length == 14 && limpio.startsWith("0")) limpio = limpio.substring(1)
        return limpio
    }

    private fun buscarEan(codigo: String, uds: Int, fechaCaducidad: String = "") {
        val codBuscado = normalizarCodigo(codigo)

        // 1. Buscar en FARMACIA
        val enFarmacia = farmaciaData.find {
            normalizarCodigo(it.ean13) == codBuscado || normalizarCodigo(it.codigo) == codBuscado
        }

        if (enFarmacia != null) {
            playSound(isLow = true)

            var codigoPrincipal = normalizarCodigo(enFarmacia.codigo)
            if (codigoPrincipal.isEmpty()) codigoPrincipal = codBuscado

            val udsQueTenia = inventarioEnMemoria[codigoPrincipal] ?: 0
            val totalAcumulado = udsQueTenia + uds

            tvResultado.text = "✅ ENCONTRADO EN FARMACIA\n\n" +
                    "Código: ${enFarmacia.codigo}\n" +
                    "Desc: ${enFarmacia.descripcion}\n" +
                    "EAN13: ${enFarmacia.ean13}\n" +
                    "PVP: ${enFarmacia.pvp}\n\n" +
                    "Leídas ahora: $uds uds\n" +
                    "➡️ TOTAL ACUMULADO: $totalAcumulado uds"
            tvResultado.setBackgroundColor(Color.parseColor("#D4EDDA"))

            if (fechaCaducidad.isNotEmpty()) inventarioFechas[codigoPrincipal] = fechaCaducidad

            guardarEnInventario(codigoPrincipal, codBuscado, uds, fechaCaducidad)

            ultimoCodLeidoOk = enFarmacia.codigo
            ultimoDescLeidoOk = enFarmacia.descripcion
            // --- CONTROL DE CADUCIDAD ---
            if (fechaCaducidad.isNotEmpty()) {
                val estadoCad = comprobarEstadoCaducidad(fechaCaducidad)
                if (estadoCad == 2) { // Caducado
                    playTripleBeep()
                    tvResultado.text = "🚫 ARTÍCULO CADUCADO\n\nCódigo: ${enFarmacia.codigo}\nDesc: ${enFarmacia.descripcion}\nCaducidad: $fechaCaducidad"
                    tvResultado.setBackgroundColor(Color.parseColor("#F8D7DA"))
                    bloquearEscaneo(2000) // Pausa de 2 segundos
                    return
                } else if (estadoCad == 1) { // Próximo a caducar
                    playDoubleBeep()
                    tvResultado.text = "⚠️ PRÓXIMO A CADUCAR\n\nCódigo: ${enFarmacia.codigo}\nDesc: ${enFarmacia.descripcion}\nCaducidad: $fechaCaducidad"
                    tvResultado.setBackgroundColor(Color.parseColor("#FFF3CD"))
                    bloquearEscaneo(2000) // Pausa de 2 segundos
                    return
                }
            }
            return
        }

        // 2. Buscar en AUX1
        val prefs = getSharedPreferences("ConfigInventario", MODE_PRIVATE)
        val prov1 = prefs.getString("aux1_prov", "AUXILIAR1") ?: "AUXILIAR1"
        val enAux1 = aux1Data.find { normalizarCodigo(it.ean13) == codBuscado || normalizarCodigo(it.codigo) == codBuscado }
        if (enAux1 != null) {
            playSound(isLow = true)

            contadorAuxSesion[enAux1.codigo] = (contadorAuxSesion[enAux1.codigo] ?: 0) + uds
            val totalAux = contadorAuxSesion[enAux1.codigo]!!

            tvResultado.text = "⚠️ NO ESTÁ EN FARMACIA (AUX 1)\n\n" +
                    "Código: ${enAux1.codigo}\n" +
                    "Desc: ${enAux1.descripcion}\n" +
                    "EAN13: ${enAux1.ean13}\n" +
                    "Proveedor: $prov1\n\n" +
                    "Leídas ahora: $uds uds\n" +
                    "➡️ TOTAL AUXILIAR: $totalAux uds"
            tvResultado.setBackgroundColor(Color.parseColor("#FFF3CD"))

            guardarEnNoNix(enAux1, "AUX1", prov1, uds)

            ultimoCodLeidoOk = enAux1.codigo
            ultimoDescLeidoOk = enAux1.descripcion
            lastRejectedEan = null
            return
        }

        // 3. Buscar en AUX2
        val prov2 = prefs.getString("aux2_prov", "AUXILIAR2") ?: "AUXILIAR2"
        val enAux2 = aux2Data.find { normalizarCodigo(it.ean13) == codBuscado || normalizarCodigo(it.codigo) == codBuscado }
        if (enAux2 != null) {
            playSound(isLow = true)

            contadorAuxSesion[enAux2.codigo] = (contadorAuxSesion[enAux2.codigo] ?: 0) + uds
            val totalAux = contadorAuxSesion[enAux2.codigo]!!

            tvResultado.text = "⚠️ NO ESTÁ EN FARMACIA (AUX 2)\n\n" +
                    "Código: ${enAux2.codigo}\n" +
                    "Desc: ${enAux2.descripcion}\n" +
                    "EAN13: ${enAux2.ean13}\n" +
                    "Proveedor: $prov2\n\n" +
                    "Leídas ahora: $uds uds\n" +
                    "➡️ TOTAL AUXILIAR: $totalAux uds"
            tvResultado.setBackgroundColor(Color.parseColor("#FFF3CD"))

            guardarEnNoNix(enAux2, "AUX2", prov2, uds)

            ultimoCodLeidoOk = enAux2.codigo
            ultimoDescLeidoOk = enAux2.descripcion
            lastRejectedEan = null
            return
        }

        // 4. NO ENCONTRADO EN NINGUNO -> DESCARTADOS
        playSound(isLow = false)
        val textoUltimo = if (ultimoCodLeidoOk.isNotEmpty())
            "Último leído OK:\n$ultimoCodLeidoOk - $ultimoDescLeidoOk"
        else
            "(Es el primer escaneo, no hay anterior)"

        tvResultado.text = "❌ NO ENCONTRADO EN NINGÚN FICHERO\n\nEscaneado: $codBuscado\nUds escaneadas: $uds\n\n$textoUltimo"
        tvResultado.setBackgroundColor(Color.parseColor("#F8D7DA"))

        guardarEnDesechados(codBuscado, uds, ultimoCodLeidoOk, ultimoDescLeidoOk)
        lastRejectedEan = codBuscado
        lastRejectedUds = uds
    }

    // --- GUARDADO EN FICHEROS ---

    private fun guardarEnInventario(codigoPrincipal: String, eanEscaneado: String, uds: Int, fechaCaducidad: String = "") {
        val udsTotales = (inventarioEnMemoria[codigoPrincipal] ?: 0) + uds
        inventarioEnMemoria[codigoPrincipal] = udsTotales
        inventarioEans[codigoPrincipal] = eanEscaneado

        if (fechaCaducidad.isNotEmpty()) inventarioFechas[codigoPrincipal] = fechaCaducidad

        try {
            val archivo = File(filesDir, "INVENTARIO.csv")
            var texto = "Codigo;EAN13;Cantidad;Caducidad\n"
            for ((cod, cantidad) in inventarioEnMemoria) {
                val ean = inventarioEans[cod] ?: ""
                val fecha = inventarioFechas[cod] ?: ""
                texto += "$cod;$ean;$cantidad;$fecha\n"
            }
            archivo.writeText(texto)
            actualizarContadorTotal()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun guardarEnNoNix(item: ArticuloAux, fuente: String, proveedor: String, uds: Int) {
        try {
            val archivo = File(filesDir, "noNix.csv")
            val agregarCabecera = !archivo.exists()
            archivo.appendText((if (agregarCabecera) "Codigo;Descripcion;EAN13;Fichero;Proveedor;Unidades\n" else "") + "${item.codigo};${item.descripcion};${item.ean13};$fuente;$proveedor;$uds\n")
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun guardarEnDesechados(ean: String, uds: Int, codAnterior: String, descAnterior: String) {
        try {
            val archivo = File(filesDir, "desechados.csv")
            val agregarCabecera = !archivo.exists()
            val fecha = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            archivo.appendText((if (agregarCabecera) "EAN_Descartado;Unidades;Cod_UltimoLeido;Desc_UltimoLeido;Fecha\n" else "") + "$ean;$uds;$codAnterior;$descAnterior;$fecha\n")
        } catch (e: Exception) { e.printStackTrace() }
    }

    // --- LÓGICA DATAMATRIX / GS1 ---

    private fun extraerFechaDeDatamatrix(raw: String): String {
        val limpio = raw.replace("[\\[\\](){}\\r\\n]".toRegex(), "")
        val regexFecha = "(?<=17)\\d{6}".toRegex()
        val matchFecha = regexFecha.find(limpio)

        if (matchFecha != null) {
            val fechaGS1 = matchFecha.value
            return try {
                val anio = "20${fechaGS1.substring(0, 2)}"
                val mes = fechaGS1.substring(2, 4)
                val dia = fechaGS1.substring(4, 6)
                "$dia/$mes/$anio"
            } catch (_: Exception) {
                matchFecha.value
            }
        }
        return ""
    }

    private fun extraerEanDeDatamatrix(raw: String): String {
        val limpio = raw.replace("[\\[\\](){}\\r\\n]".toRegex(), "")

        val regexGtin = "(?<=01)\\d{13,14}".toRegex()
        val matchGtin = regexGtin.find(limpio)

        if (matchGtin != null) {
            var gtin = matchGtin.value
            if (gtin.length == 14 && gtin.startsWith("0")) gtin = gtin.substring(1)
            return gtin
        }

        val regexEan13 = "\\d{13}".toRegex()
        regexEan13.find(limpio)?.value?.let { return it }

        val regexEan8 = "\\d{8}".toRegex()
        regexEan8.find(limpio)?.value?.let { return it }

        return raw
    }

    // --- CONTADOR EN PANTALLA ---
    private fun actualizarContadorTotal() {
        val totalUds = inventarioEnMemoria.values.sum()
        val totalArticulos = inventarioEnMemoria.size
        tvContadorTotal.text = "📦 En inventario: $totalUds uds ($totalArticulos artículos)"
    }

    // --- SONIDO ---

    private fun playSound(isLow: Boolean) {
        Thread {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_SYSTEM, 100)
                if (isLow) toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                else toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 500)
                Thread.sleep(600)
                toneGen.release()
            } catch (e: Exception) { e.printStackTrace() }
        }.start()
    }
    // --- LÓGICA DE CADUCIDAD ---

    private fun comprobarEstadoCaducidad(fechaStr: String): Int {
        if (fechaStr.isEmpty()) return 0 // 0 = OK, no hay fecha
        try {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            val fechaCad = sdf.parse(fechaStr) ?: return 0

            val hoyCal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val hoy = hoyCal.time

            // Si la fecha es anterior a hoy, está caducado
            if (fechaCad.before(hoy)) return 2

            // Comprobamos el límite de aviso
            val prefs = getSharedPreferences("ConfigInventario", MODE_PRIVATE)
            val mesesAviso = prefs.getString("caducidad_meses_aviso", "6")?.toIntOrNull() ?: 6

            val fechaLimiteCal = java.util.Calendar.getInstance().apply {
                time = hoy
                add(java.util.Calendar.MONTH, mesesAviso)
            }
            val fechaLimite = fechaLimiteCal.time

            // Si la fecha es igual o anterior a la fecha límite, está próximo a caducar
            if (!fechaCad.after(fechaLimite)) return 1

        } catch (e: Exception) { e.printStackTrace() }
        return 0 // OK
    }

    private fun playDoubleBeep() {
        Thread {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_SYSTEM, 100)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                Thread.sleep(250)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                Thread.sleep(450)
                toneGen.release()
            } catch (_: Exception) {}
        }.start()
    }

    private fun playTripleBeep() {
        Thread {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_SYSTEM, 100)
                toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 200)
                Thread.sleep(250)
                toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 200)
                Thread.sleep(250)
                toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 400)
                Thread.sleep(600)
                toneGen.release()
            } catch (_: Exception) {}
        }.start()
    }

    private fun bloquearEscaneo(milisegundos: Long) {
        etEanInput.isEnabled = false // Bloquea el campo de texto
        Handler(Looper.getMainLooper()).postDelayed({
            etEanInput.isEnabled = true // Lo vuelve a habilitar tras X segundos
            etEanInput.requestFocus()
        }, milisegundos)
    }
}