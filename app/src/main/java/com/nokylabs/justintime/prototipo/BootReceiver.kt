package com.nokylabs.justintime.prototipo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarmes do AlarmManager somem quando o celular reinicia: aqui eles são refeitos. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val motivo = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "celular ligou"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "app atualizado"
            Intent.ACTION_TIME_CHANGED -> "hora alterada"
            Intent.ACTION_TIMEZONE_CHANGED -> "fuso alterado"
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> "permissão de alarme mudou"
            else -> return
        }
        Notificador.criarCanais(c)
        Agendador.reagendarTudo(c, motivo)
    }
}
