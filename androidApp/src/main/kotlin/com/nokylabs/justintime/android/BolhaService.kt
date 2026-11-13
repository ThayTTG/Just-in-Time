package com.nokylabs.justintime.android

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
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.Chronometer
import android.widget.LinearLayout
import android.widget.TextView
import com.nokylabs.justintime.dominio.Item
import com.nokylabs.justintime.dominio.Status
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
        const val FECHADA = "android.bolha.fechada"
        private const val POS_X = "android.bolha.x"
        private const val POS_Y = "android.bolha.y"

        /** A tarefa que a bolha deve mostrar agora, ou null se ela deve sumir. */
        fun alvo(c: Context, itens: List<Item>): Item? {
            if (!c.tarefas.config().bolhaAtiva || !Settings.canDrawOverlays(c)) return null
            val t = itens.firstOrNull() ?: return null
            return if (t.ocorrencia.id == c.tarefas.preferencia(FECHADA)) null else t
        }

        fun sincronizar(c: Context, tarefas: List<Item>) {
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
    private var tarefaAtual: Item? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        rodando = true
        wm = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val agora = System.currentTimeMillis()
        val tarefas = this.tarefas.abertasAgora()
        // startForeground precisa ser chamado sempre, em até 5 s, mesmo se formos parar logo em seguida
        val notif = Vitrine.construirResumo(this, tarefas)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Vitrine.ID_RESUMO, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(Vitrine.ID_RESUMO, notif)

        val t = alvo(this, tarefas)
        if (t == null) { stopSelf(); return START_NOT_STICKY }
        Erros.proteger(this, "Bolha: mostrar") { mostrar(t, agora) }
        return START_STICKY
    }

    override fun onDestroy() {
        raiz?.let { runCatching { wm.removeView(it) } }
        raiz = null
        rodando = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Sem a bolha, o resumo volta a ser uma notificação comum (se estiver ligado)
        Erros.proteger(this, "Bolha: encerrar") { Vitrine.atualizar(this) }
        super.onDestroy()
    }

    // ---------------- Desenho ----------------

    private val densidade get() = resources.displayMetrics.density
    private fun px(v: Int) = (v * densidade).toInt()

    private var escala = 1f
    private lateinit var coluna: LinearLayout
    private lateinit var fechar: TextView
    private lateinit var fundo: GradientDrawable
    private lateinit var controles: LinearLayout
    private val esconderControles = Runnable { if (::controles.isInitialized) controles.visibility = View.GONE }

    private fun mostrar(t: Item, agora: Long) {
        if (raiz == null) criarBolha()
        tarefaAtual = t
        nome.text = t.rotulo
        val prazo = Vitrine.prazoMs(this, t)
        val restante = prazo - agora
        barra.setBackgroundColor(Vitrine.corDaBarra(t, restante))
        when {
            t.status == Status.AGUARDANDO || restante <= 0 -> mostrarInfo("Responder", Vitrine.COR_APP)
            restante > Vitrine.UM_DIA || t.tarefa.tipoPrazo.name == "DIA_INTEIRO" -> mostrarInfo(Vitrine.textoDoPrazo(t), Vitrine.COR_NEUTRA)
            else -> {
                crono.base = Vitrine.baseCrono(prazo) // mesma base do widget e do resumo
                crono.isCountDown = true
                crono.setTextColor(if (restante <= Vitrine.UMA_HORA) Vitrine.COR_CRITICA else Vitrine.COR_TEXTO)
                crono.start()
                crono.visibility = View.VISIBLE
                info.visibility = View.GONE
            }
        }
        // O tamanho pode ter mudado pelas Preferências
        val e = tarefas.config().bolhaTamanho
        if (e != escala) aplicarEscala(e)
    }

    private fun mostrarInfo(texto: String, cor: Int) {
        crono.stop()
        crono.visibility = View.GONE
        info.text = texto
        info.setTextColor(cor)
        info.visibility = View.VISIBLE
    }

    /** Ajusta todos os tamanhos da bolha a partir da escala (1.0 = normal). */
    private fun aplicarEscala(e: Float) {
        escala = e.coerceIn(0.7f, 1.8f)
        fun s(v: Int) = (v * densidade * escala).toInt()
        nome.textSize = 14f * escala; nome.maxWidth = s(190)
        crono.textSize = 16f * escala
        info.textSize = 13f * escala
        fechar.textSize = 16f * escala; fechar.setPadding(s(10), s(6), s(4), s(6))
        coluna.setPadding(s(10), 0, s(4), 0)
        raiz?.setPadding(s(12), s(8), s(8), s(8))
        fundo.cornerRadius = s(20).toFloat()
        barra.layoutParams = LinearLayout.LayoutParams(s(4), s(34))
        raiz?.let { if (it.isAttachedToWindow) wm.updateViewLayout(it, params) }
    }

    private fun mudarTamanho(delta: Float) {
        aplicarEscala(escala + delta)
        salvarTamanho()
        raiz?.removeCallbacks(esconderControles)
        raiz?.postDelayed(esconderControles, 4000)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun criarBolha() {
        barra = View(this)
        nome = TextView(this).apply {
            setTextColor(Vitrine.COR_TEXTO); typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        crono = Chronometer(this).apply { typeface = Typeface.MONOSPACE }
        info = TextView(this).apply { typeface = Typeface.DEFAULT_BOLD }
        coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(nome); addView(crono); addView(info)
        }
        fechar = TextView(this).apply {
            text = "✕"; setTextColor(Vitrine.COR_NEUTRA)
            contentDescription = "Fechar bolha"
            setOnClickListener {
                tarefaAtual?.let {
                    tarefas.gravarPreferencia(FECHADA, it.ocorrencia.id)
                }
                Vitrine.atualizar(this@BolhaService)
            }
        }
        val principal = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(barra); addView(coluna); addView(fechar)
        }
        // Botões de tamanho: aparecem ao segurar a bolha
        fun botaoTamanho(rotulo: String, delta: Float) = TextView(this).apply {
            text = rotulo; textSize = 15f; setTextColor(0xFF110E1A.toInt()); typeface = Typeface.DEFAULT_BOLD
            setPadding(px(14), px(6), px(14), px(6))
            background = GradientDrawable().apply { cornerRadius = px(14).toFloat(); setColor(Vitrine.COR_APP) }
            setOnClickListener { mudarTamanho(delta) }
        }
        controles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, px(8), 0, 0)
            visibility = View.GONE
            addView(botaoTamanho("A−", -0.15f))
            addView(View(this@BolhaService), LinearLayout.LayoutParams(px(10), 1))
            addView(botaoTamanho("A+", 0.15f))
        }
        fundo = GradientDrawable().apply { setColor(0xF01C1729.toInt()); setStroke(px(1), 0xFF342B4B.toInt()) }
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = fundo
            elevation = px(6).toFloat()
            addView(principal); addView(controles)
        }

        val x = tarefas.preferencia(POS_X)?.toIntOrNull() ?: px(16)
        val y = tarefas.preferencia(POS_Y)?.toIntOrNull() ?: px(160)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }

        raiz = r
        aplicarEscala(tarefas.config().bolhaTamanho)

        // Pinça com dois dedos: muda o tamanho
        val pinca = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean { aplicarEscala(escala * d.scaleFactor); return true }
            override fun onScaleEnd(d: ScaleGestureDetector) { salvarTamanho() }
        })

        // Um dedo: arrastar; toque curto abre o app; segurar mostra A− / A+
        var inicioX = 0; var inicioY = 0; var toqueX = 0f; var toqueY = 0f
        var arrastou = false; var pincou = false; var segurou = false
        val aoSegurar = Runnable {
            segurou = true
            controles.visibility = View.VISIBLE
            r.removeCallbacks(esconderControles); r.postDelayed(esconderControles, 4000)
        }
        r.setOnTouchListener { _, e ->
            pinca.onTouchEvent(e)
            if (e.pointerCount > 1 || pinca.isInProgress) {
                pincou = true; r.removeCallbacks(aoSegurar)
                return@setOnTouchListener true
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    inicioX = params.x; inicioY = params.y; toqueX = e.rawX; toqueY = e.rawY
                    arrastou = false; pincou = false; segurou = false
                    r.postDelayed(aoSegurar, 500)
                }
                MotionEvent.ACTION_MOVE -> if (!pincou) {
                    val dx = e.rawX - toqueX; val dy = e.rawY - toqueY
                    if (abs(dx) > px(6) || abs(dy) > px(6)) { arrastou = true; r.removeCallbacks(aoSegurar) }
                    if (arrastou) {
                        params.x = inicioX + dx.toInt(); params.y = inicioY + dy.toInt()
                        wm.updateViewLayout(r, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    r.removeCallbacks(aoSegurar)
                    when {
                        arrastou -> { tarefas.gravarPreferencia(POS_X, params.x.toString()); tarefas.gravarPreferencia(POS_Y, params.y.toString()) }
                        !pincou && !segurou -> abrirTarefa()
                    }
                }
                MotionEvent.ACTION_CANCEL -> r.removeCallbacks(aoSegurar)
            }
            true
        }
        wm.addView(r, params)
    }

    private fun abrirTarefa() {
        val t = tarefaAtual
        val i = if (t != null && t.status == Status.AGUARDANDO)
            AlarmeActivity.intent(this, t.ocorrencia.id)
        else Intent(this, MainActivity::class.java)
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Guarda o tamanho nas preferências (as Configurações mostram o mesmo valor). */
    private fun salvarTamanho() {
        val c = tarefas.config()
        if (c.bolhaTamanho != escala) tarefas.salvarConfig(c.copy(bolhaTamanho = escala))
    }
}
