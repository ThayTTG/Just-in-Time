package com.nokylabs.justintime.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.nokylabs.justintime.dominio.Aviso
import com.nokylabs.justintime.dominio.Item
import com.nokylabs.justintime.dominio.Toque
import com.nokylabs.justintime.dominio.formatado
import com.nokylabs.justintime.dominio.minutosPorExtenso

/** Canais e notificações do app. */
object Notificacoes {
    const val CANAL_ALARME = "alarmes"
    const val CANAL_SILENCIOSO = "silencioso"
    /**
     * "Faltam 30 min". Precisa de importância ALTA para aparecer flutuando (pop-up);
     * a importância de um canal não muda depois de criado, por isso é um canal novo (0.5.0).
     */
    const val CANAL_AVISOS = "avisos_antes"
    /** Resumo do dia e pendências: o antigo canal "avisos", que continua discreto. */
    const val CANAL_RESUMOS = "avisos"
    const val CANAL_ERROS = "erros"
    private const val ID_RESUMO_DO_DIA = 1_000_001
    private const val ID_PENDENTES = 1_000_002
    private val COR_APP = 0xFFFFD60A.toInt()

    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)

    fun criarCanais(c: Context) {
        val som = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        nm(c).createNotificationChannels(listOf(
            NotificationChannel(CANAL_ALARME, "Alarmes de prazo", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Toca quando o prazo de uma tarefa chega"
                setSound(som, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 400, 600)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CANAL_AVISOS, "Avisos antes do prazo", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "\"Faltam 30 min\": aparece por cima da tela, com som"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CANAL_RESUMOS, "Resumo do dia e pendências", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tarefas de dia inteiro que faltam e as que aguardam resposta"
            },
            NotificationChannel(CANAL_SILENCIOSO, "Sem som (horário silencioso)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Prazos e avisos que chegam no horário silencioso"
                setSound(null, null); enableVibration(false)
            },
            NotificationChannel(CANAL_ERROS, "Avisos de erro", NotificationManager.IMPORTANCE_DEFAULT),
        ))
    }

    private fun idDe(ocorrenciaId: String) = ocorrenciaId.hashCode()

    private fun acao(c: Context, acao: String, ocorrenciaId: String, n: Int): PendingIntent = PendingIntent.getBroadcast(
        c, ocorrenciaId.hashCode() * 10 + n,
        Intent(c, AlarmeReceiver::class.java).setAction(acao).putExtra(AlarmeReceiver.ID, ocorrenciaId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun abrirApp(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 1, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE
    )

    fun podeTelaCheia(c: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 34 || nm(c).canUseFullScreenIntent()

    /**
     * O prazo chegou. ALTO: som de alarme insistente + tela de alarme (por cima do bloqueio).
     * SILENCIOSO: a mesma notificação, sem som (horário silencioso, dia inteiro, ou depois de 60 s).
     */
    fun mostrarPrazo(c: Context, item: Item, toque: Toque) {
        val id = item.ocorrencia.id
        val alto = toque == Toque.ALTO
        val icone = Icon.createWithResource(c, R.drawable.ic_notificacao)
        val telaDeAlarme = PendingIntent.getActivity(
            c, id.hashCode(), AlarmeActivity.intent(c, id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = Notification.Builder(c, if (alto) CANAL_ALARME else CANAL_SILENCIOSO)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(if (item.tarefa.cor != 0) item.tarefa.cor else COR_APP)
            .setContentTitle(item.rotulo)
            .setContentText(
                if (item.tarefa.tipoPrazo.name == "DIA_INTEIRO") "O dia acabou. Você fez?"
                else "O prazo chegou (${item.prazo.time.formatado()}). Você fez?"
            )
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(telaDeAlarme)
            .setOngoing(true)
            .setShowWhen(true)
            .addAction(Notification.Action.Builder(icone, "Feito", acao(c, AlarmeReceiver.FEITO, id, 1)).build())
            .addAction(Notification.Action.Builder(icone, "Não feito", acao(c, AlarmeReceiver.NAO_FEITO, id, 2)).build())
            .addAction(Notification.Action.Builder(icone, "Adiar 15 min", acao(c, AlarmeReceiver.ADIAR, id, 3)).build())
        if (alto) b.setFullScreenIntent(telaDeAlarme, true)
        val n = b.build()
        if (alto) n.flags = n.flags or Notification.FLAG_INSISTENT // repete o som até alguém interagir
        else nm(c).cancel(idDe(id)) // troca de canal: tira a versão barulhenta antes
        nm(c).notify(idDe(id), n)
    }

    /** Avisos (faltam X), resumo do dia e pendências de quando o silêncio acaba. */
    fun mostrarAviso(c: Context, aviso: Aviso) {
        val canal = when {
            aviso.silencioso -> CANAL_SILENCIOSO
            aviso is Aviso.FaltaPouco -> CANAL_AVISOS
            else -> CANAL_RESUMOS
        }
        val b = Notification.Builder(c, canal)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(COR_APP)
            .setContentIntent(abrirApp(c))
            .setAutoCancel(true)
        val id = when (aviso) {
            is Aviso.FaltaPouco -> {
                b.setContentTitle(aviso.item.rotulo)
                    .setContentText("Faltam ${minutosPorExtenso(aviso.minutosAntes)} · prazo às ${aviso.item.prazo.time.formatado()}")
                    .setColor(if (aviso.item.tarefa.cor != 0) aviso.item.tarefa.cor else COR_APP)
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                idDe(aviso.item.ocorrencia.id) + 7
            }
            is Aviso.ResumoDoDia -> {
                b.setContentTitle("Ainda dá tempo hoje: ${aviso.itens.size} tarefa${if (aviso.itens.size > 1) "s" else ""}")
                    .setContentText(aviso.itens.joinToString(" · ") { it.rotulo })
                    .setStyle(Notification.BigTextStyle().bigText(aviso.itens.joinToString("\n") { "• ${it.rotulo}" }))
                ID_RESUMO_DO_DIA
            }
            is Aviso.Pendentes -> {
                b.setContentTitle("Bom dia! ${aviso.itens.size} tarefa${if (aviso.itens.size > 1) "s" else ""} aguardando resposta")
                    .setContentText(aviso.itens.joinToString(" · ") { it.rotulo })
                    .setStyle(Notification.BigTextStyle().bigText(aviso.itens.joinToString("\n") { "• ${it.rotulo}" }))
                ID_PENDENTES
            }
        }
        nm(c).notify(id, b.build())
    }

    fun cancelar(c: Context, ocorrenciaId: String) {
        nm(c).cancel(idDe(ocorrenciaId))
    }
}
