package com.nokylabs.justintime.prototipo

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Tela única do protótipo: permissões, criar tarefa de teste, lista de tarefas e log.
 * (A interface é montada em código para o protótipo ficar curto; no app final será Compose.)
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var caixaPermissoes: LinearLayout
    private lateinit var caixaTarefas: LinearLayout
    private lateinit var textoLog: TextView
    private lateinit var campoNome: EditText
    private lateinit var campoMinutos: EditText
    private val dp by lazy { resources.displayMetrics.density }

    private val relogio = object : Runnable {
        override fun run() {
            atualizarTarefas()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notificador.criarCanais(this)
        Agendador.reagendarTudo(this, "app aberto")
        montarTela()
    }

    override fun onResume() {
        super.onResume()
        atualizarPermissoes()
        atualizarLog()
        handler.post(relogio)
    }

    override fun onPause() {
        handler.removeCallbacks(relogio)
        super.onPause()
    }

    // ---------------- Montagem da tela ----------------

    private fun px(v: Int) = (v * dp).toInt()

    private fun titulo(s: String) = TextView(this).apply {
        text = s; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.parseColor("#1E1B2E"))
        setPadding(0, px(20), 0, px(6))
    }

    private fun texto(s: String, tam: Float = 14f, cor: String = "#4A4560") = TextView(this).apply {
        text = s; textSize = tam; setTextColor(Color.parseColor(cor))
    }

    private fun botao(s: String, aoClicar: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; setOnClickListener { aoClicar() }
    }

    private fun montarTela() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(18), px(8), px(18), px(32))
        }

        col.addView(texto("Protótipo de risco: alarme exato, tela de alarme no bloqueio, " +
            "notificação com respostas e widget com contagem regressiva.", 13f))

        col.addView(titulo("1. Permissões"))
        caixaPermissoes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(caixaPermissoes)

        col.addView(titulo("2. Nova tarefa de teste"))
        campoNome = EditText(this).apply { hint = "Nome da tarefa"; setText("Tarefa de teste") }
        col.addView(campoNome)
        campoMinutos = EditText(this).apply {
            hint = "Prazo daqui a quantos minutos?"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("2")
        }
        col.addView(campoMinutos)
        val linhaBotoes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        linhaBotoes.addView(botao("Agendar") {
            val min = campoMinutos.text.toString().toLongOrNull() ?: 0
            if (min <= 0) toast("Digite os minutos") else criarTarefa(min * 60_000L)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        linhaBotoes.addView(botao("Em 30 s") { criarTarefa(30_000L) }, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(linhaBotoes)

        col.addView(titulo("3. Tarefas"))
        caixaTarefas = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(caixaTarefas)

        col.addView(titulo("4. Log de diagnóstico"))
        col.addView(texto("O atraso de cada alarme aparece aqui. Use \"Copiar log\" e cole na conversa com o Claude.", 12f))
        val linhaLog = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        linhaLog.addView(botao("Copiar log") { copiarLog() }, LinearLayout.LayoutParams(0, -2, 1f))
        linhaLog.addView(botao("Limpar log") { Store.limparLog(this); atualizarLog() }, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(linhaLog)
        textoLog = texto("", 12f, "#2B2840").apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(px(10), px(10), px(10), px(10))
            setBackgroundColor(Color.parseColor("#F1EFF8"))
        }
        col.addView(textoLog)

        setContentView(ScrollView(this).apply { addView(col) })
    }

    // ---------------- Permissões ----------------

    private fun linhaPermissao(nome: String, ok: Boolean, explicacao: String, acao: (() -> Unit)?) {
        val linha = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(4), 0, px(4))
        }
        val textos = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textos.addView(texto((if (ok) "✅ " else "⚠️ ") + nome, 15f, "#1E1B2E"))
        textos.addView(texto(explicacao, 12f))
        linha.addView(textos, LinearLayout.LayoutParams(0, -2, 1f))
        if (!ok && acao != null) linha.addView(botao("Liberar") { acao() })
        caixaPermissoes.addView(linha)
    }

    private fun atualizarPermissoes() {
        caixaPermissoes.removeAllViews()

        linhaPermissao(
            "Notificações", Notificador.podeNotificar(this),
            "Sem isso o alarme não aparece nem toca."
        ) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
            else abrir(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }

        linhaPermissao(
            "Alarmes exatos", Agendador.podeAgendarExato(this),
            "Para tocar no segundo certo, mesmo com o celular em repouso."
        ) {
            if (Build.VERSION.SDK_INT >= 31) abrir(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }

        linhaPermissao(
            "Tela cheia no bloqueio", Notificador.podeTelaCheia(this),
            "Para a tela de alarme acender o celular bloqueado."
        ) {
            if (Build.VERSION.SDK_INT >= 34) abrir(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        }

        val pm = getSystemService(PowerManager::class.java)
        linhaPermissao(
            "Bateria sem restrição", pm.isIgnoringBatteryOptimizations(packageName),
            "Evita que o sistema encerre o app em segundo plano."
        ) {
            abrir(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }

        texto("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}", 12f, "#8E89AD")
            .also { caixaPermissoes.addView(it) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        atualizarPermissoes()
    }

    private fun abrir(i: Intent) {
        try { startActivity(i) } catch (e: Exception) {
            // Alguns fabricantes não têm a tela específica: abre os detalhes do app
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    // ---------------- Tarefas ----------------

    private fun criarTarefa(daquiMs: Long) {
        val nome = campoNome.text.toString().trim().ifEmpty { "Tarefa de teste" }
        val t = Tarefa(id = Store.novoId(this), titulo = nome, prazo = System.currentTimeMillis() + daquiMs)
        Store.salvar(this, t)
        Agendador.agendarPrazo(this, t)
        DeadlineWidget.atualizarTodos(this)
        toast("Agendada para ${Store.hora(t.prazo)}")
        atualizarTarefas(); atualizarLog()
    }

    private fun restanteTexto(ms: Long): String {
        if (ms <= 0) return "venceu"
        val s = ms / 1000
        val h = s / 3600; val m = (s % 3600) / 60; val seg = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, seg) else "%02d:%02d".format(m, seg)
    }

    private fun atualizarTarefas() {
        caixaTarefas.removeAllViews()
        val todas = Store.tarefas(this).sortedByDescending { it.id }
        if (todas.isEmpty()) {
            caixaTarefas.addView(texto("Nenhuma tarefa ainda. Agende uma acima.", 13f)); return
        }
        val agora = System.currentTimeMillis()
        for (t in todas) {
            val linha = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(4), 0, px(4))
            }
            val textos = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            textos.addView(texto(t.titulo, 15f, "#1E1B2E"))
            val detalhe = buildString {
                append(Status.rotulo(t.status)).append(" · prazo ").append(Store.hora(t.prazo))
                if (t.status == Status.ATIVA) append(" · faltam ").append(restanteTexto(t.prazo - agora))
                if (t.adiamentos > 0) append(" · adiada ${t.adiamentos}x")
            }
            textos.addView(texto(detalhe, 12f))
            linha.addView(textos, LinearLayout.LayoutParams(0, -2, 1f))
            if (t.status == Status.AGUARDANDO) {
                linha.addView(botao("Responder") {
                    startActivity(Intent(this, AlarmActivity::class.java).putExtra(Agendador.EXTRA_ID, t.id))
                })
            }
            linha.addView(botao("Excluir") {
                Agendador.cancelar(this, t.id)
                Notificador.cancelar(this, t.id)
                Store.remover(this, t.id)
                Store.log(this, "Excluída: \"${t.titulo}\"")
                DeadlineWidget.atualizarTodos(this)
                atualizarTarefas(); atualizarLog()
            })
            caixaTarefas.addView(linha)
        }
        // Atualiza o log junto, para ver os disparos chegando com o app aberto
        atualizarLog()
    }

    // ---------------- Log ----------------

    private fun atualizarLog() {
        val linhas = Store.logs(this)
        val novo = if (linhas.isEmpty()) "(vazio)" else linhas.take(60).joinToString("\n")
        if (textoLog.text.toString() != novo) textoLog.text = novo // não atrapalha seleção à toa
    }

    private fun copiarLog() {
        val cabecalho = "Just in Time protótipo · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
            "${Build.MANUFACTURER} ${Build.MODEL}\n"
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("log", cabecalho + Store.logs(this).joinToString("\n")))
        toast("Log copiado")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
