package com.nokylabs.justintime.prototipo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference

/** Tela de alarme que aparece por cima da tela de bloqueio. */
class AlarmActivity : Activity() {

    companion object {
        private var atual: WeakReference<AlarmActivity>? = null
        fun fecharSeAberta(@Suppress("UNUSED_PARAMETER") c: Context) {
            atual?.get()?.finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        atual = WeakReference(this)
        // Acender a tela e aparecer sobre o bloqueio (Android 8.1+ usa os métodos; 8.0 usa as flags)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        montarTela(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        montarTela(intent)
    }

    override fun onDestroy() {
        if (atual?.get() === this) atual = null
        super.onDestroy()
    }

    private fun montarTela(intent: Intent) {
        val id = intent.getIntExtra(Agendador.EXTRA_ID, -1)
        val t = Store.get(this, id)
        if (t == null || !t.aberta) { finish(); return }

        val dp = resources.displayMetrics.density
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1E1B2E"))
            setPadding((32 * dp).toInt(), (48 * dp).toInt(), (32 * dp).toInt(), (48 * dp).toInt())
        }
        fun texto(s: String, tam: Float, cor: String, negrito: Boolean = false) = TextView(this).apply {
            text = s; textSize = tam; setTextColor(Color.parseColor(cor)); gravity = Gravity.CENTER
            if (negrito) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }
        raiz.addView(texto("O prazo chegou", 18f, "#B9B4D6").apply { if (t.cor != 0) setTextColor(t.cor) })
        raiz.addView(texto(t.rotulo, 30f, "#FFFFFF", negrito = true))
        raiz.addView(texto("Prazo: ${Store.amigavel(t.prazo)}", 16f, "#B9B4D6"))
        if (t.disparouEm > 0) {
            val atraso = (t.disparouEm - t.prazo) / 1000.0
            raiz.addView(texto("Atraso do alarme: ${"%.1f".format(atraso)} s", 14f, "#8E89AD"))
        }
        raiz.addView(texto("Você fez?", 22f, "#FFFFFF"))

        fun botao(rotulo: String, cor: String, acao: String) = Button(this).apply {
            text = rotulo; textSize = 18f; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(cor))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (64 * dp).toInt()
            ).apply { topMargin = (12 * dp).toInt() }
            setOnClickListener {
                sendBroadcast(
                    Intent(this@AlarmActivity, AlarmReceiver::class.java)
                        .setAction(acao).putExtra(Agendador.EXTRA_ID, id)
                )
                finish()
            }
        }
        raiz.addView(botao("Feito", "#2E7D32", Notificador.ACAO_FEITO))
        raiz.addView(botao("Não feito", "#C62828", Notificador.ACAO_NAO_FEITO))
        raiz.addView(botao("Adiar 5 min", "#5E35B1", Notificador.ACAO_ADIAR))
        setContentView(raiz)
    }
}
