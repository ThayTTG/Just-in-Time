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

/**
 * Widget com contagem regressiva.
 *
 * O segredo: o Chronometer em modo regressivo é desenhado pelo próprio launcher e
 * conta sozinho. O app só "acorda" para trocar cores/estados, nunca a cada segundo.
 */
class DeadlineWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) = atualizarTodos(c)

    override fun onEnabled(c: Context) = atualizarTodos(c)

    companion object {
        private const val UMA_HORA = 3_600_000L
        private const val UM_DIA = 24 * UMA_HORA

        private const val COR_NEUTRA = 0xFF8E89AD.toInt()
        private const val COR_ATENCAO = 0xFFF9A825.toInt()
        private const val COR_CRITICA = 0xFFE53935.toInt()
        private const val COR_RESPONDER = 0xFFB388FF.toInt()
        private const val COR_TEXTO = 0xFFFFFFFF.toInt()

        private val LINHAS = intArrayOf(R.id.linha1, R.id.linha2, R.id.linha3)
        private val BARRAS = intArrayOf(R.id.barra1, R.id.barra2, R.id.barra3)
        private val NOMES = intArrayOf(R.id.nome1, R.id.nome2, R.id.nome3)
        private val CRONOS = intArrayOf(R.id.crono1, R.id.crono2, R.id.crono3)
        private val INFOS = intArrayOf(R.id.info1, R.id.info2, R.id.info3)

        fun atualizarTodos(c: Context) {
            val agora = System.currentTimeMillis()
            val tarefas = Store.abertasOrdenadas(c)

            // Próximo momento em que alguma tarefa muda de cor (24 h ou 1 h antes do prazo)
            val proximaMudanca = tarefas
                .filter { it.status == Status.ATIVA }
                .flatMap { listOf(it.prazo - UM_DIA, it.prazo - UMA_HORA) }
                .filter { it > agora }
                .minOrNull()
            Agendador.agendarAtualizacaoWidget(c, proximaMudanca)

            val mgr = AppWidgetManager.getInstance(c)
            val ids = mgr.getAppWidgetIds(ComponentName(c, DeadlineWidget::class.java))
            if (ids.isEmpty()) return
            mgr.updateAppWidget(ids, montar(c, tarefas, agora))
        }

        private fun montar(c: Context, tarefas: List<Tarefa>, agora: Long): RemoteViews {
            val rv = RemoteViews(c.packageName, R.layout.widget_prazo)

            val abrirApp = PendingIntent.getActivity(
                c, 1, Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.raiz, abrirApp)
            rv.setOnClickPendingIntent(R.id.botaoMais, abrirApp)

            rv.setViewVisibility(R.id.vazio, if (tarefas.isEmpty()) View.VISIBLE else View.GONE)
            val extras = tarefas.size - LINHAS.size
            if (extras > 0) {
                rv.setTextViewText(R.id.mais, "+$extras tarefa${if (extras > 1) "s" else ""}")
                rv.setViewVisibility(R.id.mais, View.VISIBLE)
            } else rv.setViewVisibility(R.id.mais, View.GONE)

            for (i in LINHAS.indices) {
                val t = tarefas.getOrNull(i)
                if (t == null) { rv.setViewVisibility(LINHAS[i], View.GONE); continue }
                rv.setViewVisibility(LINHAS[i], View.VISIBLE)
                rv.setTextViewText(NOMES[i], t.titulo)

                val restante = t.prazo - agora
                when {
                    t.status == Status.AGUARDANDO || restante <= 0 -> {
                        cor(rv, i, COR_RESPONDER)
                        mostrarInfo(rv, i, "Responder", COR_RESPONDER)
                    }
                    restante > UM_DIA -> {
                        cor(rv, i, COR_NEUTRA)
                        val dias = restante / UM_DIA
                        mostrarInfo(rv, i, "em $dias dia${if (dias > 1) "s" else ""}", COR_NEUTRA)
                    }
                    else -> {
                        val corFaixa = if (restante > UMA_HORA) COR_ATENCAO else COR_CRITICA
                        cor(rv, i, corFaixa)
                        // Converte o prazo (relógio de parede) para a base do Chronometer
                        val base = SystemClock.elapsedRealtime() + restante
                        rv.setChronometer(CRONOS[i], base, null, true)
                        rv.setChronometerCountDown(CRONOS[i], true)
                        rv.setTextColor(CRONOS[i], if (restante > UMA_HORA) COR_TEXTO else COR_CRITICA)
                        rv.setViewVisibility(CRONOS[i], View.VISIBLE)
                        rv.setViewVisibility(INFOS[i], View.GONE)
                    }
                }
            }
            return rv
        }

        private fun cor(rv: RemoteViews, i: Int, cor: Int) =
            rv.setInt(BARRAS[i], "setBackgroundColor", cor)

        private fun mostrarInfo(rv: RemoteViews, i: Int, texto: String, cor: Int) {
            rv.setChronometer(CRONOS[i], SystemClock.elapsedRealtime(), null, false)
            rv.setViewVisibility(CRONOS[i], View.GONE)
            rv.setTextViewText(INFOS[i], texto)
            rv.setTextColor(INFOS[i], cor)
            rv.setViewVisibility(INFOS[i], View.VISIBLE)
        }
    }
}
