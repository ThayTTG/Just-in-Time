package com.nokylabs.justintime.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nokylabs.justintime.dominio.Agendador
import com.nokylabs.justintime.dominio.Item
import kotlinx.datetime.Instant

/**
 * Agendador do Android (o mesmo método que passou no teste do protótipo: 0 a 1,5 s de atraso).
 * Prazo → setAlarmClock(): exato, atravessa o modo soneca e mostra o despertador na barra.
 */
class AgendadorAndroid(private val context: Context) : Agendador {

    private val am get() = context.getSystemService(AlarmManager::class.java)

    fun podeAgendarExato(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    override fun agendar(item: Item, quando: Instant) {
        val pi = intentDoAlarme(item.ocorrencia.id)
        val ms = quando.toEpochMilliseconds()
        if (podeAgendarExato()) {
            val abrirApp = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(ms, abrirApp), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    override fun cancelar(ocorrenciaId: String) {
        am.cancel(intentDoAlarme(ocorrenciaId))
        Notificacoes.cancelar(context, ocorrenciaId)
    }

    private fun intentDoAlarme(ocorrenciaId: String): PendingIntent = PendingIntent.getBroadcast(
        context, ocorrenciaId.hashCode(),
        Intent(context, AlarmeReceiver::class.java).setAction(AlarmeReceiver.PRAZO).putExtra(AlarmeReceiver.ID, ocorrenciaId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
