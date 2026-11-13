package com.nokylabs.justintime.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Recebe o alarme do prazo e as respostas da notificação (Feito / Não feito / Adiar). */
class AlarmeReceiver : BroadcastReceiver() {
    companion object {
        const val PRAZO = "jit.PRAZO"
        const val FEITO = "jit.FEITO"
        const val NAO_FEITO = "jit.NAO_FEITO"
        const val ADIAR = "jit.ADIAR"
        const val ID = "ocorrencia"
    }

    override fun onReceive(c: Context, intent: Intent) {
        val id = intent.getStringExtra(ID) ?: return
        val tarefas = c.tarefas
        when (intent.action) {
            PRAZO -> tarefas.prazoChegou(id)?.let { Notificacoes.mostrarPrazo(c, it) }
            FEITO -> tarefas.responder(id, feita = true)
            NAO_FEITO -> tarefas.responder(id, feita = false)
            ADIAR -> { tarefas.adiarEm(id, 15 * 60); Notificacoes.cancelar(c, id) }
        }
    }
}

/** Alarmes do sistema somem quando o celular reinicia (ou a hora muda): aqui eles são refeitos. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        c.tarefas.reconciliar()
    }
}
