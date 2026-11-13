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
import android.widget.HorizontalScrollView
import android.widget.NumberPicker
import android.widget.SeekBar
import android.graphics.drawable.GradientDrawable
import android.icu.text.BreakIterator
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

    // Escolhas da nova tarefa
    private lateinit var campoEmoji: EditText
    private lateinit var linhaCores: LinearLayout
    private lateinit var botaoRepetir: Button
    private var corEscolhida = 0
    private var repeticaoEscolhida = Repeticao()

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
        val linhaNome = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        campoEmoji = EditText(this).apply {
            hint = "🙂"; textSize = 22f; gravity = Gravity.CENTER
            setOnFocusChangeListener { _, foco -> if (!foco) setText(primeiroEmoji(text.toString())) }
        }
        campoNome = EditText(this).apply { hint = "O que precisa ser feito?" }
        linhaNome.addView(campoEmoji, LinearLayout.LayoutParams(px(56), -2))
        linhaNome.addView(campoNome, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(linhaNome)

        col.addView(rotuloCampo("Ícone (opcional)"))
        col.addView(linhaDeEmojis())
        col.addView(rotuloCampo("Cor (opcional)"))
        linhaCores = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        col.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(linhaCores) })
        desenharCores()

        col.addView(rotuloCampo("Prazo de entrega"))
        botaoData = botao("") { escolherData() }
        botaoHora = botao("") { escolherHora() }
        col.addView(linhaDeBotoes(botaoData, botaoHora))
        col.addView(rotuloCampo("Repetição"))
        botaoRepetir = botao("") { escolherRepeticao() }
        col.addView(botaoRepetir)
        atualizarBotoesPrazo()
        col.addView(botao("Agendar tarefa") { agendarEscolhida() }.apply {
            setTextColor(Color.WHITE); textSize = 16f
            background = GradientDrawable().apply { cornerRadius = px(12).toFloat(); setColor(Color.parseColor("#5E35B1")) }
            layoutParams = LinearLayout.LayoutParams(-1, px(52)).apply { topMargin = px(14) }
        })

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
        col.addView(rotuloCampo("Tamanho da bolha (ou segure a bolha / use dois dedos)"))
        col.addView(SeekBar(this).apply {
            max = 110
            progress = ((Store.escalaBolha(this@MainActivity) - 0.7f) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, v: Int, doUsuario: Boolean) {}
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {
                    Store.salvarEscalaBolha(this@MainActivity, 0.7f + sb.progress / 100f)
                    Vitrine.atualizarTudo(this@MainActivity)
                }
            })
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
        val porHorarios = repeticaoEscolhida.tipo == Repeticao.Tipo.SEMANAL
        botaoData.isEnabled = !porHorarios; botaoHora.isEnabled = !porHorarios
        if (porHorarios) {
            botaoData.text = "📅 pelos horários"; botaoHora.text = "🕒 pelos horários"
        } else {
            botaoData.text = "📅 " + Store.data(prazoEscolhido.timeInMillis)
            botaoHora.text = "🕒 " + Store.soHora(prazoEscolhido.timeInMillis)
        }
        botaoRepetir.text = "🔁 " + repeticaoEscolhida.descricao()
    }

    private fun rotuloCampo(s: String) = texto(s, 12f, "#8E89AD").apply { setPadding(0, px(12), 0, px(2)) }

    // ---------------- Emoji e cor ----------------

    /** Fica só com o primeiro "caractere visível" (um emoji pode ter vários códigos). */
    private fun primeiroEmoji(s: String): String {
        val t = s.trim()
        if (t.isEmpty()) return ""
        val it = BreakIterator.getCharacterInstance().apply { setText(t) }
        val fim = it.next()
        return if (fim == BreakIterator.DONE) t else t.substring(0, fim)
    }

    private fun linhaDeEmojis(): HorizontalScrollView {
        val linha = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        (listOf("∅") + Paleta.EMOJIS).forEach { e ->
            linha.addView(TextView(this).apply {
                text = e; textSize = 24f; setPadding(px(8), px(4), px(8), px(4))
                setOnClickListener { campoEmoji.setText(if (e == "∅") "" else e) }
            })
        }
        return HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(linha) }
    }

    private fun desenharCores() {
        linhaCores.removeAllViews()
        (listOf("Sem cor" to 0) + Paleta.CORES).forEach { (nome, cor) ->
            val escolhida = cor == corEscolhida
            linhaCores.addView(TextView(this).apply {
                contentDescription = nome
                text = if (cor == 0) "∅" else if (escolhida) "✓" else ""
                gravity = Gravity.CENTER; textSize = 16f
                setTextColor(if (cor == 0) Color.parseColor("#8E89AD") else Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (cor == 0) Color.TRANSPARENT else cor)
                    setStroke(px(if (escolhida) 3 else 1), Color.parseColor(if (escolhida) "#1E1B2E" else "#C9C5DB"))
                }
                layoutParams = LinearLayout.LayoutParams(px(38), px(38)).apply { marginEnd = px(8) }
                setOnClickListener { corEscolhida = cor; desenharCores() }
            })
        }
    }

    // ---------------- Repetição ----------------

    private fun escolherRepeticao() {
        val opcoes = arrayOf(
            "Não repete", "Todo dia", "Toda semana", "A cada 15 dias", "Todo mês", "Todo ano",
            "Personalizado (a cada N…)", "Dias e horários específicos…"
        )
        AlertDialog.Builder(this).setTitle("Repetir").setItems(opcoes) { _, i ->
            val I = Repeticao.Tipo.INTERVALO
            when (i) {
                0 -> definirRepeticao(Repeticao())
                1 -> definirRepeticao(Repeticao(I, 1, Repeticao.Unidade.DIA))
                2 -> definirRepeticao(Repeticao(I, 1, Repeticao.Unidade.SEMANA))
                3 -> definirRepeticao(Repeticao(I, 2, Repeticao.Unidade.SEMANA))
                4 -> definirRepeticao(Repeticao(I, 1, Repeticao.Unidade.MES))
                5 -> definirRepeticao(Repeticao(I, 1, Repeticao.Unidade.ANO))
                6 -> escolherPersonalizado()
                7 -> editarHorarios((if (repeticaoEscolhida.tipo == Repeticao.Tipo.SEMANAL) repeticaoEscolhida.horarios else emptyList()).toMutableList())
            }
        }.show()
    }

    private fun definirRepeticao(r: Repeticao) { repeticaoEscolhida = r; atualizarBotoesPrazo() }

    private fun escolherPersonalizado() {
        val unidades = Repeticao.Unidade.values()
        val numero = NumberPicker(this).apply { minValue = 1; maxValue = 99; value = 2 }
        val unidade = NumberPicker(this).apply {
            minValue = 0; maxValue = unidades.size - 1
            displayedValues = unidades.map { it.plural }.toTypedArray(); value = 1
        }
        val caixa = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            addView(texto("A cada", 16f, "#1E1B2E")); addView(numero); addView(unidade)
        }
        AlertDialog.Builder(this).setTitle("Repetir a cada…").setView(caixa)
            .setPositiveButton("OK") { _, _ ->
                definirRepeticao(Repeticao(Repeticao.Tipo.INTERVALO, numero.value, unidades[unidade.value]))
            }
            .setNegativeButton("Cancelar", null).show()
    }

    /** Lista de "dia + horário" (ex.: seg de manhã, qui à noite, sáb à tarde). */
    private fun editarHorarios(lista: MutableList<Horario>) {
        val caixa = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(8), px(20), 0) }
        val ordenados = Repeticao(Repeticao.Tipo.SEMANAL, horarios = lista).horariosOrdenados()
        if (ordenados.isEmpty()) caixa.addView(texto("Nenhum horário ainda.", 14f))
        var dialogo: AlertDialog? = null
        ordenados.forEach { h ->
            val linha = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            linha.addView(texto("${Repeticao.DIAS_LONGOS[h.diaSemana - 1]} às %02d:%02d".format(h.hora, h.minuto), 16f, "#1E1B2E"),
                LinearLayout.LayoutParams(0, -2, 1f))
            linha.addView(botao("Remover") { lista.remove(h); dialogo?.dismiss(); editarHorarios(lista) })
            caixa.addView(linha)
        }
        caixa.addView(botao("+ Adicionar dia e horário") { dialogo?.dismiss(); adicionarHorario(lista) })
        dialogo = AlertDialog.Builder(this).setTitle("Dias e horários").setView(caixa)
            .setPositiveButton("Pronto") { _, _ ->
                definirRepeticao(if (lista.isEmpty()) Repeticao() else Repeticao(Repeticao.Tipo.SEMANAL, horarios = lista.toList()))
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun adicionarHorario(lista: MutableList<Horario>) {
        AlertDialog.Builder(this).setTitle("Qual dia?").setItems(Repeticao.DIAS_LONGOS.toTypedArray()) { _, i ->
            val dia = i + 1 // Calendar.SUNDAY = 1
            val periodos = arrayOf("Manhã (08:00)", "Tarde (14:00)", "Noite (20:00)", "Escolher horário…")
            AlertDialog.Builder(this).setTitle(Repeticao.DIAS_LONGOS[i]).setItems(periodos) { _, p ->
                fun add(h: Int, m: Int) {
                    if (lista.none { it.diaSemana == dia && it.hora == h && it.minuto == m }) lista.add(Horario(dia, h, m))
                    editarHorarios(lista)
                }
                when (p) {
                    0 -> add(8, 0); 1 -> add(14, 0); 2 -> add(20, 0)
                    else -> TimePickerDialog(this, { _, h, m -> add(h, m) }, 9, 0, true)
                        .apply { setOnCancelListener { editarHorarios(lista) } }.show()
                }
            }.setOnCancelListener { editarHorarios(lista) }.show()
        }.setOnCancelListener { editarHorarios(lista) }.show()
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
        val agora = System.currentTimeMillis()
        val prazo = if (repeticaoEscolhida.tipo == Repeticao.Tipo.SEMANAL)
            repeticaoEscolhida.proximaApos(0, agora) ?: run { toast("Adicione pelo menos um horário"); return }
        else prazoEscolhido.timeInMillis
        if (prazo <= agora) { toast("Esse horário já passou. Escolha outro."); return }
        criarTarefa(nome, prazo, primeiroEmoji(campoEmoji.text.toString()), corEscolhida, repeticaoEscolhida)
        campoNome.setText(""); campoEmoji.setText("")
        corEscolhida = 0; desenharCores()
        repeticaoEscolhida = Repeticao(); atualizarBotoesPrazo()
    }

    private fun criarTarefa(nome: String, prazo: Long, emoji: String = "", cor: Int = 0, rep: Repeticao = Repeticao()) {
        val t = Tarefa(id = Store.novoId(this), titulo = nome, prazo = prazo, emoji = emoji, cor = cor, repeticao = rep.codificar())
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
            val nomeView = texto(t.rotulo, 15f, if (t.aberta) "#1E1B2E" else "#8E89AD")
            if (t.cor != 0) {
                nomeView.setCompoundDrawablesRelativeWithIntrinsicBounds(GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(t.cor); setSize(px(12), px(12))
                }, null, null, null)
                nomeView.compoundDrawablePadding = px(8)
            }
            textos.addView(nomeView)
            val detalhe = buildString {
                append(Status.rotulo(t.status)).append(" · ").append(Store.amigavel(t.prazo))
                if (t.status == Status.ATIVA) append(" · faltam ").append(restanteTexto(t.prazo - agora))
                if (t.adiamentos > 0) append(" · adiada ${t.adiamentos}x")
                if (t.regra.repete) append(" · 🔁 ").append(t.regra.descricao())
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
                Store.log(this, "Excluída: \"${t.rotulo}\"")
                if (t.regra.repete && t.status == Status.ATIVA) toast("Repetição encerrada: \"${t.rotulo}\" não volta mais")
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
