package com.nokylabs.justintime.prototipo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

/** O widget da tela inicial. Toda a lógica de desenho está na Vitrine. */
class DeadlineWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) =
        Relatorio.proteger(c, "Widget: onUpdate") { Vitrine.atualizarTudo(c) }

    override fun onEnabled(c: Context) =
        Relatorio.proteger(c, "Widget: onEnabled") { Vitrine.atualizarTudo(c) }

    companion object {
        /** Mantido por compatibilidade com o resto do código. */
        fun atualizarTodos(c: Context) = Vitrine.atualizarTudo(c)
    }
}

/** Ids de uma linha (barra colorida, nome, cronômetro, texto). */
class IdsLinha(val linha: Int, val barra: Int, val nome: Int, val crono: Int, val info: Int)

/**
 * "Vitrine": tudo o que mostra as tarefas fora do app — widget, notificação-resumo e bolha.
 * Uma única função atualiza os três, para eles nunca discordarem entre si.
 */
object Vitrine {
    const val UMA_HORA = 3_600_000L
    const val UM_DIA = 24 * UMA_HORA

    const val COR_NEUTRA = 0xFF8E89AD.toInt()
    const val COR_ATENCAO = 0xFFF9A825.toInt()
    const val COR_CRITICA = 0xFFE53935.toInt()
    const val COR_RESPONDER = 0xFFB388FF.toInt()
    const val COR_RESPONDER_ESCURA = 0xFF6A3FD0.toInt() // legível em fundo claro
    const val COR_BRANCA = 0xFFFFFFFF.toInt()

    private val LINHAS_WIDGET = listOf(
        IdsLinha(R.id.linha1, R.id.barra1, R.id.nome1, R.id.crono1, R.id.info1),
        IdsLinha(R.id.linha2, R.id.barra2, R.id.nome2, R.id.crono2, R.id.info2),
        IdsLinha(R.id.linha3, R.id.barra3, R.id.nome3, R.id.crono3, R.id.info3),
    )

    fun atualizarTudo(c: Context) {
        val agora = System.currentTimeMillis()
        val tarefas = Store.abertasOrdenadas(c)

        // Próximo momento em que alguma tarefa muda de cor (24 h ou 1 h antes do prazo)
        val proximaMudanca = tarefas
            .filter { it.status == Status.ATIVA }
            .flatMap { listOf(it.prazo - UM_DIA, it.prazo - UMA_HORA) }
            .filter { it > agora }
            .minOrNull()
        Agendador.agendarAtualizacaoWidget(c, proximaMudanca)

        Relatorio.proteger(c, "Atualizar widget") { atualizarWidget(c, tarefas, agora) }
        Relatorio.proteger(c, "Atualizar resumo") { Notificador.atualizarResumo(c, tarefas, agora) }
        Relatorio.proteger(c, "Atualizar bolha") { BolhaService.sincronizar(c, tarefas) }
    }

    private fun atualizarWidget(c: Context, tarefas: List<Tarefa>, agora: Long) {
        val mgr = AppWidgetManager.getInstance(c)
        val ids = mgr.getAppWidgetIds(ComponentName(c, DeadlineWidget::class.java))
        if (ids.isEmpty()) return
        val rv = RemoteViews(c.packageName, R.layout.widget_prazo)
        val abrirApp = PendingIntent.getActivity(
            c, 1, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        rv.setOnClickPendingIntent(R.id.raiz, abrirApp)
        rv.setOnClickPendingIntent(R.id.botaoMais, abrirApp)
        rv.setViewVisibility(R.id.vazio, if (tarefas.isEmpty()) View.VISIBLE else View.GONE)
        preencher(rv, LINHAS_WIDGET, tarefas, agora, fundoEscuro = true)
        mgr.updateAppWidget(ids, rv)
    }

    /** Preenche N linhas + o texto "+X tarefas" (id R.id.mais) num RemoteViews. */
    fun preencher(rv: RemoteViews, linhas: List<IdsLinha>, tarefas: List<Tarefa>, agora: Long, fundoEscuro: Boolean) {
        val extras = tarefas.size - linhas.size
        if (extras > 0) {
            rv.setTextViewText(R.id.mais, "+$extras tarefa${if (extras > 1) "s" else ""}")
            rv.setViewVisibility(R.id.mais, View.VISIBLE)
        } else rv.setViewVisibility(R.id.mais, View.GONE)

        linhas.forEachIndexed { i, ids ->
            val t = tarefas.getOrNull(i)
            if (t == null) rv.setViewVisibility(ids.linha, View.GONE)
            else { rv.setViewVisibility(ids.linha, View.VISIBLE); preencherLinha(rv, ids, t, agora, fundoEscuro) }
        }
    }

    /**
     * Base do Chronometer para um prazo. Widget, resumo e bolha usam SEMPRE esta função,
     * então todos partem exatamente do mesmo instante.
     */
    fun baseCrono(prazo: Long): Long = prazo - (System.currentTimeMillis() - SystemClock.elapsedRealtime())

    /**
     * Cor da barra: a cor escolhida para a tarefa; sem cor, a cor da urgência.
     * A urgência continua visível pelo cronômetro, que fica vermelho na última hora.
     */
    fun corDaBarra(t: Tarefa, restante: Long): Int = when {
        t.cor != 0 -> t.cor
        t.status == Status.AGUARDANDO || restante <= 0 -> COR_RESPONDER
        restante > UM_DIA -> COR_NEUTRA
        restante > UMA_HORA -> COR_ATENCAO
        else -> COR_CRITICA
    }

    fun preencherLinha(rv: RemoteViews, ids: IdsLinha, t: Tarefa, agora: Long, fundoEscuro: Boolean) {
        rv.setTextViewText(ids.nome, t.rotulo)
        val restante = t.prazo - agora
        barra(rv, ids, corDaBarra(t, restante))
        when {
            t.status == Status.AGUARDANDO || restante <= 0 ->
                info(rv, ids, "Responder", if (fundoEscuro) COR_RESPONDER else COR_RESPONDER_ESCURA)
            restante > UM_DIA ->
                info(rv, ids, Store.curto(t.prazo), COR_NEUTRA)
            else -> {
                rv.setChronometer(ids.crono, baseCrono(t.prazo), null, true)
                rv.setChronometerCountDown(ids.crono, true)
                // Fora da última hora, mantém a cor do layout (branca no widget, a do tema na notificação)
                if (restante <= UMA_HORA) rv.setTextColor(ids.crono, COR_CRITICA)
                rv.setViewVisibility(ids.crono, View.VISIBLE)
                rv.setViewVisibility(ids.info, View.GONE)
            }
        }
    }

    private fun barra(rv: RemoteViews, ids: IdsLinha, cor: Int) =
        rv.setInt(ids.barra, "setBackgroundColor", cor)

    private fun info(rv: RemoteViews, ids: IdsLinha, texto: String, cor: Int) {
        rv.setChronometer(ids.crono, SystemClock.elapsedRealtime(), null, false)
        rv.setViewVisibility(ids.crono, View.GONE)
        rv.setTextViewText(ids.info, texto)
        rv.setTextColor(ids.info, cor)
        rv.setViewVisibility(ids.info, View.VISIBLE)
    }
}
