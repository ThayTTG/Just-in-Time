package com.nokylabs.justintime.prototipo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Recebe os alarmes do sistema e as respostas da notificação/tela de alarme. */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACAO_SIMULAR_ERRO = "jit.SIMULAR_ERRO"
    }

    override fun onReceive(c: Context, intent: Intent) {
        val id = intent.getIntExtra(Agendador.EXTRA_ID, -1)
        // Qualquer erro aqui vira um relatório + notificação, em vez de sumir em silêncio
        Relatorio.proteger(c, "AlarmReceiver: ${intent.action}") {
            when (intent.action) {
                Agendador.ACAO_PRAZO -> prazoChegou(c, id)
                Agendador.ACAO_SILENCIAR -> silenciar(c, id)
                Agendador.ACAO_ATUALIZAR_WIDGET -> DeadlineWidget.atualizarTodos(c)
                Notificador.ACAO_FEITO -> responder(c, id, Status.FEITA)
                Notificador.ACAO_NAO_FEITO -> responder(c, id, Status.NAO_FEITA)
                Notificador.ACAO_ADIAR -> adiar(c, id)
                ACAO_SIMULAR_ERRO -> throw IllegalStateException("Erro simulado em segundo plano (teste do relatório)")
            }
        }
    }

    /** Com "Aparecer sobre outros apps", a tela de alarme abre também com o celular em uso. */
    private fun abrirTelaDeAlarme(c: Context, t: Tarefa) {
        if (!Settings.canDrawOverlays(c)) {
            Store.log(c, "Celular em uso: sem \"aparecer sobre outros apps\", fica só a notificação")
            return
        }
        c.startActivity(
            Intent(c, AlarmActivity::class.java)
                .putExtra(Agendador.EXTRA_ID, t.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        )
    }

    private fun prazoChegou(c: Context, id: Int) {
        val t = Store.get(c, id) ?: return
        if (t.status != Status.ATIVA) return
        val agora = System.currentTimeMillis()
        t.disparouEm = agora
        t.status = Status.AGUARDANDO
        Store.salvar(c, t)

        val atrasoS = (agora - t.prazo) / 1000.0
        Store.log(c, "🔔 Disparou \"${t.titulo}\" — atraso de ${"%.1f".format(atrasoS)} s")
        if (!Notificador.podeNotificar(c)) Store.log(c, "⚠ Notificações desativadas: o alarme não aparece")
        if (!Notificador.podeTelaCheia(c)) Store.log(c, "⚠ Sem permissão de tela cheia: aparece só como notificação")

        Notificador.mostrarAlarme(c, t, alto = true)
        Relatorio.proteger(c, "Abrir tela de alarme") { abrirTelaDeAlarme(c, t) }
        Agendador.agendarSilenciar(c, id)
        DeadlineWidget.atualizarTodos(c)
    }

    private fun silenciar(c: Context, id: Int) {
        val t = Store.get(c, id) ?: return
        if (t.status != Status.AGUARDANDO) return
        Notificador.mostrarAlarme(c, t, alto = false)
        Store.log(c, "🔕 Sem resposta em 60 s: \"${t.titulo}\" segue aguardando, agora sem som")
    }

    private fun responder(c: Context, id: Int, novo: String) {
        val t = Store.get(c, id) ?: return
        t.status = novo
        Store.salvar(c, t)
        Agendador.cancelar(c, id)
        Notificador.cancelar(c, id)
        AlarmActivity.fecharSeAberta(c)
        Store.log(c, "Resposta: \"${t.titulo}\" → ${Status.rotulo(novo)}")
        DeadlineWidget.atualizarTodos(c)
    }

    private fun adiar(c: Context, id: Int) {
        val t = Store.get(c, id) ?: return
        t.prazo = System.currentTimeMillis() + Notificador.ADIAR_MS
        t.status = Status.ATIVA
        t.adiamentos++
        Store.salvar(c, t)
        Agendador.cancelar(c, id)
        Notificador.cancelar(c, id)
        AlarmActivity.fecharSeAberta(c)
        Store.log(c, "Adiada: \"${t.titulo}\" (adiamento nº ${t.adiamentos})")
        Agendador.agendarPrazo(c, t)
        DeadlineWidget.atualizarTodos(c)
    }
}
