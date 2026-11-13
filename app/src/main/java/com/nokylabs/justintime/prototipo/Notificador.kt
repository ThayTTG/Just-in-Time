package com.nokylabs.justintime.prototipo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object Notificador {

    const val CANAL_ALARME = "alarmes"
    const val CANAL_SILENCIOSO = "silencioso"

    const val ACAO_FEITO = "jit.FEITO"
    const val ACAO_NAO_FEITO = "jit.NAO_FEITO"
    const val ACAO_ADIAR = "jit.ADIAR"

    /** Adiamento curto, para facilitar os testes. No app final: +15 min, +1 h, amanhã... */
    const val ADIAR_MS = 5 * 60_000L

    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)

    fun criarCanais(c: Context) {
        val somAlarme = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarme = NotificationChannel(
            CANAL_ALARME, "Alarmes de prazo", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Toca quando o prazo de uma tarefa chega"
            setSound(
                somAlarme,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600)
            setBypassDnd(true) // só vale se a pessoa permitir nas configurações
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val silencioso = NotificationChannel(
            CANAL_SILENCIOSO, "Pendências (sem som)", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Tarefas aguardando resposta, sem tocar"
            setSound(null, null)
            enableVibration(false)
        }
        nm(c).createNotificationChannels(listOf(alarme, silencioso))
    }

    fun podeTelaCheia(c: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || nm(c).canUseFullScreenIntent()

    fun podeNotificar(c: Context): Boolean = nm(c).areNotificationsEnabled()

    private fun piAcao(c: Context, acao: String, id: Int, n: Int): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction(acao).putExtra(Agendador.EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            c, id * 10 + n, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun piTelaAlarme(c: Context, id: Int): PendingIntent {
        val i = Intent(c, AlarmActivity::class.java)
            .putExtra(Agendador.EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        return PendingIntent.getActivity(
            c, id * 10 + 9, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * @param alto true = canal de alarme, som insistente e tela cheia;
     *             false = mesma notificação, mas sem som (depois de 60 s).
     */
    fun mostrarAlarme(c: Context, t: Tarefa, alto: Boolean) {
        val canal = if (alto) CANAL_ALARME else CANAL_SILENCIOSO
        val icone = Icon.createWithResource(c, R.drawable.ic_stat_alarme)
        val b = Notification.Builder(c, canal)
            .setSmallIcon(R.drawable.ic_stat_alarme)
            .setContentTitle(t.titulo)
            .setContentText("O prazo chegou (${Store.hora(t.prazo)}). Você fez?")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setShowWhen(true)
            .setWhen(t.prazo)
            .setContentIntent(piTelaAlarme(c, t.id))
            .addAction(Notification.Action.Builder(icone, "Feito", piAcao(c, ACAO_FEITO, t.id, 1)).build())
            .addAction(Notification.Action.Builder(icone, "Não feito", piAcao(c, ACAO_NAO_FEITO, t.id, 2)).build())
            .addAction(Notification.Action.Builder(icone, "Adiar 5 min", piAcao(c, ACAO_ADIAR, t.id, 3)).build())
        if (alto) b.setFullScreenIntent(piTelaAlarme(c, t.id), true)
        val n = b.build()
        if (alto) n.flags = n.flags or Notification.FLAG_INSISTENT // repete o som até interagir
        if (!alto) nm(c).cancel(t.id) // troca de canal: remove a versão barulhenta antes
        nm(c).notify(t.id, n)
    }

    fun cancelar(c: Context, id: Int) = nm(c).cancel(id)
}
