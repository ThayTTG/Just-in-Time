package com.nokylabs.justintime.prototipo

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/**
 * Tela única do protótipo.
 * (A interface é montada em código para o protótipo ficar curto; no app final será Compose.)
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var caixaPermissoes: LinearLayout
    private lateinit var caixaTarefas: LinearLayout
    private lateinit var textoLog: TextView
    private lateinit var campoNome: EditText
    private lateinit var botaoData: Button
    private lateinit var botaoHora: Button
    private val dp by lazy { resources.displayMetrics.density }
    private var dialogoErroAberto = false

    /** Data e hora escolhidas para o prazo. Começa na próxima hora cheia. */
    private val prazoEscolhido: Calendar = Calendar.getInstance().apply {
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    private val relogio = object : Runnable {
        override fun run() {
            atualizarTarefas()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notificador.criarCanais(this)
        Relatorio.proteger(this, "Reagendar ao abrir") { Agendador.reagendarTudo(this, "app aberto") }
        montarTela()
    }

    override fun onResume() {
        super.onResume()
        atualizarPermissoes()
        atualizarLog()
        handler.post(relogio)
        oferecerRelatorioSeHouverErros()
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

    private fun linhaDeBotoes(vararg botoes: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        botoes.forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
    }

    private fun montarTela() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(18), px(8), px(18), px(32))
        }

        col.addView(titulo("1. Nova tarefa"))
        campoNome = EditText(this).apply { hint = "O que precisa ser feito?" }
        col.addView(campoNome)
        col.addView(texto("Prazo de entrega", 12f, "#8E89AD").apply { setPadding(0, px(10), 0, 0) })
        botaoData = botao("") { escolherData() }
        botaoHora = botao("") { escolherHora() }
        col.addView(linhaDeBotoes(botaoData, botaoHora))
        atualizarBotoesPrazo()
        col.addView(botao("Agendar tarefa") { agendarEscolhida() })

        col.addView(titulo("2. Tarefas"))
        caixaTarefas = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(caixaTarefas)

        col.addView(titulo("3. Permissões"))
        caixaPermissoes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(caixaPermissoes)

        col.addView(titulo("4. Preferências"))
        col.addView(CheckBox(this).apply {
            text = "Resumo nas notificações (contagem das próximas tarefas)"
            isChecked = Store.resumoAtivo(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Store.definirResumo(this@MainActivity, v); Vitrine.atualizarTudo(this@MainActivity) }
        })
        col.addView(CheckBox(this).apply {
            text = "Bolha flutuante com a próxima tarefa"
            isChecked = Store.bolhaAtiva(this@MainActivity)
            setOnCheckedChangeListener { _, v ->
                Store.definirBolha(this@MainActivity, v)
                if (v && !Settings.canDrawOverlays(this@MainActivity)) toast("Libere \"Aparecer sobre outros apps\" em Permissões")
                Vitrine.atualizarTudo(this@MainActivity)
            }
        })
        col.addView(botao("Mostrar a bolha de novo") {
            Store.fecharBolhaPara(this, -1); Vitrine.atualizarTudo(this)
        })

        col.addView(titulo("5. Diagnóstico"))
        col.addView(texto("Ferramentas de teste. \"Copiar log\" copia tudo para colar na conversa com o Claude.", 12f))
        col.addView(linhaDeBotoes(
            botao("Alarme em 30 s") { criarTarefa(nomeOuPadrao("Teste rápido"), System.currentTimeMillis() + 30_000L) },
            botao("Simular falha") { throw RuntimeException("Falha simulada pelo botão de teste") },
        ))
        col.addView(linhaDeBotoes(
            botao("Erro em 2º plano") {
                sendBroadcast(Intent(this, AlarmReceiver::class.java).setAction(AlarmReceiver.ACAO_SIMULAR_ERRO))
                toast("Erro simulado: deve chegar uma notificação")
            },
            botao("Reportar bug") { startActivity(Relatorio.intentCompartilhar(this)) },
        ))
        col.addView(linhaDeBotoes(
            botao("Copiar log") { copiarLog() },
            botao("Limpar log") { Store.limparLog(this); atualizarLog() },
        ))
        textoLog = texto("", 12f, "#2B2840").apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(px(10), px(10), px(10), px(10))
            setBackgroundColor(Color.parseColor("#F1EFF8"))
        }
        col.addView(textoLog)

        setContentView(ScrollView(this).apply { addView(col) })
    }

    // ---------------- Data e hora do prazo ----------------

    private fun atualizarBotoesPrazo() {
        botaoData.text = "📅 " + Store.data(prazoEscolhido.timeInMillis)
        botaoHora.text = "🕒 " + Store.soHora(prazoEscolhido.timeInMillis)
    }

    private fun escolherData() {
        DatePickerDialog(this, { _, ano, mes, dia ->
            prazoEscolhido.set(ano, mes, dia); atualizarBotoesPrazo()
        }, prazoEscolhido.get(Calendar.YEAR), prazoEscolhido.get(Calendar.MONTH), prazoEscolhido.get(Calendar.DAY_OF_MONTH)
        ).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
    }

    private fun escolherHora() {
        TimePickerDialog(this, { _, hora, minuto ->
            prazoEscolhido.set(Calendar.HOUR_OF_DAY, hora); prazoEscolhido.set(Calendar.MINUTE, minuto)
            prazoEscolhido.set(Calendar.SECOND, 0); prazoEscolhido.set(Calendar.MILLISECOND, 0)
            atualizarBotoesPrazo()
        }, prazoEscolhido.get(Calendar.HOUR_OF_DAY), prazoEscolhido.get(Calendar.MINUTE), true).show()
    }

    private fun nomeOuPadrao(padrao: String) = campoNome.text.toString().trim().ifEmpty { padrao }

    private fun agendarEscolhida() {
        val nome = campoNome.text.toString().trim()
        if (nome.isEmpty()) { toast("Dê um nome para a tarefa"); return }
        val prazo = prazoEscolhido.timeInMillis
        if (prazo <= System.currentTimeMillis()) { toast("Esse horário já passou. Escolha outro."); return }
        criarTarefa(nome, prazo)
        campoNome.setText("")
    }

    private fun criarTarefa(nome: String, prazo: Long) {
        val t = Tarefa(id = Store.novoId(this), titulo = nome, prazo = prazo)
        Store.salvar(this, t)
        Agendador.agendarPrazo(this, t)
        Vitrine.atualizarTudo(this)
        toast("Agendada para ${Store.amigavel(prazo)}")
        atualizarTarefas(); atualizarLog()
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

        linhaPermissao("Notificações", Notificador.podeNotificar(this), "Sem isso o alarme não aparece nem toca.") {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
            else abrir(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
        linhaPermissao("Alarmes exatos", Agendador.podeAgendarExato(this), "Para tocar no segundo certo, mesmo em repouso.") {
            if (Build.VERSION.SDK_INT >= 31) abrir(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
        linhaPermissao("Tela cheia no bloqueio", Notificador.podeTelaCheia(this), "Para a tela de alarme acender o celular bloqueado.") {
            if (Build.VERSION.SDK_INT >= 34) abrir(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        }
        linhaPermissao(
            "Aparecer sobre outros apps", Settings.canDrawOverlays(this),
            "Para a tela de alarme abrir com o celular em uso e para a bolha flutuante."
        ) {
            abrir(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        val pm = getSystemService(PowerManager::class.java)
        linhaPermissao("Bateria sem restrição", pm.isIgnoringBatteryOptimizations(packageName), "Evita que o sistema encerre o app em segundo plano.") {
            abrir(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
        caixaPermissoes.addView(
            texto("Versão ${Relatorio.versao(this)} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}", 12f, "#8E89AD")
        )
        // Ao voltar das configurações, a bolha pode ter ficado disponível
        Vitrine.atualizarTudo(this)
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

    private fun restanteTexto(ms: Long): String {
        if (ms <= 0) return "venceu"
        val s = ms / 1000
        val d = s / 86400; val h = (s % 86400) / 3600; val m = (s % 3600) / 60; val seg = s % 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "%d:%02d:%02d".format(h, m, seg)
            else -> "%02d:%02d".format(m, seg)
        }
    }

    private fun atualizarTarefas() {
        caixaTarefas.removeAllViews()
        val todas = Store.tarefas(this).sortedWith(compareBy<Tarefa>({ if (it.aberta) 0 else 1 }, { it.prazo }))
        if (todas.isEmpty()) {
            caixaTarefas.addView(texto("Nenhuma tarefa ainda. Crie uma acima.", 13f)); return
        }
        val agora = System.currentTimeMillis()
        for (t in todas) {
            val linha = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(4), 0, px(4))
            }
            val textos = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            textos.addView(texto(t.titulo, 15f, if (t.aberta) "#1E1B2E" else "#8E89AD"))
            val detalhe = buildString {
                append(Status.rotulo(t.status)).append(" · ").append(Store.amigavel(t.prazo))
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
                Vitrine.atualizarTudo(this)
                atualizarTarefas(); atualizarLog()
            })
            caixaTarefas.addView(linha)
        }
        atualizarLog()
    }

    // ---------------- Relatório de bugs ----------------

    private fun oferecerRelatorioSeHouverErros() {
        val erros = Store.errosPendentes(this)
        if (erros.isEmpty() || dialogoErroAberto) return
        dialogoErroAberto = true
        AlertDialog.Builder(this)
            .setTitle("Ops, algo deu errado 😕")
            .setMessage(
                "O app registrou ${erros.size} erro${if (erros.size > 1) "s" else ""}. " +
                    "Quer enviar um relatório? Ele leva o modelo do celular, a versão do app, o erro " +
                    "e as últimas linhas do log (que incluem os nomes das tarefas)."
            )
            .setPositiveButton("Reportar bug") { _, _ ->
                startActivity(Relatorio.intentCompartilhar(this)); Store.limparErros(this)
            }
            .setNeutralButton("Copiar") { _, _ ->
                copiar(Relatorio.montarTexto(this)); Store.limparErros(this)
            }
            .setNegativeButton("Descartar") { _, _ -> Store.limparErros(this) }
            .setOnDismissListener { dialogoErroAberto = false }
            .show()
    }

    // ---------------- Log ----------------

    private fun atualizarLog() {
        val linhas = Store.logs(this)
        val novo = if (linhas.isEmpty()) "(vazio)" else linhas.take(60).joinToString("\n")
        if (textoLog.text.toString() != novo) textoLog.text = novo // não atrapalha a seleção à toa
    }

    private fun copiarLog() {
        val cabecalho = "Just in Time ${Relatorio.versao(this)} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
            "${Build.MANUFACTURER} ${Build.MODEL}\n"
        copiar(cabecalho + Store.logs(this).joinToString("\n"))
    }

    private fun copiar(texto: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Just in Time", texto))
        toast("Copiado")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
