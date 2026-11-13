package com.nokylabs.justintime.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nokylabs.justintime.dominio.Agendador
import com.nokylabs.justintime.dominio.Item
import com.nokylabs.justintime.dominio.TipoPrazo
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime

/**
 * Agendador do Android.
 * - Prazo com horário exato → setAlarmClock(): exato, atravessa o modo soneca e mostra o despertador
 *   na barra (o método que deu 0 a 1,5 s de atraso no protótipo).
 * - Prazo de dia inteiro, silenciar depois de 60 s e o próximo lembrete (aviso, resumo do dia,
 *   fim do silêncio) → setExactAndAllowWhileIdle(), sem ícone de despertador.
 * Lembrete: só UM fica agendado por vez; quando ele toca, o seguinte é calculado.
 */
class AgendadorAndroid(private val context: Context) : Agendador {

    private val am get() = context.getSystemService(AlarmManager::class.java)

    fun podeAgendarExato(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    override fun agendar(item: Item, quando: Instant) {
        val id = item.ocorrencia.id
        Notificacoes.cancelar(context, id) // reagendada (ex.: adiada): a notificação antiga sai
        cancelarSilenciar(id)
        val pi = intent(AlarmeReceiver.PRAZO, id, id.hashCode())
        val ms = quando.toEpochMilliseconds()
        when {
            !podeAgendarExato() -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            item.tarefa.tipoPrazo == TipoPrazo.DIA_INTEIRO -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            else -> {
                val abrirApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(ms, abrirApp), pi)
            }
        }
    }

    override fun cancelar(ocorrenciaId: String) {
        am.cancel(intent(AlarmeReceiver.PRAZO, ocorrenciaId, ocorrenciaId.hashCode()))
        cancelarSilenciar(ocorrenciaId)
        Notificacoes.cancelar(context, ocorrenciaId)
    }

    /** Depois de qualquer mudança: recalcula o próximo lembrete. */
    override fun vitrineMudou() {
        Erros.proteger(context, "Agendar lembrete") { agendarProximoLembrete() }
        Vitrine.atualizar(context) // widget, resumo e bolha
    }

    fun agendarProximoLembrete() {
        val tarefas = context.tarefas
        val ultimo = tarefas.preferencia(ULTIMO_LEMBRETE)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        val agora = tarefas.agoraLocal()
        val depoisDe = if (ultimo != null && ultimo > agora) ultimo else agora
        val proximo = context.lembretes.proximo(depoisDe)
        val pi = PendingIntent.getBroadcast(
            context, LEMBRETE_CODIGO,
            Intent(context, AlarmeReceiver::class.java).setAction(AlarmeReceiver.LEMBRETE)
                .putExtra(AlarmeReceiver.CHAVE, proximo?.chave),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (proximo == null) { am.cancel(pi); return }
        exato(tarefas.instante(proximo.quando).toEpochMilliseconds(), pi)
    }

    /** O alarme alto para de tocar depois de 60 s (a notificação continua, sem som). */
    fun agendarSilenciar(ocorrenciaId: String) =
        exato(System.currentTimeMillis() + 60_000, intent(AlarmeReceiver.SILENCIAR, ocorrenciaId, ocorrenciaId.hashCode() + 1))

    private fun cancelarSilenciar(ocorrenciaId: String) =
        am.cancel(intent(AlarmeReceiver.SILENCIAR, ocorrenciaId, ocorrenciaId.hashCode() + 1))

    private fun exato(ms: Long, pi: PendingIntent) {
        if (podeAgendarExato()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
    }

    private fun intent(acao: String, ocorrenciaId: String, codigo: Int): PendingIntent = PendingIntent.getBroadcast(
        context, codigo,
        Intent(context, AlarmeReceiver::class.java).setAction(acao).putExtra(AlarmeReceiver.ID, ocorrenciaId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ULTIMO_LEMBRETE = "android.lembrete.ultimo"
        private const val LEMBRETE_CODIGO = 424_242
    }
}
