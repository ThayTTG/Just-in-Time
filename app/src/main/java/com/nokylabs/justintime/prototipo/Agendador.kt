package com.nokylabs.justintime.prototipo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Agenda os alarmes no sistema.
 *
 * - Prazo da tarefa: setAlarmClock() → exato, atravessa o modo soneca (Doze)
 *   e mostra o ícone de despertador na barra de status.
 * - Eventos secundários (silenciar após 60 s, atualizar cor do widget):
 *   setExactAndAllowWhileIdle().
 *
 * No protótipo cada tarefa tem o seu alarme; no app final ficam só "o próximo prazo"
 * e "o próximo evento secundário", como está no documento de arquitetura.
 */
object Agendador {

    const val ACAO_PRAZO = "jit.PRAZO"
    const val ACAO_SILENCIAR = "jit.SILENCIAR"
    const val ACAO_ATUALIZAR_WIDGET = "jit.ATUALIZAR_WIDGET"
    const val EXTRA_ID = "id"

    const val SILENCIAR_APOS_MS = 60_000L

    private fun am(c: Context) = c.getSystemService(AlarmManager::class.java)

    fun podeAgendarExato(c: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am(c).canScheduleExactAlarms()

    private fun piReceiver(c: Context, acao: String, id: Int, requestCode: Int): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction(acao).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            c, requestCode, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun agendarPrazo(c: Context, t: Tarefa) {
        val pi = piReceiver(c, ACAO_PRAZO, t.id, t.id)
        if (podeAgendarExato(c)) {
            // "showIntent": o que abre quando a pessoa toca no despertador da barra de status
            val abrirApp = PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            am(c).setAlarmClock(AlarmManager.AlarmClockInfo(t.prazo, abrirApp), pi)
            Store.log(c, "Agendado (exato): \"${t.titulo}\" para ${Store.hora(t.prazo)}")
        } else {
            am(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.prazo, pi)
            Store.log(c, "⚠ SEM permissão de alarme exato — \"${t.titulo}\" agendado de forma aproximada")
        }
    }

    fun agendarSilenciar(c: Context, id: Int) {
        val quando = System.currentTimeMillis() + SILENCIAR_APOS_MS
        exatoSecundario(c, quando, piReceiver(c, ACAO_SILENCIAR, id, 100_000 + id))
    }

    /** Acorda o app quando alguma tarefa muda de faixa de cor no widget (24 h / 1 h). */
    fun agendarAtualizacaoWidget(c: Context, quando: Long?) {
        val pi = piReceiver(c, ACAO_ATUALIZAR_WIDGET, 0, 999_999)
        if (quando == null) am(c).cancel(pi) else exatoSecundario(c, quando, pi)
    }

    private fun exatoSecundario(c: Context, quando: Long, pi: PendingIntent) {
        if (podeAgendarExato(c)) am(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
        else am(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi)
    }

    fun cancelar(c: Context, id: Int) {
        am(c).cancel(piReceiver(c, ACAO_PRAZO, id, id))
        am(c).cancel(piReceiver(c, ACAO_SILENCIAR, id, 100_000 + id))
    }

    /**
     * Refaz todos os alarmes. Chamado ao abrir o app, ao ligar o celular,
     * quando a hora/fuso muda e quando o app é atualizado.
     */
    fun reagendarTudo(c: Context, motivo: String) {
        val agora = System.currentTimeMillis()
        var reagendadas = 0
        for (t in Store.tarefas(c)) {
            if (t.status != Status.ATIVA) continue
            if (t.prazo > agora) {
                agendarPrazo(c, t); reagendadas++
            } else {
                // O prazo passou com o celular desligado: fica aguardando resposta
                t.status = Status.AGUARDANDO
                Store.salvar(c, t)
                Store.log(c, "Prazo de \"${t.titulo}\" passou enquanto o alarme não podia tocar → Aguardando resposta")
            }
        }
        Store.log(c, "Reagendamento ($motivo): $reagendadas alarme(s)")
        DeadlineWidget.atualizarTodos(c)
    }
}
