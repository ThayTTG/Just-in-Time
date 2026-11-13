package com.nokylabs.justintime.prototipo

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Chronometer
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Bolha flutuante com a próxima tarefa e sua contagem regressiva.
 *
 * - Fica por cima dos outros apps (permissão "Aparecer sobre outros apps").
 * - Pode ser arrastada; a posição é lembrada.
 * - O ✕ esconde a bolha até a próxima tarefa mudar.
 * - Roda num serviço em primeiro plano para o Android não encerrá-la;
 *   a notificação desse serviço é o próprio resumo das tarefas.
 */
class BolhaService : Service() {

    companion object {
        @Volatile var rodando = false
            private set

        /** A tarefa que a bolha deve mostrar agora, ou null se ela deve sumir. */
        fun alvo(c: Context, tarefas: List<Tarefa>): Tarefa? {
            if (!Store.bolhaAtiva(c) || !Settings.canDrawOverlays(c)) return null
            val t = tarefas.firstOrNull() ?: return null
            return if (t.id == Store.bolhaFechadaPara(c)) null else t
        }

        fun sincronizar(c: Context, tarefas: List<Tarefa>) {
            val i = Intent(c, BolhaService::class.java)
            when {
                alvo(c, tarefas) == null -> if (rodando) c.stopService(i)
                rodando -> c.startService(i)
                else -> c.startForegroundService(i)
            }
        }
    }

    private lateinit var wm: WindowManager
    private var raiz: LinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var barra: View
    private lateinit var nome: TextView
    private lateinit var crono: Chronometer
    private lateinit var info: TextView
    private var tarefaAtual: Tarefa? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        rodando = true
        wm = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val agora = System.currentTimeMillis()
        val tarefas = Store.abertasOrdenadas(this)
        // startForeground precisa ser chamado sempre, em até 5 s, mesmo se formos parar logo em seguida
        val notif = Notificador.construirResumo(this, tarefas, agora)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notificador.ID_RESUMO, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(Notificador.ID_RESUMO, notif)

        val t = alvo(this, tarefas)
        if (t == null) { stopSelf(); return START_NOT_STICKY }
        Relatorio.proteger(this, "Bolha: mostrar") { mostrar(t, agora) }
        return START_STICKY
    }

    override fun onDestroy() {
        raiz?.let { runCatching { wm.removeView(it) } }
        raiz = null
        rodando = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Sem a bolha, o resumo volta a ser uma notificação comum (se estiver ligado)
        Relatorio.proteger(this, "Bolha: encerrar") {
            Notificador.atualizarResumo(this, Store.abertasOrdenadas(this), System.currentTimeMillis())
        }
        super.onDestroy()
    }

    // ---------------- Desenho ----------------

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun mostrar(t: Tarefa, agora: Long) {
        if (raiz == null) criarBolha()
        tarefaAtual = t
        nome.text = t.titulo
        val restante = t.prazo - agora
        when {
            t.status == Status.AGUARDANDO || restante <= 0 -> {
                barra.setBackgroundColor(Vitrine.COR_RESPONDER)
                mostrarInfo("Responder", Vitrine.COR_RESPONDER)
            }
            restante > Vitrine.UM_DIA -> {
                barra.setBackgroundColor(Vitrine.COR_NEUTRA)
                mostrarInfo(Store.amigavel(t.prazo), Vitrine.COR_NEUTRA)
            }
            else -> {
                val critico = restante <= Vitrine.UMA_HORA
                barra.setBackgroundColor(if (critico) Vitrine.COR_CRITICA else Vitrine.COR_ATENCAO)
                crono.base = SystemClock.elapsedRealtime() + restante
                crono.isCountDown = true
                crono.setTextColor(if (critico) Vitrine.COR_CRITICA else Vitrine.COR_BRANCA)
                crono.start()
                crono.visibility = View.VISIBLE
                info.visibility = View.GONE
            }
        }
    }

    private fun mostrarInfo(texto: String, cor: Int) {
        crono.stop()
        crono.visibility = View.GONE
        info.text = texto
        info.setTextColor(cor)
        info.visibility = View.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun criarBolha() {
        barra = View(this)
        nome = TextView(this).apply {
            setTextColor(Vitrine.COR_BRANCA); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            maxWidth = px(190); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        crono = Chronometer(this).apply { textSize = 15f; typeface = Typeface.MONOSPACE }
        info = TextView(this).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD }
        val coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(10), 0, px(4), 0)
            addView(nome); addView(crono); addView(info)
        }
        val fechar = TextView(this).apply {
            text = "✕"; textSize = 16f; setTextColor(Vitrine.COR_NEUTRA)
            setPadding(px(10), px(6), px(4), px(6))
            contentDescription = "Fechar bolha"
            setOnClickListener {
                tarefaAtual?.let {
                    Store.fecharBolhaPara(this@BolhaService, it.id)
                    Store.log(this@BolhaService, "Bolha fechada para \"${it.titulo}\"")
                }
                Vitrine.atualizarTudo(this@BolhaService)
            }
        }
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(12), px(8), px(8), px(8))
            background = GradientDrawable().apply {
                cornerRadius = px(20).toFloat()
                setColor(0xEE1E1B2E.toInt())
            }
            elevation = px(6).toFloat()
            addView(barra, LinearLayout.LayoutParams(px(4), px(32)))
            addView(coluna)
            addView(fechar)
        }

        val (x, y) = Store.posicaoBolha(this)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }

        // Arrastar a bolha; um toque curto abre o app
        var inicioX = 0; var inicioY = 0; var toqueX = 0f; var toqueY = 0f; var arrastou = false
        r.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    inicioX = params.x; inicioY = params.y; toqueX = e.rawX; toqueY = e.rawY; arrastou = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - toqueX; val dy = e.rawY - toqueY
                    if (abs(dx) > px(6) || abs(dy) > px(6)) arrastou = true
                    if (arrastou) {
                        params.x = inicioX + dx.toInt(); params.y = inicioY + dy.toInt()
                        wm.updateViewLayout(r, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (arrastou) Store.salvarPosicaoBolha(this, params.x, params.y) else abrirTarefa()
                    true
                }
                else -> false
            }
        }
        wm.addView(r, params)
        raiz = r
    }

    private fun abrirTarefa() {
        val t = tarefaAtual
        val i = if (t != null && t.status == Status.AGUARDANDO)
            Intent(this, AlarmActivity::class.java).putExtra(Agendador.EXTRA_ID, t.id)
        else Intent(this, MainActivity::class.java)
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
