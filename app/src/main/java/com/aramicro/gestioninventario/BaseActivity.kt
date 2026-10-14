package com.aramicro.gestioninventario

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

abstract class BaseActivity : AppCompatActivity() {

    private var margenAplicadoCandado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        aplicarColorGlobal()
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        aplicarColorGlobal()
    }

    private fun aplicarColorGlobal() {
        val prefs = getSharedPreferences("ConfigInventario", MODE_PRIVATE)
        val tema = prefs.getString("tema_color", "blanco") ?: "blanco"

        val colorFondo = if (tema == "rojo") Color.parseColor("#FFCDD2") else Color.WHITE

        val rootView = (findViewById<View>(android.R.id.content) as? ViewGroup)?.getChildAt(0)
        rootView?.setBackgroundColor(colorFondo)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if ((rootView != null) && !margenAplicadoCandado) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, windowInsets ->
                if (!margenAplicadoCandado) {
                    margenAplicadoCandado = true
                    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                    v.setPadding(
                        v.paddingLeft,
                        v.paddingTop + insets.top,
                        v.paddingRight,
                        v.paddingBottom + insets.bottom,
                    )
                }
                WindowInsetsCompat.CONSUMED
            }
        }
    }
}