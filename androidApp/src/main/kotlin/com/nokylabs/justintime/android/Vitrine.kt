package com.nokylabs.justintime.android

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.nokylabs.justintime.dominio.Item
import com.nokylabs.justintime.dominio.Status
import com.nokylabs.justintime.dominio.TipoPrazo
import com.nokylabs.justintime.dominio.formatado
import com.nokylabs.justintime.ui.Formatos

/** O widget da tela inicial. Toda a lógica de desenho está na [Vitrine]. */
class PrazosWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) =
        Erros.proteger(c, "Widget: atualizar") { Vitrine.atualizar(c) }

    override fun onEnabled(c: Context) = Erros.proteger(c, "Widget: adicionado") { Vitrine.atualizar(c) }
}

/** Ids de uma linha (barra colorida, nome, cronômetro, texto). */
class IdsLinha(val linha: Int, val barra: Int, val nome: Int, val crono: Int, val info: Int)

/**
 * "Vitrine": tudo o que mostra as tarefas fora do app — widget, notificação-resumo e bolha.
 * Uma única função atualiza os três, para eles nunca discordarem entre si (o mesmo do protótipo).
 *
 * O segredo da contagem: o Chronometer em modo regressivo é desenhado pelo próprio sistema e
 * conta sozinho. O app só "acorda" para trocar cores e estados, nunca a cada segundo.
 */
object Vitrine {
    const val UMA_HORA = 3_600_000L
    const val UM_DIA = 24 * UMA_HORA
    const val ID_RESUMO = 1_000_000
    const val CANAL_RESUMO = "resumo"

    // Cores do NokyLabs.UI (tema escuro) + amarelo do Just in Time
    const val COR_APP = 0xFFFFD60A.toInt()
    const val COR_APP_NO_CLARO = 0xFF856F05.toInt() // o amarelo legível em fundo claro
    const val COR_NEUTRA = 0xFF968DAC.toInt()
    const val COR_ATENCAO = 0xFFF6B84C.toInt()
    const val COR_CRITICA = 0xFFF67070.toInt()
    const val COR_TEXTO = 0xFFF0ECF8.toInt()

    private val LINHAS_WIDGET = listOf(
        IdsLinha(R.id.linha1, R.id.barra1, R.id.nome1, R.id.crono1, R.id.info1),
        IdsLinha(R.id.linha2, R.id.barra2, R.id.nome2, R.id.crono2, R.id.info2),
        IdsLinha(R.id.linha3, R.id.barra3, R.id.nome3, R.id.crono3, R.id.info3),
    )
    private val LINHAS_RECOLHIDO = LINHAS_WIDGET.take(1)
    private val LINHAS_EXPANDIDO = LINHAS_WIDGET.take(2)

    fun criarCanal(c: Context) {
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CANAL_RESUMO, "Resumo das tarefas", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Notificação fixa com a contagem regressiva das próximas tarefas"
                setSound(null, null); enableVibration(false); setShowBadge(false)
            }
        )
    }

    /** Atualiza widget, resumo e bolha (e agenda a próxima troca de cor). */
    fun atualizar(c: Context) {
        val itens = c.tarefas.abertasAgora()
        val agora = System.currentTimeMillis()
        Erros.proteger(c, "Vitrine: widget") { atualizarWidget(c, itens, agora) }
        Erros.proteger(c, "Vitrine: resumo") { atualizarResumo(c, itens, agora) }
        Erros.proteger(c, "Vitrine: bolha") { BolhaService.sincronizar(c, itens) }
        Erros.proteger(c, "Vitrine: próxima troca de cor") { agendarProximaMudanca(c, itens, agora) }
    }

    // ---------------- Cálculos comuns ----------------

    fun prazoMs(c: Context, item: Item) = c.tarefas.instante(item.prazo).toEpochMilliseconds()

    /** Base do Chronometer para um prazo: widget, resumo e bolha usam sempre esta conta. */
    fun baseCrono(prazoMs: Long): Long = prazoMs - (System.currentTimeMillis() - SystemClock.elapsedRealtime())

    /** A cor escolhida para a tarefa; sem cor, a cor da urgência. */
    fun corDaBarra(item: Item, restante: Long): Int = when {
        item.tarefa.cor != 0 -> item.tarefa.cor
        item.status == Status.AGUARDANDO || restante <= 0 -> COR_APP
        restante > UM_DIA -> COR_NEUTRA
        restante > UMA_HORA -> COR_ATENCAO
        else -> COR_CRITICA
    }

    /** Texto para prazos com mais de 24 h ("sáb., 21/11 09:00") ou de dia inteiro. */
    fun textoDoPrazo(item: Item): String =
        if (item.tarefa.tipoPrazo == TipoPrazo.DIA_INTEIRO) "${Formatos.data(item.prazo.date)} (dia)"
        else "${Formatos.data(item.prazo.date)} ${item.prazo.time.formatado()}"

    /** Quando o app precisa redesenhar por conta própria: 24 h e 1 h antes de cada prazo. */
    private fun agendarProximaMudanca(c: Context, itens: List<Item>, agora: Long) {
        val quando = itens.filter { it.status == Status.ATIVA }
            .flatMap { val p = prazoMs(c, it); listOf(p - UM_DIA, p - UMA_HORA) }
            .filter { it > agora }.minOrNull()
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            c, 515_151, Intent(c, AlarmeReceiver::class.java).setAction(AlarmeReceiver.VITRINE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (quando == null) am.cancel(pi)
        else if (AgendadorAndroid(c).podeAgendarExato()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
    }

    // ---------------- Widget ----------------

    private fun atualizarWidget(c: Context, itens: List<Item>, agora: Long) {
        val mgr = AppWidgetManager.getInstance(c)
        val ids = mgr.getAppWidgetIds(ComponentName(c, PrazosWidget::class.java))
        if (ids.isEmpty()) return
        val rv = RemoteViews(c.packageName, R.layout.widget_prazo)
        rv.setOnClickPendingIntent(R.id.raiz, abrirApp(c, novaTarefa = false))
        rv.setOnClickPendingIntent(R.id.botaoMais, abrirApp(c, novaTarefa = true))
        rv.setViewVisibility(R.id.vazio, if (itens.isEmpty()) View.VISIBLE else View.GONE)
        preencher(c, rv, LINHAS_WIDGET, itens, agora, fundoEscuro = true)
        // Contador de entregas (o mesmo período escolhido no app)
        val placar = c.tarefas.placar(c.tarefas.periodoDoPlacar())
        if (placar.total > 0 || placar.sequencia > 0) {
            val partes = mutableListOf("${placar.periodo.rotulo}: ✓ ${placar.entregues}  ✗ ${placar.naoEntregues}")
            if (placar.sequencia > 0) partes += "🔥 ${placar.sequencia}"
            rv.setTextViewText(R.id.placar, partes.joinToString("  ·  "))
            rv.setViewVisibility(R.id.placar, View.VISIBLE)
        } else rv.setViewVisibility(R.id.placar, View.GONE)
        mgr.updateAppWidget(ids, rv)
    }

    fun abrirApp(c: Context, novaTarefa: Boolean): PendingIntent = PendingIntent.getActivity(
        c, if (novaTarefa) 31 else 30,
        Intent(c, MainActivity::class.java).putExtra(MainActivity.NOVA_TAREFA, novaTarefa)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Preenche N linhas + o texto "+X tarefas" (id R.id.mais). */
    private fun preencher(c: Context, rv: RemoteViews, linhas: List<IdsLinha>, itens: List<Item>, agora: Long, fundoEscuro: Boolean) {
        val extras = itens.size - linhas.size
        if (extras > 0) {
            rv.setTextViewText(R.id.mais, "+$extras tarefa${if (extras > 1) "s" else ""}")
            rv.setViewVisibility(R.id.mais, View.VISIBLE)
        } else rv.setViewVisibility(R.id.mais, View.GONE)

        linhas.forEachIndexed { i, ids ->
            val item = itens.getOrNull(i)
            if (item == null) rv.setViewVisibility(ids.linha, View.GONE)
            else { rv.setViewVisibility(ids.linha, View.VISIBLE); preencherLinha(c, rv, ids, item, agora, fundoEscuro) }
        }
    }

    private fun preencherLinha(c: Context, rv: RemoteViews, ids: IdsLinha, item: Item, agora: Long, fundoEscuro: Boolean) {
        rv.setTextViewText(ids.nome, item.rotulo)
        val prazo = prazoMs(c, item)
        val restante = prazo - agora
        rv.setInt(ids.barra, "setBackgroundColor", corDaBarra(item, restante))
        when {
            item.status == Status.AGUARDANDO || restante <= 0 ->
                info(rv, ids, "Responder", if (fundoEscuro) COR_APP else COR_APP_NO_CLARO)
            restante > UM_DIA || item.tarefa.tipoPrazo == TipoPrazo.DIA_INTEIRO ->
                info(rv, ids, textoDoPrazo(item), COR_NEUTRA)
            else -> {
                rv.setChronometer(ids.crono, baseCrono(prazo), null, true)
                rv.setChronometerCountDown(ids.crono, true)
                // Fora da última hora, mantém a cor do layout (clara no widget, a do tema na notificação)
                if (restante <= UMA_HORA) rv.setTextColor(ids.crono, COR_CRITICA)
                rv.setViewVisibility(ids.crono, View.VISIBLE)
                rv.setViewVisibility(ids.info, View.GONE)
            }
        }
    }

    private fun info(rv: RemoteViews, ids: IdsLinha, texto: String, cor: Int) {
        rv.setChronometer(ids.crono, SystemClock.elapsedRealtime(), null, false)
        rv.setViewVisibility(ids.crono, View.GONE)
        rv.setTextViewText(ids.info, texto)
        rv.setTextColor(ids.info, cor)
        rv.setViewVisibility(ids.info, View.VISIBLE)
    }

    // ---------------- Notificação-resumo ----------------

    /**
     * Fixa e silenciosa, espelha o widget: recolhida mostra a próxima tarefa; expandida mostra
     * até 2 + "+N tarefas". Também é a notificação do serviço da bolha.
     */
    fun construirResumo(c: Context, itens: List<Item>): Notification {
        val agora = System.currentTimeMillis()
        val recolhida = RemoteViews(c.packageName, R.layout.notif_resumo_recolhida)
        preencher(c, recolhida, LINHAS_RECOLHIDO, itens, agora, fundoEscuro = false)
        val outras = itens.size - 1
        if (outras > 0) {
            recolhida.setTextViewText(R.id.mais, "+$outras")
            recolhida.setViewVisibility(R.id.mais, View.VISIBLE)
        }
        val expandida = RemoteViews(c.packageName, R.layout.notif_resumo_expandida)
        preencher(c, expandida, LINHAS_EXPANDIDO, itens, agora, fundoEscuro = false)

        return Notification.Builder(c, CANAL_RESUMO)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(COR_APP)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(recolhida)
            .setCustomBigContentView(expandida)
            .setContentIntent(abrirApp(c, novaTarefa = false))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
    }

    private fun atualizarResumo(c: Context, itens: List<Item>, @Suppress("UNUSED_PARAMETER") agora: Long) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val mostrar = itens.isNotEmpty() && (c.tarefas.config().resumoNaNotificacao || BolhaService.rodando)
        if (mostrar) nm.notify(ID_RESUMO, construirResumo(c, itens))
        else if (!BolhaService.rodando) nm.cancel(ID_RESUMO)
    }
}
