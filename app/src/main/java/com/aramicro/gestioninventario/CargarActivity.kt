package com.aramicro.gestioninventario

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

class CargarActivity : BaseActivity() {

    private lateinit var pickerFarmacia: ActivityResultLauncher<Intent>
    private lateinit var pickerAux1: ActivityResultLauncher<Intent>
    private lateinit var pickerAux2: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cargar)

        pickerFarmacia = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) procesarArchivo(result.data?.data, "FARMACIA")
        }
        pickerAux1 = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) procesarArchivo(result.data?.data, "AUX1")
        }
        pickerAux2 = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) procesarArchivo(result.data?.data, "AUX2")
        }

        cargarPreferencias()

        findViewById<Button>(R.id.btnCargarFarmacia).setOnClickListener {
            seleccionarArchivo("text/csv", pickerFarmacia)
        }
        findViewById<Button>(R.id.btnCargarAux1).setOnClickListener {
            guardarPreferencias()
            seleccionarArchivo("*/*", pickerAux1)
        }
        findViewById<Button>(R.id.btnCargarAux2).setOnClickListener {
            guardarPreferencias()
            seleccionarArchivo("*/*", pickerAux2)
        }

        // Botón Exportar
        findViewById<Button>(R.id.btnExportarDescargas).setOnClickListener {
            exportarTodoADescargas()
        }
    }

    private fun seleccionarArchivo(mimeType: String, launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
        }
        launcher.launch(intent)
    }

    private fun procesarArchivo(uri: Uri?, tipo: String) {
        if (uri == null) return
        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                if (tipo == "FARMACIA") {
                    val archivo = File(filesDir, "NIXFARMA.CSV")
                    archivo.outputStream().use { out -> inputStream.copyTo(out) }
                    mostrarPreviaFarmacia(archivo)
                    Toast.makeText(this, "NIXFARMA.CSV guardado", Toast.LENGTH_SHORT).show()
                } else {
                    val texto = inputStream.bufferedReader().readText()
                    val esAux1 = (tipo == "AUX1")

                    if (esAux1) mostrarPreviaAux(texto, R.id.tvPrevAux1, true) else mostrarPreviaAux(texto, R.id.tvPrevAux2, false)

                    val nombreArchivo = if(esAux1) "AUX1.txt" else "AUX2.txt"
                    val archivo = File(filesDir, nombreArchivo)
                    archivo.writeText(texto)
                    Toast.makeText(this, "$tipo procesado", Toast.LENGTH_SHORT).show()
                }
                inputStream.close()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al leer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun mostrarPreviaFarmacia(archivo: File) {
        val tv = findViewById<TextView>(R.id.tvPrevFarmacia)
        tv.text = ""
        archivo.useLines { lines -> lines.take(5).forEach { tv.append("$it\n") } }
    }

    private fun mostrarPreviaAux(texto: String, idTextView: Int, esAux1: Boolean) {
        val tv = findViewById<TextView>(idTextView)
        tv.text = ""

        val iniC = if (esAux1) findViewById<EditText>(R.id.etAux1CodIni).text.toString().toIntOrNull() ?: 1 else findViewById<EditText>(R.id.etAux2CodIni).text.toString().toIntOrNull() ?: 1
        val finC = if (esAux1) findViewById<EditText>(R.id.etAux1CodFin).text.toString().toIntOrNull() ?: 10 else findViewById<EditText>(R.id.etAux2CodFin).text.toString().toIntOrNull() ?: 10
        val iniD = if (esAux1) findViewById<EditText>(R.id.etAux1DescIni).text.toString().toIntOrNull() ?: 11 else findViewById<EditText>(R.id.etAux2DescIni).text.toString().toIntOrNull() ?: 11
        val finD = if (esAux1) findViewById<EditText>(R.id.etAux1DescFin).text.toString().toIntOrNull() ?: 50 else findViewById<EditText>(R.id.etAux2DescFin).text.toString().toIntOrNull() ?: 50
        val iniE = if (esAux1) findViewById<EditText>(R.id.etAux1EanIni).text.toString().toIntOrNull() ?: 51 else findViewById<EditText>(R.id.etAux2EanIni).text.toString().toIntOrNull() ?: 51
        val finE = if (esAux1) findViewById<EditText>(R.id.etAux1EanFin).text.toString().toIntOrNull() ?: 64 else findViewById<EditText>(R.id.etAux2EanFin).text.toString().toIntOrNull() ?: 64

        texto.lines().take(5).forEach { linea ->
            val cod = if (linea.length >= finC) linea.substring(iniC - 1, finC) else ""
            val desc = if (linea.length >= finD) linea.substring(iniD - 1, finD) else ""
            val ean = if (linea.length >= finE) linea.substring(iniE - 1, finE) else ""
            tv.append("$cod | $desc | $ean\n")
        }
    }

    private fun guardarPreferencias() {
        val prefs = getSharedPreferences("ConfigInventario", MODE_PRIVATE)
        prefs.edit().apply {
            putString("aux1_cod", "${findViewById<EditText>(R.id.etAux1CodIni).text};${findViewById<EditText>(R.id.etAux1CodFin).text}")
            putString("aux1_desc", "${findViewById<EditText>(R.id.etAux1DescIni).text};${findViewById<EditText>(R.id.etAux1DescFin).text}")
            putString("aux1_ean", "${findViewById<EditText>(R.id.etAux1EanIni).text};${findViewById<EditText>(R.id.etAux1EanFin).text}")
            putString("aux1_prov", findViewById<EditText>(R.id.etAux1Prov).text.toString())

            putString("aux2_cod", "${findViewById<EditText>(R.id.etAux2CodIni).text};${findViewById<EditText>(R.id.etAux2CodFin).text}")
            putString("aux2_desc", "${findViewById<EditText>(R.id.etAux2DescIni).text};${findViewById<EditText>(R.id.etAux2DescFin).text}")
            putString("aux2_ean", "${findViewById<EditText>(R.id.etAux2EanIni).text};${findViewById<EditText>(R.id.etAux2EanFin).text}")
            putString("aux2_prov", findViewById<EditText>(R.id.etAux2Prov).text.toString())
            apply()
        }
    }

    private fun cargarPreferencias() {
        val prefs = getSharedPreferences("ConfigInventario", MODE_PRIVATE)

        aplicarPreferencia(prefs.getString("aux1_cod", "1;10"), R.id.etAux1CodIni, R.id.etAux1CodFin)
        aplicarPreferencia(prefs.getString("aux1_desc", "11;50"), R.id.etAux1DescIni, R.id.etAux1DescFin)
        aplicarPreferencia(prefs.getString("aux1_ean", "51;64"), R.id.etAux1EanIni, R.id.etAux1EanFin)
        findViewById<EditText>(R.id.etAux1Prov).setText(prefs.getString("aux1_prov", ""))

        aplicarPreferencia(prefs.getString("aux2_cod", "1;10"), R.id.etAux2CodIni, R.id.etAux2CodFin)
        aplicarPreferencia(prefs.getString("aux2_desc", "11;50"), R.id.etAux2DescIni, R.id.etAux2DescFin)
        aplicarPreferencia(prefs.getString("aux2_ean", "51;64"), R.id.etAux2EanIni, R.id.etAux2EanFin)
        findViewById<EditText>(R.id.etAux2Prov).setText(prefs.getString("aux2_prov", ""))
    }

    private fun aplicarPreferencia(valor: String?, idIni: Int, idFin: Int) {
        val partes = valor?.split(";")
        if (partes?.size == 2) {
            findViewById<EditText>(idIni).setText(partes[0])
            findViewById<EditText>(idFin).setText(partes[1])
        }
    }

    // --- FUNCIÓN EXPORTAR A DESCARGAS ---

    private fun exportarTodoADescargas() {
        val archivosAExportar = listOf("INVENTARIO.csv", "noNix.csv", "desechados.csv", "NIXFARMA.CSV", "AUX1.txt", "AUX2.txt")
        var exportados = 0

        for (nombreArchivo in archivosAExportar) {
            val archivoInterno = File(filesDir, nombreArchivo)
            if (archivoInterno.exists()) {
                try {
                    copiarADescargas(nombreArchivo, archivoInterno)
                    exportados++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (exportados > 0) {
            Toast.makeText(this, "✅ $exportados archivos exportados a Descargas", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "No hay archivos generados para exportar.", Toast.LENGTH_LONG).show()
        }
    }

    private fun copiarADescargas(nombreArchivo: String, archivoOrigen: File) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = contentResolver
            val valores = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, nombreArchivo)
                put(MediaStore.Downloads.MIME_TYPE, if (nombreArchivo.endsWith(".csv")) "text/csv" else "text/plain")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores) ?: return

            resolver.openOutputStream(uri)?.use { outputStream ->
                archivoOrigen.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            valores.clear()
            valores.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, valores, null, null)
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val destino = File(downloadsDir, nombreArchivo)
            archivoOrigen.copyTo(destino, overwrite = true)
        }
    }
}