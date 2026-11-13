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
import com.nokylabs.justintime.dominio.Item
import com.nokylabs.justintime.dominio.formatado

/**
 * Notificação do prazo, com Feito / Não feito / Adiar 15 min.
 * (Tela cheia, silenciar depois de 60 s, resumo fixo, bolha e widget chegam na próxima etapa.)
 */
object Notificacoes {
    private const val CANAL_ALARME = "alarmes"
    private val COR_APP = 0xFFFFD60A.toInt()

    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)

    fun criarCanais(c: Context) {
        val som = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        nm(c).createNotificationChannel(
            NotificationChannel(CANAL_ALARME, "Alarmes de prazo", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Toca quando o prazo de uma tarefa chega"
                setSound(som, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 400, 600)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun idNotificacao(ocorrenciaId: String) = ocorrenciaId.hashCode()

    private fun acao(c: Context, acao: String, ocorrenciaId: String, n: Int): PendingIntent = PendingIntent.getBroadcast(
        c, ocorrenciaId.hashCode() * 10 + n,
        Intent(c, AlarmeReceiver::class.java).setAction(acao).putExtra(AlarmeReceiver.ID, ocorrenciaId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun mostrarPrazo(c: Context, item: Item) {
        val id = item.ocorrencia.id
        val icone = Icon.createWithResource(c, R.drawable.ic_notificacao)
        val abrir = PendingIntent.getActivity(
            c, 1, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(c, CANAL_ALARME)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(if (item.tarefa.cor != 0) item.tarefa.cor else COR_APP)
            .setContentTitle(item.rotulo)
            .setContentText("O prazo chegou (${item.prazo.time.formatado()}). Você fez?")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(abrir)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(icone, "Feito", acao(c, AlarmeReceiver.FEITO, id, 1)).build())
            .addAction(Notification.Action.Builder(icone, "Não feito", acao(c, AlarmeReceiver.NAO_FEITO, id, 2)).build())
            .addAction(Notification.Action.Builder(icone, "Adiar 15 min", acao(c, AlarmeReceiver.ADIAR, id, 3)).build())
            .build()
        nm(c).notify(idNotificacao(id), n)
    }

    fun cancelar(c: Context, ocorrenciaId: String) = nm(c).cancel(idNotificacao(ocorrenciaId))
}
