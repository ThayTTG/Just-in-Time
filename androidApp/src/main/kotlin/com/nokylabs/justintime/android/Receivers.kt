package com.nokylabs.justintime.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.nokylabs.justintime.dominio.Lembrete
import com.nokylabs.justintime.dominio.Status
import com.nokylabs.justintime.dominio.Toque

/** Recebe os alarmes (prazo, silenciar, lembrete) e as respostas da notificação. */
class AlarmeReceiver : BroadcastReceiver() {
    companion object {
        const val PRAZO = "jit.PRAZO"
        const val SILENCIAR = "jit.SILENCIAR"
        const val LEMBRETE = "jit.LEMBRETE"
        const val FEITO = "jit.FEITO"
        const val NAO_FEITO = "jit.NAO_FEITO"
        const val ADIAR = "jit.ADIAR"
        const val SIMULAR_ERRO = "jit.SIMULAR_ERRO"
        const val VITRINE = "jit.VITRINE"
        const val ID = "ocorrencia"
        const val CHAVE = "chave"
    }

    override fun onReceive(c: Context, intent: Intent) = Erros.proteger(c, "Alarme: ${intent.action}") {
        val id = intent.getStringExtra(ID)
        val tarefas = c.tarefas
        when (intent.action) {
            PRAZO -> id?.let { prazoChegou(c, it) }
            SILENCIAR -> id?.let { ocorrencia ->
                val item = tarefas.item(ocorrencia)
                if (item?.status == Status.AGUARDANDO) Notificacoes.mostrarPrazo(c, item, Toque.SILENCIOSO)
            }
            LEMBRETE -> intent.getStringExtra(CHAVE)?.let { Lembrete.daChave(it) }?.let { l ->
                tarefas.gravarPreferencia(AgendadorAndroid.ULTIMO_LEMBRETE, l.quando.toString())
                c.lembretes.disparar(l)?.let { Notificacoes.mostrarAviso(c, it) }
                AgendadorAndroid(c).agendarProximoLembrete() // e já deixa o seguinte agendado
            }
            FEITO -> id?.let { tarefas.responder(it, feita = true) }
            NAO_FEITO -> id?.let { tarefas.responder(it, feita = false) }
            ADIAR -> id?.let { tarefas.adiarEm(it, 15 * 60) }
            VITRINE -> Vitrine.atualizar(c) // uma tarefa mudou de faixa de cor (24 h / 1 h antes)
            SIMULAR_ERRO -> throw IllegalStateException("Erro simulado em segundo plano (teste do relatório)")
        }
    }

    private fun prazoChegou(c: Context, ocorrenciaId: String) {
        val item = c.tarefas.prazoChegou(ocorrenciaId) ?: return
        val toque = c.lembretes.toque(item)
        Notificacoes.mostrarPrazo(c, item, toque)
        if (toque == Toque.ALTO) {
            AgendadorAndroid(c).agendarSilenciar(ocorrenciaId)
            // Com "aparecer sobre outros apps", a tela de alarme abre também com o celular em uso
            if (Settings.canDrawOverlays(c)) c.startActivity(AlarmeActivity.intent(c, ocorrenciaId))
        }
    }
}

/** Alarmes do sistema somem quando o celular reinicia (ou a hora muda): aqui eles são refeitos. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) = Erros.proteger(c, "Reagendar (${intent.action})") {
        c.tarefas.reconciliar()
    }
}
